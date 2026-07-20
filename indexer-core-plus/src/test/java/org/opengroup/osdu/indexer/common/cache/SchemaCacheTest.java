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
package org.opengroup.osdu.indexer.common.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.cache.RedisCache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SchemaCache class
 * Coverage: All methods, edge cases, and exception scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SchemaCache Unit Tests")
class SchemaCacheTest {

    @Mock
    private RedisCache<String, String> redisCache;

    private SchemaCache schemaCache;

    @BeforeEach
    void setUp() {
        schemaCache = new SchemaCache(redisCache);
    }

    @Test
    @DisplayName("Should successfully put key-value pair into cache")
    void testPut_Success() {
        // Arrange
        String key = "schema:v1";
        String value = "{\"type\":\"object\"}";

        // Act
        schemaCache.put(key, value);

        // Assert
        verify(redisCache, times(1)).put(key, value);
    }

    @Test
    @DisplayName("Should handle put with null key")
    void testPut_NullKey() {
        // Arrange
        String value = "{\"type\":\"object\"}";

        // Act
        schemaCache.put(null, value);

        // Assert
        verify(redisCache, times(1)).put(null, value);
    }

    @Test
    @DisplayName("Should handle put with null value")
    void testPut_NullValue() {
        // Arrange
        String key = "schema:v1";

        // Act
        schemaCache.put(key, null);

        // Assert
        verify(redisCache, times(1)).put(key, null);
    }

    @Test
    @DisplayName("Should handle put with empty strings")
    void testPut_EmptyStrings() {
        // Arrange
        String key = "";
        String value = "";

        // Act
        schemaCache.put(key, value);

        // Assert
        verify(redisCache, times(1)).put(key, value);
    }

    @Test
    @DisplayName("Should successfully retrieve value from cache")
    void testGet_Success() {
        // Arrange
        String key = "schema:v1";
        String expectedValue = "{\"type\":\"object\"}";
        when(redisCache.get(key)).thenReturn(expectedValue);

        // Act
        String actualValue = schemaCache.get(key);

        // Assert
        assertEquals(expectedValue, actualValue);
        verify(redisCache, times(1)).get(key);
    }

    @Test
    @DisplayName("Should return null when key not found")
    void testGet_KeyNotFound() {
        // Arrange
        String key = "nonexistent:key";
        when(redisCache.get(key)).thenReturn(null);

        // Act
        String result = schemaCache.get(key);

        // Assert
        assertNull(result);
        verify(redisCache, times(1)).get(key);
    }

    @Test
    @DisplayName("Should handle get with null key")
    void testGet_NullKey() {
        // Arrange
        when(redisCache.get(null)).thenReturn(null);

        // Act
        String result = schemaCache.get(null);

        // Assert
        assertNull(result);
        verify(redisCache, times(1)).get(null);
    }

    @Test
    @DisplayName("Should handle get with empty string key")
    void testGet_EmptyKey() {
        // Arrange
        String key = "";
        String value = "some-value";
        when(redisCache.get(key)).thenReturn(value);

        // Act
        String result = schemaCache.get(key);

        // Assert
        assertEquals(value, result);
        verify(redisCache, times(1)).get(key);
    }

    @Test
    @DisplayName("Should successfully delete key from cache")
    void testDelete_Success() {
        // Arrange
        String key = "schema:v1";

        // Act
        schemaCache.delete(key);

        // Assert
        verify(redisCache, times(1)).delete(key);
    }

    @Test
    @DisplayName("Should handle delete with null key")
    void testDelete_NullKey() {
        // Act
        schemaCache.delete(null);

        // Assert
        verify(redisCache, times(1)).delete(null);
    }

    @Test
    @DisplayName("Should handle delete with empty string key")
    void testDelete_EmptyKey() {
        // Arrange
        String key = "";

        // Act
        schemaCache.delete(key);

        // Assert
        verify(redisCache, times(1)).delete(key);
    }

