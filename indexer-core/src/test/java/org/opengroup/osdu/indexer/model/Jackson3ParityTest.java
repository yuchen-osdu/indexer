/*
 * Copyright 2017-2025, The Open Group
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

package org.opengroup.osdu.indexer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.indexer.model.indexproperty.AugmenterConfiguration;
import org.opengroup.osdu.indexer.service.AugmenterConfigurationServiceImpl;
import org.opengroup.osdu.indexer.util.geo.decimator.DecimatedResult;
import org.opengroup.osdu.indexer.util.geo.decimator.DouglasPeuckerReducer;
import org.opengroup.osdu.indexer.util.geo.decimator.GeoShapeDecimator;
import org.opengroup.osdu.indexer.util.geo.decimator.GeometryDecimator;
import org.opengroup.osdu.indexer.util.parser.GeoShapeParser;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Jackson 3 ignores the Jackson 2 {@code com.fasterxml.jackson.databind.annotation} (de)serializer annotations and
 * changes several mapper defaults. The geo-shape documents that the indexer writes to Elasticsearch and the
 * index-augmenter configurations it reads are produced by locally created mappers, so this test compares their
 * output with golden files recorded from the Jackson 2 implementation (master before the Spring Boot 4 migration).
 * <p>
 * The golden files under {@code jackson3-parity/} were recorded with {@code -Djackson3.parity.record=true}
 * on that Jackson 2 code base; outputs are canonicalised (sorted keys) so only content differences fail.
 */
class Jackson3ParityTest {

    private static final String RECORD_PROPERTY = "jackson3.parity.record";
    private static final Path GOLDEN_DIR = Paths.get("src/test/resources/jackson3-parity");

    private static final List<String> PARSING_INPUTS = List.of(
        "invalid_feature_collection", "invalid_point_missing_latitude", "invalid_point_nan", "invalid_point",
        "invalid_polygon_invalid_ring", "invalid_polygon_malformed_latitude", "invalid_polygon_unclosed_polygon_ring",
        "invalid_shape", "line_string_with_z_coordinate", "missing_mandatory_attribute",
        "multi_polygon_crossing_dateline", "multi_polygon_with_z_coordinates", "unsupported_type_feature",
        "valid_feature_collection_with_z_coordinate", "valid_feature_collection", "valid_geometry_collection",
        "valid_line_string", "valid_milti_line_string_with_z_coordinate", "valid_multi_line_string",
        "valid_multi_point", "valid_multi_polygon", "valid_point_with_property", "valid_point", "valid_polygon");

    private static final List<String> DECIMATOR_INPUTS = List.of(
        "geometrycollection_linestring", "geometrycollection_multilinestring", "geometrycollection_multipoint",
        "geometrycollection_multipolygon", "geometrycollection_point",
        "geometrycollection_polygon", "geometrycollection_small_multilinestring", "point_2d", "point_3d");

    private static final List<String> CONFIGURATION_INPUTS = List.of(
        "/indexproperty/well_configuration_record.json",
        "/indexproperty/wellbore_configuration_record.json",
        "/indexproperty/welllog_configuration_record.json",
        "/indexproperty/value_extraction_match_configuration_record.json",
        "/indexproperty/augmented_schema/Configuration_Well.json",
        "/indexproperty/augmented_schema/Configuration_Wellbore.json",
        "/indexproperty/augmented_schema/Configuration_WellLog.json");

    private final Gson gson = new Gson();

    @Test
    void geoShapeParser_output_matches_jackson2() throws IOException {
        Map<String, String> results = new TreeMap<>();
        for (String name : PARSING_INPUTS) {
            Map<String, Object> shape = readMap("/geojson/parsing/input/" + name + ".json");
            results.put(name, outcome(() -> new GeoShapeParser().parseGeoJson(shape)));
        }
        verify("geo-shape-parser.json", results);
    }

