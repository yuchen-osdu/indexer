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
package org.opengroup.osdu.indexer.common.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@DisplayName("CorePlusConfigurationProperties Tests")
class CorePlusConfigurationPropertiesTest {

    private CorePlusConfigurationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CorePlusConfigurationProperties();
    }

    @Test
    @DisplayName("Test 01: Should instantiate correctly with proper structure and default values")
    void shouldInstantiateWithCorrectStructureAndDefaults() {
        // Assert - instantiation and parent class
        assertNotNull(properties);
        assertTrue(properties instanceof IndexerConfigurationProperties);

        // Assert - required annotations
        assertTrue(properties.getClass().isAnnotationPresent(Primary.class));
        assertTrue(properties.getClass().isAnnotationPresent(Configuration.class));
        assertTrue(properties.getClass().isAnnotationPresent(ConfigurationProperties.class));

        // Assert - default values
        assertEquals("elasticsearch", properties.getElasticsearchPropertiesPrefix());
        assertNull(properties.getRedisGroupPassword());
        assertEquals(30, properties.getRedisGroupExpiration());
        assertFalse(properties.getRedisGroupWithSsl());
        assertNull(properties.getRedisSearchPassword());
        assertEquals(3600, properties.getRedisSearchExpiration());
        assertFalse(properties.getRedisSearchWithSsl());
    }

    // ==================== Test 4-10: Getters and Setters ====================

    @Test
    @DisplayName("Test 04: Should get and set elasticsearchPropertiesPrefix")
    void shouldGetAndSetElasticsearchPropertiesPrefix() {
        // Act
        properties.setElasticsearchPropertiesPrefix("custom-prefix");

        // Assert
        assertEquals("custom-prefix", properties.getElasticsearchPropertiesPrefix());
    }

    @Test
    @DisplayName("Test 05: Should get and set redisGroupPassword")
    void shouldGetAndSetRedisGroupPassword() {
        // Act
        properties.setRedisGroupPassword("secure-password");

        // Assert
        assertEquals("secure-password", properties.getRedisGroupPassword());
    }

    @Test
    @DisplayName("Test 06: Should get and set redisGroupExpiration")
    void shouldGetAndSetRedisGroupExpiration() {
        // Act
        properties.setRedisGroupExpiration(60);

        // Assert
        assertEquals(60, properties.getRedisGroupExpiration());
    }

    @Test
    @DisplayName("Test 07: Should get and set redisGroupWithSsl")
    void shouldGetAndSetRedisGroupWithSsl() {
        // Act
        properties.setRedisGroupWithSsl(true);

        // Assert
        assertTrue(properties.getRedisGroupWithSsl());
    }

    @Test
    @DisplayName("Test 08: Should get and set redisSearchPassword")
    void shouldGetAndSetRedisSearchPassword() {
        // Act
        properties.setRedisSearchPassword("search-password");

        // Assert
        assertEquals("search-password", properties.getRedisSearchPassword());
    }

    @Test
    @DisplayName("Test 09: Should get and set redisSearchExpiration")
    void shouldGetAndSetRedisSearchExpiration() {
        // Act
        properties.setRedisSearchExpiration(7200);

        // Assert
        assertEquals(7200, properties.getRedisSearchExpiration());
    }

    @Test
    @DisplayName("Test 10: Should get and set redisSearchWithSsl")
    void shouldGetAndSetRedisSearchWithSsl() {
        // Act
        properties.setRedisSearchWithSsl(true);

        // Assert
        assertTrue(properties.getRedisSearchWithSsl());
    }

    // ==================== Test 11-15: Null Handling ====================

    @Test
    @DisplayName("Test 11: Should handle null values for String properties")
    void shouldHandleNullValuesForStringProperties() {
        // Act
        properties.setElasticsearchPropertiesPrefix(null);
        properties.setRedisGroupPassword(null);
        properties.setRedisSearchPassword(null);

        // Assert
        assertNull(properties.getElasticsearchPropertiesPrefix());
        assertNull(properties.getRedisGroupPassword());
        assertNull(properties.getRedisSearchPassword());
    }

    @Test
    @DisplayName("Test 12: Should handle null values for Integer properties")
    void shouldHandleNullValuesForIntegerProperties() {
        // Act
        properties.setRedisGroupExpiration(null);
        properties.setRedisSearchExpiration(null);

        // Assert
        assertNull(properties.getRedisGroupExpiration());
        assertNull(properties.getRedisSearchExpiration());
    }

    @Test
    @DisplayName("Test 13: Should handle null values for Boolean properties")
    void shouldHandleNullValuesForBooleanProperties() {
        // Act
        properties.setRedisGroupWithSsl(null);
        properties.setRedisSearchWithSsl(null);

        // Assert
        assertNull(properties.getRedisGroupWithSsl());
        assertNull(properties.getRedisSearchWithSsl());
    }

    @Test
    @DisplayName("Test 14: Should handle empty string values")
    void shouldHandleEmptyStringValues() {
        // Act
        properties.setElasticsearchPropertiesPrefix("");
        properties.setRedisGroupPassword("");
        properties.setRedisSearchPassword("");

        // Assert
        assertEquals("", properties.getElasticsearchPropertiesPrefix());
        assertEquals("", properties.getRedisGroupPassword());
        assertEquals("", properties.getRedisSearchPassword());
    }

    @Test
    @DisplayName("Test 15: Should handle zero values for expiration")
    void shouldHandleZeroValuesForExpiration() {
        // Act
        properties.setRedisGroupExpiration(0);
        properties.setRedisSearchExpiration(0);

        // Assert
        assertEquals(0, properties.getRedisGroupExpiration());
        assertEquals(0, properties.getRedisSearchExpiration());
    }

    // ==================== Test 16-20: Integration Scenarios ====================

    @Test
    @DisplayName("Test 16: Should toggle boolean values")
    void shouldToggleBooleanValues() {
        // Act & Assert - Initial state
        assertFalse(properties.getRedisGroupWithSsl());
        assertFalse(properties.getRedisSearchWithSsl());

        // Toggle to true
        properties.setRedisGroupWithSsl(true);
        properties.setRedisSearchWithSsl(true);
        assertTrue(properties.getRedisGroupWithSsl());
        assertTrue(properties.getRedisSearchWithSsl());

        // Toggle back to false
        properties.setRedisGroupWithSsl(false);
        properties.setRedisSearchWithSsl(false);
        assertFalse(properties.getRedisGroupWithSsl());
        assertFalse(properties.getRedisSearchWithSsl());
    }

    @Test
    @DisplayName("Test 17: Should set all properties simultaneously")
    void shouldSetAllPropertiesSimultaneously() {
        // Act
        properties.setElasticsearchPropertiesPrefix("custom-es");
        properties.setRedisGroupPassword("group-pass");
        properties.setRedisGroupExpiration(120);
        properties.setRedisGroupWithSsl(true);
        properties.setRedisSearchPassword("search-pass");
        properties.setRedisSearchExpiration(1800);
        properties.setRedisSearchWithSsl(true);

        // Assert
        assertEquals("custom-es", properties.getElasticsearchPropertiesPrefix());
        assertEquals("group-pass", properties.getRedisGroupPassword());
        assertEquals(120, properties.getRedisGroupExpiration());
        assertTrue(properties.getRedisGroupWithSsl());
        assertEquals("search-pass", properties.getRedisSearchPassword());
        assertEquals(1800, properties.getRedisSearchExpiration());
        assertTrue(properties.getRedisSearchWithSsl());
    }

    @Test
    @DisplayName("Test 18: Should maintain property independence")
    void shouldMaintainPropertyIndependence() {
        // Act
        properties.setRedisGroupPassword("group-password");
        properties.setRedisSearchPassword("search-password");
        properties.setRedisGroupExpiration(100);
        properties.setRedisSearchExpiration(200);

        // Assert - Each property maintains its own value
        assertEquals("group-password", properties.getRedisGroupPassword());
        assertEquals("search-password", properties.getRedisSearchPassword());
        assertEquals(100, properties.getRedisGroupExpiration());
        assertEquals(200, properties.getRedisSearchExpiration());
    }

    @Test
    @DisplayName("Test 19: Should handle large expiration values")
    void shouldHandleLargeExpirationValues() {
        // Act
        properties.setRedisGroupExpiration(86400);  // 1 day in seconds
        properties.setRedisSearchExpiration(604800); // 1 week in seconds

        // Assert
        assertEquals(86400, properties.getRedisGroupExpiration());
        assertEquals(604800, properties.getRedisSearchExpiration());
    }

    @Test
    @DisplayName("Test 20: Should create independent instances")
    void shouldCreateIndependentInstances() {
        // Act
        CorePlusConfigurationProperties props1 = new CorePlusConfigurationProperties();
        CorePlusConfigurationProperties props2 = new CorePlusConfigurationProperties();

        props1.setRedisGroupPassword("password1");
        props2.setRedisGroupPassword("password2");
        props1.setRedisGroupExpiration(100);
        props2.setRedisGroupExpiration(200);

        // Assert - Instances are independent
        assertEquals("password1", props1.getRedisGroupPassword());
        assertEquals("password2", props2.getRedisGroupPassword());
        assertEquals(100, props1.getRedisGroupExpiration());
        assertEquals(200, props2.getRedisGroupExpiration());
    }
}
