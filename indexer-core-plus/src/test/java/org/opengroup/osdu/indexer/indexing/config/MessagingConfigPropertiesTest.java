/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.opengroup.osdu.indexer.indexing.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Optimized unit tests for MessagingConfigProperties class
 * Coverage: 100% with minimal test cases
 */
@DisplayName("MessagingConfigProperties Unit Tests")
class MessagingConfigPropertiesTest {

    private MessagingConfigProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MessagingConfigProperties();
    }

    @Test
    @DisplayName("Should set and get all properties with valid values")
    void testAllPropertiesGettersAndSetters() {
        // Arrange
        String recordsTopic = "records-changed";
        String schemaTopic = "schema-changed";
        String statusTopic = "status-changed";
        String reprocessTopic = "reprocess";
        String reindexTopic = "reindex";

        // Act
        properties.setRecordsChangedTopicName(recordsTopic);
        properties.setSchemaChangedTopicName(schemaTopic);
        properties.setStatusChangedTopicName(statusTopic);
        properties.setReprocessTopicName(reprocessTopic);
        properties.setReindexTopicName(reindexTopic);

        // Assert
        assertEquals(recordsTopic, properties.getRecordsChangedTopicName());
        assertEquals(schemaTopic, properties.getSchemaChangedTopicName());
        assertEquals(statusTopic, properties.getStatusChangedTopicName());
        assertEquals(reprocessTopic, properties.getReprocessTopicName());
        assertEquals(reindexTopic, properties.getReindexTopicName());
    }

    @Test
    @DisplayName("Should handle null values for all properties")
    void testAllPropertiesWithNullValues() {
        // Act
        properties.setRecordsChangedTopicName(null);
        properties.setSchemaChangedTopicName(null);
        properties.setStatusChangedTopicName(null);
        properties.setReprocessTopicName(null);
        properties.setReindexTopicName(null);

        // Assert
        assertNull(properties.getRecordsChangedTopicName());
        assertNull(properties.getSchemaChangedTopicName());
        assertNull(properties.getStatusChangedTopicName());
        assertNull(properties.getReprocessTopicName());
        assertNull(properties.getReindexTopicName());
    }

    @Test
    @DisplayName("Should initialize with default null values")
    void testDefaultInitialization() {
        // Assert - verify all properties are null by default
        assertNull(properties.getRecordsChangedTopicName());
        assertNull(properties.getSchemaChangedTopicName());
        assertNull(properties.getStatusChangedTopicName());
        assertNull(properties.getReprocessTopicName());
        assertNull(properties.getReindexTopicName());
    }

    @Test
    @DisplayName("Should handle empty strings and special characters")
    void testEdgeCases() {
        // Test empty strings
        properties.setRecordsChangedTopicName("");
        assertEquals("", properties.getRecordsChangedTopicName());

        // Test special characters
        String specialTopic = "topic-with-special!@#$%^&*()_+characters";
        properties.setSchemaChangedTopicName(specialTopic);
        assertEquals(specialTopic, properties.getSchemaChangedTopicName());

        // Test whitespace
        String whitespace = "   \t\n   ";
        properties.setStatusChangedTopicName(whitespace);
        assertEquals(whitespace, properties.getStatusChangedTopicName());

        // Test value overwriting
        properties.setReprocessTopicName("initial");
        properties.setReprocessTopicName("updated");
        assertEquals("updated", properties.getReprocessTopicName());
    }

    @Test
    @DisplayName("Should be a valid Spring Configuration bean")
    void testSpringConfigurationBean() {
        // Arrange
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(MessagingConfigProperties.class);

        // Act & Assert
        contextRunner.run(context -> {
            // Verify bean is created and can be retrieved
            assertNotNull(context.getBean(MessagingConfigProperties.class));
            assertTrue(context.containsBean("messagingConfigProperties"));
        });
    }

    @Test
    @DisplayName("Should have proper Spring annotations")
    void testSpringAnnotations() {
        // Assert - verify class has required annotations
        assertTrue(properties.getClass().isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
        assertTrue(properties.getClass().isAnnotationPresent(
                org.springframework.boot.context.properties.ConfigurationProperties.class));
    }

    @Test
    @DisplayName("Should have deprecated annotation on defaultRelativeIndexerWorkerUrl field")
    void testDeprecatedField() {
        // Verify @Deprecated annotation exists
        try {
            var field = MessagingConfigProperties.class
                    .getDeclaredField("defaultRelativeIndexerWorkerUrl");
            assertTrue(field.isAnnotationPresent(Deprecated.class));
        } catch (NoSuchFieldException e) {
            fail("Field defaultRelativeIndexerWorkerUrl should exist");
        }
    }
}
