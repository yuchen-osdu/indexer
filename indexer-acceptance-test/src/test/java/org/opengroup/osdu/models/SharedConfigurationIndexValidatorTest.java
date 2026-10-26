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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SharedConfigurationIndexValidatorTest {

    @Test
    void testSchemaShouldBeValid() throws IOException {
        assertTrue(SharedConfigurationIndexValidator.schemaProblems(readTestSchema()).isEmpty());
    }

    @Test
    void schemaWithoutNestedHintsShouldBeReported() throws IOException {
        String schema = readTestSchema().replace("\"nested\"", "\"flattened\"");

        List<String> problems = SharedConfigurationIndexValidator.schemaProblems(schema);

        assertEquals(List.of(
            "Configurations is not marked x-osdu-indexing nested",
            "Configurations.Paths is not marked x-osdu-indexing nested"), problems);
    }

    @Test
    void schemaWithoutConfigurationsShouldBeReported() {
        List<String> problems = SharedConfigurationIndexValidator.schemaProblems("{\"properties\":{\"data\":{}}}");

        assertEquals(List.of("property Configurations not found"), problems);
    }

    @Test
    void nestedMappingShouldBeValid() {
        TypeMapping mapping = TypeMapping.of(m -> m
            .properties("data", data -> data.object(o -> o
                .properties("Code", code -> code.text(t -> t))
                .properties("Configurations", configurations -> configurations.nested(n -> n
                    .properties("Paths", paths -> paths.nested(p -> p)))))));

        assertTrue(SharedConfigurationIndexValidator.mappingProblems(mapping).isEmpty());
    }

    @Test
    void flatMappingShouldBeReported() {
        TypeMapping mapping = TypeMapping.of(m -> m
            .properties("data", data -> data.object(o -> o
                .properties("Configurations", configurations -> configurations.object(c -> c
                    .properties("Paths", paths -> paths.object(p -> p)))))));

        List<String> problems = SharedConfigurationIndexValidator.mappingProblems(mapping);

        assertEquals(List.of("data.Code is not mapped", "data.Configurations is not nested"), problems);
    }

    @Test
    void nestedConfigurationsWithFlatPathsShouldBeReported() {
        TypeMapping mapping = TypeMapping.of(m -> m
            .properties("data", data -> data.object(o -> o
                .properties("Code", code -> code.keyword(k -> k))
                .properties("Configurations", configurations -> configurations.nested(n -> n
                    .properties("Paths", paths -> paths.object(p -> p)))))));

        List<String> problems = SharedConfigurationIndexValidator.mappingProblems(mapping);

        assertEquals(List.of("data.Configurations.Paths is not nested"), problems);
    }

    @Test
    void mappingWithoutDataShouldBeReported() {
        List<String> problems = SharedConfigurationIndexValidator.mappingProblems(TypeMapping.of(m -> m));

        assertEquals(List.of("data is not mapped as object"), problems);
    }

    private String readTestSchema() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
            "/testData/osdu_wks_IndexPropertyPathConfiguration_v1_extended.schema.json")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