    @Test
    @DisplayName("Should successfully clear all cache entries")
    void testClearAll_Success() {
        // Act
        schemaCache.clearAll();

        // Assert
        verify(redisCache, times(1)).clearAll();
    }

    @Test
    @DisplayName("Should call clearAll multiple times without issues")
    void testClearAll_MultipleCalls() {
        // Act
        schemaCache.clearAll();
        schemaCache.clearAll();
        schemaCache.clearAll();

        // Assert
        verify(redisCache, times(3)).clearAll();
    }

    @Test
    @DisplayName("Should successfully close cache resource")
    void testClose_Success() throws Exception {
        // Act
        schemaCache.close();

        // Assert
        verify(redisCache, times(1)).close();
    }

    @Test
    @DisplayName("Should propagate exception when close fails")
    void testClose_ThrowsException(){
        // Arrange
        RuntimeException expectedException = new RuntimeException("Failed to close Redis connection");
        doThrow(expectedException).when(redisCache).close();

        // Act & Assert
        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> schemaCache.close());
        assertEquals(expectedException, thrownException);
        verify(redisCache, times(1)).close();
    }

    @Test
    @DisplayName("Should handle multiple operations in sequence")
    void testMultipleOperations_Sequence() {
        // Arrange
        String key1 = "schema:v1";
        String value1 = "{\"type\":\"object\"}";
        String key2 = "schema:v2";
        String value2 = "{\"type\":\"string\"}";

        when(redisCache.get(key1)).thenReturn(value1);
        when(redisCache.get(key2)).thenReturn(value2);

        // Act & Assert
        schemaCache.put(key1, value1);
        verify(redisCache, times(1)).put(key1, value1);

        String retrieved1 = schemaCache.get(key1);
        assertEquals(value1, retrieved1);

        schemaCache.put(key2, value2);
        verify(redisCache, times(1)).put(key2, value2);

        String retrieved2 = schemaCache.get(key2);
        assertEquals(value2, retrieved2);

        schemaCache.delete(key1);
        verify(redisCache, times(1)).delete(key1);

        schemaCache.clearAll();
        verify(redisCache, times(1)).clearAll();
    }

    @Test
    @DisplayName("Should handle special characters in keys and values")
    void testSpecialCharacters() {
        // Arrange
        String key = "schema:special!@#$%^&*()";
        String value = "{\"key\":\"value with spaces and 特殊字符\"}";
        when(redisCache.get(key)).thenReturn(value);

        // Act
        schemaCache.put(key, value);
        String result = schemaCache.get(key);

        // Assert
        verify(redisCache, times(1)).put(key, value);
        verify(redisCache, times(1)).get(key);
        assertEquals(value, result);
    }

    @Test
    @DisplayName("Should handle very long strings")
    void testLongStrings() {
        // Arrange
        String key = "schema:" + "x".repeat(1000);
        String value = "{\"data\":\"" + "y".repeat(10000) + "\"}";
        when(redisCache.get(key)).thenReturn(value);

        // Act
        schemaCache.put(key, value);
        String result = schemaCache.get(key);

        // Assert
        verify(redisCache, times(1)).put(key, value);
        assertEquals(value, result);
    }

    @Test
    @DisplayName("Should verify constructor initializes cache correctly")
    void testConstructor() {
        // Arrange & Act
        SchemaCache newCache = new SchemaCache(redisCache);

        // Assert
        assertNotNull(newCache);
    }

    @Test
    @DisplayName("Should handle try-with-resources pattern")
    void testTryWithResources() throws Exception {
        // Arrange
        RedisCache<String, String> mockCache = mock(RedisCache.class);

        // Act
        try (SchemaCache cache = new SchemaCache(mockCache)) {
            cache.put("key", "value");
        }

        // Assert
        verify(mockCache, times(1)).put("key", "value");
        verify(mockCache, times(1)).close();
    }
}