    @Test
    void geoShapeDecimator_output_matches_jackson2() throws IOException {
        GeometryDecimator geometryDecimator = new GeometryDecimator();
        ReflectionTestUtils.setField(geometryDecimator, "reducer", new DouglasPeuckerReducer());
        GeoShapeDecimator decimator = new GeoShapeDecimator();
        ReflectionTestUtils.setField(decimator, "decimator", geometryDecimator);

        Map<String, String> results = new TreeMap<>();
        for (String name : DECIMATOR_INPUTS) {
            Map<String, Object> shape = readMap("/geo/decimator/" + name + ".json");
            results.put(name, outcome(() -> {
                DecimatedResult result = decimator.decimateShapeObj(shape);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("decimated", result.isDecimated());
                out.put("shape", result.getDecimatedShapeObj());
                return out;
            }));
        }
        verify("geo-shape-decimator.json", results);
    }

    @Test
    void augmenterConfiguration_matches_jackson2() throws Exception {
        Object mapper = ReflectionTestUtils.getField(new AugmenterConfigurationServiceImpl(), "objectMapper");
        Method write = mapper.getClass().getMethod("writeValueAsString", Object.class);
        Method read = mapper.getClass().getMethod("readValue", String.class, Class.class);

        Map<String, Object> inputs = new LinkedHashMap<>();
        for (String resource : CONFIGURATION_INPUTS) {
            inputs.put(resource, readMap(resource));
        }
        Map<String, Object> base = readMap("/indexproperty/well_configuration_record.json");
        inputs.put("unknown top-level property", withExtra(base, null, "UnknownProperty"));
        inputs.put("unknown property in Configurations[0]", withExtra(base, "Configurations", "UnknownProperty"));
        inputs.put("null for unknown property in Configurations[0]", withExtra(base, "Configurations", null));

        Map<String, String> results = new TreeMap<>();
        for (Map.Entry<String, Object> input : inputs.entrySet()) {
            results.put(input.getKey(), outcome(() -> {
                String data = (String) write.invoke(mapper, input.getValue());
                AugmenterConfiguration configuration = (AugmenterConfiguration) read.invoke(mapper, data, AugmenterConfiguration.class);
                return configuration.toString() + " valid=" + configuration.isValid();
            }));
        }
        verify("augmenter-configuration.json", results);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> withExtra(Map<String, Object> base, String listProperty, String extraValue) {
        Map<String, Object> copy = gson.fromJson(gson.toJson(base), new TypeToken<Map<String, Object>>() {}.getType());
        Map<String, Object> target = copy;
        if (listProperty != null) {
            target = ((List<Map<String, Object>>) copy.get(listProperty)).get(0);
        }
        target.put("UnknownProperty", extraValue);
        return copy;
    }

    private String outcome(ThrowingSupplier supplier) {
        try {
            return "OK " + canonical(supplier.get());
        } catch (Throwable e) {
            Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
            return "ERROR " + category(cause);
        }
    }

    private static String category(Throwable e) {
        if (e instanceof IllegalArgumentException) {
            return "IllegalArgumentException: " + e.getMessage();
        }
        String name = e.getClass().getName();
        return name.startsWith("com.fasterxml.jackson") || name.startsWith("tools.jackson") ? "JacksonException" : name;
    }

    private String canonical(Object value) {
        return gson.toJson(sorted(value));
    }

    @SuppressWarnings("unchecked")
    private Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), sorted(v)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(v -> result.add(sorted(v)));
            return result;
        }
        if (value instanceof Number number && !(value instanceof Double) && !(value instanceof Float)) {
            // Keep integral and floating values distinguishable after Gson serialisation.
            return number.getClass().getSimpleName() + ":" + number;
        }
        return value;
    }

    private void verify(String goldenFile, Map<String, String> actual) throws IOException {
        Path golden = GOLDEN_DIR.resolve(goldenFile);
        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(actual) + "\n";
        if (Boolean.getBoolean(RECORD_PROPERTY)) {
            Files.createDirectories(GOLDEN_DIR);
            Files.writeString(golden, json, StandardCharsets.UTF_8);
            return;
        }
        Map<String, String> expected = gson.fromJson(Files.readString(golden, StandardCharsets.UTF_8),
            new TypeToken<TreeMap<String, String>>() {}.getType());
        for (String key : Stream.concat(expected.keySet().stream(), actual.keySet().stream()).distinct().toList()) {
            assertEquals(expected.get(key), actual.get(key), goldenFile + " / " + key);
        }
    }

    private Map<String, Object> readMap(String resource) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return gson.fromJson(text, new TypeToken<Map<String, Object>>() {}.getType());
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Exception;
    }
}
