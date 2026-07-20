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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.search.Preconditions;
import org.opengroup.osdu.indexer.cache.partitionsafe.VirtualPropertiesSchemaCache;
import org.opengroup.osdu.indexer.schema.converter.config.SchemaConverterConfig;
import org.opengroup.osdu.indexer.schema.converter.exeption.SchemaProcessingException;
import org.opengroup.osdu.indexer.schema.converter.interfaces.SchemaToStorageFormat;
import org.opengroup.osdu.indexer.schema.converter.tags.Priority;
import org.opengroup.osdu.indexer.schema.converter.tags.PropertiesData;
import org.opengroup.osdu.indexer.schema.converter.tags.SchemaRoot;
import org.opengroup.osdu.indexer.schema.converter.tags.VirtualProperty;
import org.opengroup.osdu.indexer.util.PropertyUtil;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts schema from Schema Service format to Storage Service format
 */
@Component
public class SchemaToStorageFormatImpl implements SchemaToStorageFormat {
    private final Gson gson = new Gson();

    private ObjectMapper objectMapper;
    private SchemaConverterConfig schemaConverterConfig;

    @Inject
    private VirtualPropertiesSchemaCache virtualPropertiesSchemaCache;

    @Inject
    public SchemaToStorageFormatImpl(ObjectMapper objectMapper, JaxRsDpsLog log, SchemaConverterConfig schemaConverterConfig) {
        Preconditions.checkNotNull(objectMapper, "objectMapper cannot be null");

        this.objectMapper = objectMapper;
        this.schemaConverterConfig = schemaConverterConfig;
    }

    @Override
    public String convertToString(final String schemaServiceFormat, String kind) {
        Preconditions.checkNotNullOrEmpty(schemaServiceFormat, "schemaServiceFormat cannot be null or empty");
        Preconditions.checkNotNullOrEmpty(kind, "kind cannot be null or empty");

        return saveJsonToString(convert(parserJsonString(schemaServiceFormat), kind));
    }

    public Map<String, Object> convertToMap(final String schemaServiceFormat, String kind) {
        Preconditions.checkNotNullOrEmpty(schemaServiceFormat, "schemaServiceFormat cannot be null or empty");
        Preconditions.checkNotNullOrEmpty(kind, "kind cannot be null or empty");

        return convert(parserJsonString(schemaServiceFormat), kind);
    }

    private SchemaRoot parserJsonString(final String schemaServiceFormat) {
        try {
            return objectMapper.readValue(schemaServiceFormat, SchemaRoot.class);
        } catch (JsonProcessingException e) {
            throw new SchemaProcessingException("Failed to parse the schema");
        }
    }

