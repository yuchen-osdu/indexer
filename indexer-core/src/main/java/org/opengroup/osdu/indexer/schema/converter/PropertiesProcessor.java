// Copyright 2017-2020, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.indexer.schema.converter;

import lombok.Getter;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.search.Preconditions;
import org.opengroup.osdu.indexer.schema.converter.config.SchemaConverterConfig;
import org.opengroup.osdu.indexer.schema.converter.config.SchemaConverterPropertiesConfig;
import org.opengroup.osdu.indexer.schema.converter.exeption.SchemaProcessingException;
import org.opengroup.osdu.indexer.schema.converter.tags.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PropertiesProcessor {

    private final SchemaConverterConfig schemaConverterConfig;

    private static final String TYPE_KEY = "type";
    private static final String DEF_PREFIX = "#/definitions/";
    private static final String LINK_PREFIX = "^srn";
    private static final String LINK_TYPE = "link";

    private final Definitions definitions;
    private final String pathPrefix;
    private final String pathPrefixWithDot;

    @Getter
    private final List<String> errors = new LinkedList<>();

    public PropertiesProcessor(Definitions definitions, SchemaConverterConfig schemaConverterConfig) {
        this(definitions, null, schemaConverterConfig);
    }

    public PropertiesProcessor(Definitions definitions, String pathPrefix, SchemaConverterConfig schemaConverterConfig) {
        this.definitions = definitions;
        this.pathPrefix = pathPrefix;
        this.pathPrefixWithDot = Objects.isNull(pathPrefix) || pathPrefix.isEmpty() ? "" : pathPrefix + ".";
        this.schemaConverterConfig = schemaConverterConfig;
    }

    public Stream<Map<String, Object>> processItem(AllOfItem allOfItem) {
        try {
            Preconditions.checkNotNull(allOfItem, "allOfItem cannot be null");

            Stream<Map<String, Object>> ofItems = processOfItems(allOfItem.getAllOf(), allOfItem.getAnyOf(), allOfItem.getOneOf());

            if (Objects.nonNull(ofItems)) {
                return ofItems;
            }

            String ref = allOfItem.getRef();

            return Objects.isNull(ref) ?
                    allOfItem.getProperties().entrySet().stream().flatMap(this::processPropertyEntry) : processRef(ref);
        } catch (RuntimeException exception) {
            errors.add(exception.getMessage());
            return Stream.empty();
        }
    }

    public Stream<Map<String, Object>> processRef(String ref) {
        Preconditions.checkNotNull(ref, "reference cannot be null");

        try {
            if (!ref.contains(DEF_PREFIX)) {
                throw new SchemaProcessingException("Unknown definition format, no prefix:" + ref);
            }

            String definitionSubRef = ref.substring(DEF_PREFIX.length());

            String definitionIdentity = getDefinitionIdentity(definitionSubRef);

            if (schemaConverterConfig.getSkippedDefinitions().contains(definitionIdentity)) {
                return Stream.empty();
            }

            if (Objects.nonNull(schemaConverterConfig.getSpecialDefinitionsMap().get(definitionIdentity))) {
                return storageSchemaEntry(
                        schemaConverterConfig.getSpecialDefinitionsMap().get(definitionIdentity) + getDefinitionColonVersion(definitionSubRef),
                        pathPrefix);
            }

            Definition definition = definitions.getDefinition(definitionSubRef);
            Optional.ofNullable(definition).orElseThrow(() ->
                    new SchemaProcessingException("Failed to find definition:" + definitionSubRef));

            Stream<Map<String, Object>> ofItems =
                    processOfItems(definition.getAllOf(), definition.getAnyOf(), definition.getOneOf());

            if (Objects.nonNull(ofItems)) {
                return ofItems;
            }

            return processProperties(definition.getProperties());
        } catch (RuntimeException exception) {
            errors.add(exception.getMessage());
            return Stream.empty();
        }
    }

    public Stream<Map<String, Object>> processProperties(Map<String, TypeProperty> properties) {
        return properties.entrySet().stream().flatMap(this::processPropertyEntry);
    }

    private String getDefinitionIdentity(String definitionSubRef) {
        String[] components = definitionSubRef.split(":");
        switch (components.length) {
            case 1:
                return components[0];
            case 4:
                return components[2];
        }
        throw new AppException(HttpStatus.SC_CONFLICT, "Wrong definition format:" + definitionSubRef,
                "Wrong definition format:" + definitionSubRef);
    }

    private String getDefinitionColonVersion(String definitionSubRef) {
        String[] components = definitionSubRef.split(":");
        switch (components.length) {
            case 1:
                return ":1.0.0";
            case 4:
                return ":" + components[3];
        }
        throw new AppException(HttpStatus.SC_CONFLICT, "Wrong definition format:" + definitionSubRef,
                "Wrong definition format:" + definitionSubRef);
    }

    private Stream<Map<String, Object>> processOfItems(List<AllOfItem> allOf, List<AllOfItem> anyOf, List<AllOfItem> oneOf) {
        Stream<Map<String, Object>> ofItems = null;

        if (Objects.nonNull(allOf)) {
            ofItems = allOf.stream().flatMap(this::processItem);
        }

        if (Objects.nonNull(anyOf)) {
            ofItems = Stream.concat(Optional.ofNullable(ofItems).orElseGet(Stream::empty), anyOf.stream().flatMap(this::processItem));
        }

        if (Objects.nonNull(oneOf)) {
            ofItems = Stream.concat(Optional.ofNullable(ofItems).orElseGet(Stream::empty), oneOf.stream().flatMap(this::processItem));
        }

        return ofItems;
    }

    private Stream<Map<String, Object>> processPropertyEntry(Map.Entry<String, TypeProperty> entry) {
        Preconditions.checkNotNull(entry, "entry cannot be null");

        try {
            if ("object".equals(entry.getValue().getType())
                    && Objects.isNull(entry.getValue().getItems())
                    && Objects.isNull(entry.getValue().getRef())
                    && Objects.isNull(entry.getValue().getProperties())) {
                return Stream.empty();
            }

            if ("array".equals(entry.getValue().getType())) {
                Items items = entry.getValue().getItems();

                if (items == null) {
                    errors.add(String.format("Invalid array attribute: '%s', missing or null 'items'", entry.getKey()));
                    return Stream.empty();
                }

                if (Objects.nonNull(items) && items.isComplexTypeItems()) {
                    return processComplexTypeItems(entry, items);
                }

                if (schemaConverterConfig.getSupportedArrayTypes().contains(entry.getValue().getItems().getType()) && !items.isComplexTypeItems()) {
                    return storageSchemaEntry("[]" + getTypeByDefinitionProperty(entry.getValue()), pathPrefixWithDot + entry.getKey());
                }

                return Stream.empty();
            }

            Stream<Map<String, Object>> ofItems = processOfItems(entry);

            if (Objects.nonNull(ofItems)) {
                return ofItems;
            }

            if (Objects.nonNull(entry.getValue().getProperties())) {
                PropertiesProcessor propertiesProcessor = new PropertiesProcessor(definitions, pathPrefixWithDot + entry.getKey()
                        , schemaConverterConfig);
                Stream<Map<String, Object>> result = entry.getValue().getProperties().entrySet().stream().flatMap(propertiesProcessor::processPropertyEntry);
                errors.addAll(propertiesProcessor.getErrors());
                return result;
            }

            if (Objects.nonNull(entry.getValue().getRef())) {
                PropertiesProcessor propertiesProcessor = new PropertiesProcessor(definitions
                        , pathPrefixWithDot + entry.getKey(), schemaConverterConfig);
                Stream<Map<String, Object>> refResult = propertiesProcessor.processRef(entry.getValue().getRef());
                errors.addAll(propertiesProcessor.getErrors());
                return refResult;
            }

            return storageSchemaEntry(getTypeByDefinitionProperty(entry.getValue()), pathPrefixWithDot + entry.getKey());
        } catch (RuntimeException ex) {
            errors.add(ex.getMessage());
            return Stream.empty();
        }

    }

    private Stream<Map<String, Object>> processComplexTypeItems(Entry<String, TypeProperty> entry, Items items) {
        Map<String, String> indexHint = entry.getValue().getIndexHint();

        String indexingType = Objects.isNull(indexHint) ?
                schemaConverterConfig.getDefaultObjectArraysType() :
                indexHint.getOrDefault(TYPE_KEY, schemaConverterConfig.getDefaultObjectArraysType());

        if (schemaConverterConfig.getProcessedArraysTypes().contains(indexingType)) {
            PropertiesProcessor propertiesProcessor = new PropertiesProcessor(definitions, schemaConverterConfig);

            Stream<Map<String, Object>> propertiesStream = Stream.empty();

            if (Objects.nonNull(items.getProperties())) {
                propertiesStream = items.getProperties().entrySet().stream().flatMap(propertiesProcessor::processPropertyEntry);
            }
            if (Objects.nonNull(items.getRef())) {
                propertiesStream = Stream.concat(propertiesStream, propertiesProcessor.processRef(items.getRef()));
            }
            if (Objects.nonNull(items.getAllOf())) {
                propertiesStream = Stream.concat(propertiesStream, items.getAllOf().stream().flatMap(propertiesProcessor::processItem));
            }
            return storageSchemaObjectArrayEntry(
                    indexingType,
                    pathPrefixWithDot + entry.getKey(),
                    propertiesStream);
        } else {
            return storageSchemaEntry(indexingType, pathPrefixWithDot + entry.getKey());
        }
    }

    private Stream<Map<String, Object>> processOfItems(Map.Entry<String, TypeProperty> entry) {
        Stream<Map<String, Object>> ofItems = null;

        if (Objects.nonNull(entry.getValue().getAllOf())) {
            PropertiesProcessor propertiesProcessor = new PropertiesProcessor(definitions, pathPrefixWithDot + entry.getKey()
                    , schemaConverterConfig);

            ofItems = entry.getValue().getAllOf().stream().flatMap(propertiesProcessor::processItem);
            errors.addAll(propertiesProcessor.getErrors());
        }

        if (Objects.nonNull(entry.getValue().getAnyOf())) {
            PropertiesProcessor propertiesProcessor = new PropertiesProcessor(definitions, pathPrefixWithDot + entry.getKey()
                    , schemaConverterConfig);

            ofItems = Stream.concat(Optional.ofNullable(ofItems).orElseGet(Stream::empty),
                    entry.getValue().getAnyOf().stream().flatMap(propertiesProcessor::processItem));
            errors.addAll(propertiesProcessor.getErrors());
        }

        if (Objects.nonNull(entry.getValue().getOneOf())) {
            PropertiesProcessor propertiesProcessor = new PropertiesProcessor(definitions, pathPrefixWithDot + entry.getKey()
                    , schemaConverterConfig);

            ofItems = Stream.concat(Optional.ofNullable(ofItems).orElseGet(Stream::empty),
                    entry.getValue().getOneOf().stream().flatMap(propertiesProcessor::processItem));
            errors.addAll(propertiesProcessor.getErrors());
        }

        return ofItems;
    }

    private Stream<Map<String, Object>> storageSchemaEntry(String kind, String path) {
        Preconditions.checkNotNullOrEmpty(path, "path cannot be null or empty");
        Preconditions.checkNotNullOrEmpty(kind, String.format("kind cannot be null or empty for path '%s'", path));

        Map<String, Object> map = new HashMap<>();
        map.put("kind", kind);
        map.put("path", path);
        return Stream.of(map);
    }

    private Stream<Map<String, Object>> storageSchemaObjectArrayEntry(String kind, String path, Stream<Map<String, Object>> mapStream) {
        Preconditions.checkNotNullOrEmpty(kind, "kind cannot be null or empty");
        Preconditions.checkNotNullOrEmpty(path, "path cannot be null or empty");

        Map<String, Object> map = new HashMap<>();
        map.put("kind", kind);
        map.put("path", path);
        map.put(Constants.PROPERTIES, mapStream.collect(Collectors.toList()));
        return Stream.of(map);
    }

    private String getTypeByDefinitionProperty(TypeProperty definitionProperty) {
        Preconditions.checkNotNull(definitionProperty, "definitionProperty cannot be null");

        return Stream.of(
                getFromPattern(definitionProperty.getPattern()),
                getFromItemsPattern(() -> definitionProperty.getItems() != null ? definitionProperty.getItems().getPattern() : null),
                getFromFormat(definitionProperty::getFormat),
                getFromItemsType(() -> definitionProperty.getItems() != null ? definitionProperty.getItems().getType() : null))
                .filter(x -> x.get() != null)
                .findFirst()
                .orElse(getFromType(definitionProperty::getType)).get();
    }

    private Supplier<String> getFromPattern(String pattern) {
        return () -> Objects.nonNull(pattern) && pattern.startsWith(LINK_PREFIX) ? LINK_TYPE : null;
    }

    private Supplier<String> getFromItemsPattern(Supplier<String> itemsPatternSupplier) {
        return () -> {
            String itemsPattern = itemsPatternSupplier.get();
            return Objects.nonNull(itemsPattern) && itemsPattern.startsWith(LINK_PREFIX) ? LINK_TYPE : null;
        };
    }

    private Supplier<String> getFromType(Supplier<String> typeSupplier) {
        return () -> {
            String type = typeSupplier.get();
            return schemaConverterConfig.getPrimitiveTypesMap().getOrDefault(type, type);
        };
    }

    private Supplier<String> getFromFormat(Supplier<String> formatSupplier) {
        return () -> {
            String format = formatSupplier.get();
            return Objects.nonNull(format) ? schemaConverterConfig.getPrimitiveTypesMap().getOrDefault(format, format) : null;
        };
    }

    private Supplier<String> getFromItemsType(Supplier<String> itemsTypeSupplier) {
        return () -> {
            String itemsType = itemsTypeSupplier.get();
            return Objects.nonNull(itemsType) ? schemaConverterConfig.getPrimitiveTypesMap().getOrDefault(itemsType, itemsType) : null;
        };
    }

}
