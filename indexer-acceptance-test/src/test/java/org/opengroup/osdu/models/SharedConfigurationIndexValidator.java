/*
 * Copyright 2017-2026, The Open Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.models;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.test.client.ElasticClient;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.IndexerClient;
import org.opengroup.osdu.core.test.client.SchemaClient;

/**
 * The indexer finds augmenter configurations with nested queries on data.Configurations and
 * data.Configurations.Paths. If the shared configuration index lacks those nested mappings, every
 * lookup fails inside the indexer and no record gets augmented, so the scenarios would only time out.
 */
@Slf4j
public class SharedConfigurationIndexValidator {

    private static boolean validated = false;

    private SharedConfigurationIndexValidator() {
    }

    public static synchronized void ensureValid(String kind, String index, SchemaClient schemaClient,
                                                ElasticClient elasticClient, IndexerClient indexerClient) {
        if (validated) {
            return;
        }

        HttpResponse<String> schemaResponse = schemaClient.getSchemaBody(kind);
        if (schemaResponse.statusCode() != 200) {
            throw new AssertionError(String.format("Schema of '%s' is not available: %d %s",
                kind, schemaResponse.statusCode(), schemaResponse.body()));
        }
        List<String> schemaProblems = schemaProblems(schemaResponse.body());
        if (!schemaProblems.isEmpty()) {
            // createIfNotExist keeps an existing schema, so the test schema file was not applied
            throw new AssertionError(String.format(
                "Schema of '%s' in the environment differs from the test schema: %s", kind, schemaProblems));
        }

        if (elasticClient.isIndexExist(index)) {
            Map<String, IndexMappingRecord> mappings = elasticClient.getMapping(index);
            List<String> mappingProblems = new ArrayList<>();
            mappings.forEach((physicalIndex, mapping) ->
                mappingProblems(mapping.mappings()).forEach(problem -> mappingProblems.add(physicalIndex + ": " + problem)));
            if (!mappingProblems.isEmpty()) {
                // The indexer recreates the index from the (valid) schema on the next ingest
                log.warn("Deleting configuration index '{}' with invalid mapping: {}", index, mappingProblems);
                indexerClient.deleteIndex(kind);
            }
        }

        validated = true;
    }

    public static List<String> schemaProblems(String schemaJson) {
        List<String> problems = new ArrayList<>();
        JsonObject configurations = findProperty(JsonParser.parseString(schemaJson), "Configurations");
        if (configurations == null) {
            problems.add("property Configurations not found");
            return problems;
        }
        if (!isNestedHint(configurations)) {
            problems.add("Configurations is not marked x-osdu-indexing nested");
        }
        JsonObject paths = findProperty(configurations, "Paths");
        if (paths == null) {
            problems.add("property Configurations.Paths not found");
        } else if (!isNestedHint(paths)) {
            problems.add("Configurations.Paths is not marked x-osdu-indexing nested");
        }
        return problems;
    }

    public static List<String> mappingProblems(TypeMapping mapping) {
        List<String> problems = new ArrayList<>();
        Property data = mapping == null ? null : mapping.properties().get("data");
        if (data == null || !data.isObject()) {
            problems.add("data is not mapped as object");
            return problems;
        }
        Map<String, Property> dataProperties = data.object().properties();
        if (!dataProperties.containsKey("Code")) {
            problems.add("data.Code is not mapped");
        }
        Property configurations = dataProperties.get("Configurations");
        if (configurations == null || !configurations.isNested()) {
            problems.add("data.Configurations is not nested");
            return problems;
        }
        Property paths = configurations.nested().properties().get("Paths");
        if (paths == null || !paths.isNested()) {
            problems.add("data.Configurations.Paths is not nested");
        }
        return problems;
    }

    // Depth-first search for the JSON schema definition of a property with the given name
    private static JsonObject findProperty(JsonElement node, String name) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            JsonElement properties = object.get("properties");
            if (properties != null && properties.isJsonObject()) {
                JsonElement property = properties.getAsJsonObject().get(name);
                if (property != null && property.isJsonObject()) {
                    return property.getAsJsonObject();
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonObject found = findProperty(entry.getValue(), name);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isJsonArray()) {
            for (JsonElement element : node.getAsJsonArray()) {
                JsonObject found = findProperty(element, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean isNestedHint(JsonObject property) {
        JsonElement hint = property.get("x-osdu-indexing");
        if (hint == null || !hint.isJsonObject()) {
            return false;
        }
        JsonElement type = hint.getAsJsonObject().get("type");
        return type != null && "nested".equals(type.getAsString());
    }
}