    private String saveJsonToString(final Map<String, Object> schemaServiceFormat) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schemaServiceFormat);
        } catch (JsonProcessingException e) {
            throw new SchemaProcessingException("Failed to save the JSON file");
        }
    }

    private Map<String, Object> convert(SchemaRoot schemaServiceSchema, String kind) {
        Preconditions.checkNotNull(objectMapper, "schemaServiceSchema cannot be null");
        Preconditions.checkNotNullOrEmpty(kind, "kind cannot be null or empty");

        PropertiesProcessor propertiesProcessor = new PropertiesProcessor(schemaServiceSchema.getDefinitions(), schemaConverterConfig);

        final List<Map<String, Object>> storageSchemaItems = new ArrayList<>();
        if (schemaServiceSchema.getProperties() == null) {
            throw new SchemaProcessingException(String.format("Schema doesn't have data section, kind: %s", kind));
        }

        PropertiesData schemaData = schemaServiceSchema.getProperties().getData();
        if (Objects.isNull(schemaData)) {
            throw new SchemaProcessingException(String.format("Schema doesn't have properties section, kind: %s", kind));
        }

        if (schemaData.getAllOf() != null) {
            storageSchemaItems.addAll(schemaServiceSchema.getProperties().getData().getAllOf().stream()
                    .flatMap(propertiesProcessor::processItem)
                    .collect(Collectors.toList()));
        }

        if (schemaData.getAnyOf() != null) {
            storageSchemaItems.addAll(schemaServiceSchema.getProperties().getData().getAnyOf().stream()
                    .flatMap(propertiesProcessor::processItem)
                    .collect(Collectors.toList()));
        }

        if (schemaData.getOneOf() != null) {
            storageSchemaItems.addAll(schemaServiceSchema.getProperties().getData().getOneOf().stream()
                    .flatMap(propertiesProcessor::processItem)
                    .collect(Collectors.toList()));
        }

        if (schemaData.getRef() != null) {
            storageSchemaItems.addAll(propertiesProcessor.processRef(schemaData.getRef())
                    .collect(Collectors.toList()));
        }

        if (schemaData.getProperties() != null) {
            storageSchemaItems.addAll(propertiesProcessor.processProperties(schemaData.getProperties())
                    .collect(Collectors.toList()));
        }

        if (schemaServiceSchema.getVirtualProperties() != null) {
            this.virtualPropertiesSchemaCache.put(kind, schemaServiceSchema.getVirtualProperties());
            populateVirtualPropertiesSchema(propertiesProcessor, storageSchemaItems, schemaServiceSchema.getVirtualProperties().getProperties());
        }

        if (!propertiesProcessor.getErrors().isEmpty()) {
            throw new SchemaProcessingException(String.format("Errors occurred during parsing the schema, kind: %s | errors: %s",
                    kind, String.join(",", propertiesProcessor.getErrors())));
        }

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", kind);
        result.put("schema", storageSchemaItems);

        return result;
    }

    private void populateVirtualPropertiesSchema(PropertiesProcessor propertiesProcessor, List<Map<String, Object>> storageSchemaItems, Map<String, VirtualProperty> virtualProperties) {
        if (virtualProperties == null || virtualProperties.isEmpty())
            return;

        boolean hasVirtualDefaultLocation = false;
        for (Map.Entry<String, VirtualProperty> entry : virtualProperties.entrySet()) {
            if (entry.getValue().getPriorities() == null ||
                    entry.getValue().getPriorities().size() == 0) {
                propertiesProcessor.getErrors().add(String.format("Invalid virtual properties attribute '%s': priority missing.", entry.getKey()));
                continue;
            }

            // The schema for different properties in the list of Priority should be the same
            Priority priority = entry.getValue().getPriorities().get(0);
            String virtualPropertyPath = PropertyUtil.removeDataPrefix(entry.getKey());
            hasVirtualDefaultLocation |= PropertyUtil.isPropertyPathMatched(virtualPropertyPath, PropertyUtil.VIRTUAL_DEFAULT_LOCATION);

            String originalPropertyPath = PropertyUtil.removeDataPrefix(priority.getPath());
            List<Map<String, Object>> matchedItems = storageSchemaItems.stream().filter(item ->
                            PropertyUtil.isPropertyPathMatched((String) item.get("path"), originalPropertyPath))
                    .collect(Collectors.toList());
            storageSchemaItems.addAll(matchedItems.stream().map(item ->
                            cloneVirtualProperty(item, virtualPropertyPath, originalPropertyPath))
                    .collect(Collectors.toList()));
        }

        if(hasVirtualDefaultLocation) {
            Map<String, Object> isDecimatedProperty = new HashMap<>();
            isDecimatedProperty.put("path", PropertyUtil.VIRTUAL_DEFAULT_LOCATION_IS_DECIMATED_PATH);
            isDecimatedProperty.put("kind", "boolean");
            storageSchemaItems.add(isDecimatedProperty);
        }
    }

    private Map<String, Object> cloneVirtualProperty(Map<String, Object> originalProperty, String virtualPropertyPath, String originalPropertyPath) {
        String jsonString = gson.toJson(originalProperty);
        Map<String, Object> clonedItem = gson.fromJson(jsonString, HashMap.class); // Clean clone
        String virtualPropertyFullPath = virtualPropertyPath + clonedItem.get("path").toString().substring(originalPropertyPath.length());
        clonedItem.put("path", virtualPropertyFullPath);
        return clonedItem;
    }
}
