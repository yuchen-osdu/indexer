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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.provider.interfaces.IIndexCache;

@ExtendWith(MockitoExtension.class)
@DisplayName("IndexCache Tests")
class IndexCacheTest {

    @Mock
    private RedisCache<String, Boolean> redisCache;

    private IndexCache indexCache;

    private static final String TEST_KEY = "test-key";
    private static final String TEST_KEY_2 = "test-key-2";
    private static final Boolean TEST_VALUE_TRUE = true;
    private static final Boolean TEST_VALUE_FALSE = false;

    @BeforeEach
    void setUp() {
        indexCache = new IndexCache(redisCache);
    }

    // ==================== Test 1-3: Instantiation and Interfaces ====================

    @Test
    @DisplayName("Test 01: Should instantiate successfully and implement required interfaces")
    void shouldInstantiateAndImplementRequiredInterfaces() {
        // Assert
        assertNotNull(indexCache);
        assertTrue(indexCache instanceof IIndexCache);
        assertTrue(indexCache instanceof AutoCloseable);
    }

    // ==================== Test 4-7: Put Operation ====================

    @Test
    @DisplayName("Test 04: Should delegate put operation to RedisCache")
    void shouldDelegatePutOperationToRedisCache() {
        // Act
        indexCache.put(TEST_KEY, TEST_VALUE_TRUE);

        // Assert
        verify(redisCache, times(1)).put(TEST_KEY, TEST_VALUE_TRUE);
    }

    @Test
    @DisplayName("Test 05: Should put multiple values")
    void shouldPutMultipleValues() {
        // Act
        indexCache.put(TEST_KEY, TEST_VALUE_TRUE);
        indexCache.put(TEST_KEY_2, TEST_VALUE_FALSE);

        // Assert
        verify(redisCache).put(TEST_KEY, TEST_VALUE_TRUE);
        verify(redisCache).put(TEST_KEY_2, TEST_VALUE_FALSE);
    }

    @Test
    @DisplayName("Test 06: Should put with null key")
    void shouldPutWithNullKey() {
        // Act
        indexCache.put(null, TEST_VALUE_TRUE);

        // Assert
        verify(redisCache).put(null, TEST_VALUE_TRUE);
    }

    @Test
    @DisplayName("Test 07: Should put with null value")
    void shouldPutWithNullValue() {
        // Act
        indexCache.put(TEST_KEY, null);

        // Assert
        verify(redisCache).put(TEST_KEY, null);
    }

    // ==================== Test 8-11: Get Operation ====================

    @Test
    @DisplayName("Test 08: Should delegate get operation and return value")
    void shouldDelegateGetOperationAndReturnValue() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(TEST_VALUE_TRUE);

        // Act
        Boolean result = indexCache.get(TEST_KEY);

        // Assert
        assertNotNull(result);
        assertTrue(result);
        verify(redisCache, times(1)).get(TEST_KEY);
    }

    @Test
    @DisplayName("Test 09: Should return false when getting false value")
    void shouldReturnFalseWhenGettingFalseValue() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(TEST_VALUE_FALSE);

        // Act
        Boolean result = indexCache.get(TEST_KEY);

        // Assert
        assertNotNull(result);
        assertFalse(result);
    }

    @Test
    @DisplayName("Test 10: Should return null for non-existent key")
    void shouldReturnNullForNonExistentKey() {
        // Arrange
        when(redisCache.get(anyString())).thenReturn(null);

        // Act
        Boolean result = indexCache.get("non-existent-key");

        // Assert
        assertNull(result);
    }

    @Test
    @DisplayName("Test 11: Should get multiple values")
    void shouldGetMultipleValues() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(TEST_VALUE_TRUE);
        when(redisCache.get(TEST_KEY_2)).thenReturn(TEST_VALUE_FALSE);

        // Act
        Boolean result1 = indexCache.get(TEST_KEY);
        Boolean result2 = indexCache.get(TEST_KEY_2);

        // Assert
        assertTrue(result1);
        assertFalse(result2);
    }

    // ==================== Test 12-14: Delete Operation ====================

    @Test
    @DisplayName("Test 12: Should delegate delete operation to RedisCache")
    void shouldDelegateDeleteOperationToRedisCache() {
        // Act
        indexCache.delete(TEST_KEY);

        // Assert
        verify(redisCache, times(1)).delete(TEST_KEY);
    }

    @Test
    @DisplayName("Test 13: Should delete multiple keys")
    void shouldDeleteMultipleKeys() {
        // Act
        indexCache.delete(TEST_KEY);
        indexCache.delete(TEST_KEY_2);

        // Assert
        verify(redisCache).delete(TEST_KEY);
        verify(redisCache).delete(TEST_KEY_2);
    }

    @Test
    @DisplayName("Test 14: Should delete with null key")
    void shouldDeleteWithNullKey() {
        // Act
        indexCache.delete(null);

        // Assert
        verify(redisCache).delete(null);
    }

    // ==================== Test 15-16: ClearAll Operation ====================

    @Test
    @DisplayName("Test 15: Should delegate clearAll operation to RedisCache")
    void shouldDelegateClearAllOperationToRedisCache() {
        // Act
        indexCache.clearAll();

        // Assert
        verify(redisCache, times(1)).clearAll();
    }

    @Test
    @DisplayName("Test 16: Should call clearAll multiple times")
    void shouldCallClearAllMultipleTimes() {
        // Act
        indexCache.clearAll();
        indexCache.clearAll();
        indexCache.clearAll();

        // Assert
        verify(redisCache, times(3)).clearAll();
    }

    // ==================== Test 17-19: Close Operation ====================

    @Test
    @DisplayName("Test 17: Should delegate close operation to RedisCache")
    void shouldDelegateCloseOperationToRedisCache() throws Exception {
        // Arrange
        doNothing().when(redisCache).close();

        // Act
        indexCache.close();

        // Assert
        verify(redisCache, times(1)).close();
    }

    @Test
    @DisplayName("Test 18: Should propagate RuntimeException from close")
    void shouldPropagateRuntimeExceptionFromClose() {
        // Arrange
        doThrow(new RuntimeException("Close failed")).when(redisCache).close();

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            indexCache.close();
        });
        assertEquals("Close failed", exception.getMessage());
        verify(redisCache).close();
    }

    @Test
    @DisplayName("Test 19: Should work with try-with-resources")
    void shouldWorkWithTryWithResources() {
        // Arrange
        doNothing().when(redisCache).close();

        // Act & Assert
        assertDoesNotThrow(() -> {
            try (IndexCache cache = new IndexCache(redisCache)) {
                cache.put(TEST_KEY, TEST_VALUE_TRUE);
            }
        });
        verify(redisCache).close();
    }

    // ==================== Test 20: Integration Scenario ====================

    @Test
    @DisplayName("Test 20: Should handle complete cache lifecycle")
    void shouldHandleCompleteCacheLifecycle() throws Exception {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(TEST_VALUE_TRUE);
        when(redisCache.get(TEST_KEY_2)).thenReturn(TEST_VALUE_FALSE);
        doNothing().when(redisCache).close();

        // Act - Complete lifecycle with reads
        indexCache.put(TEST_KEY, TEST_VALUE_TRUE);
        indexCache.put(TEST_KEY_2, TEST_VALUE_FALSE);

        // Actually retrieve the values
        Boolean value1 = indexCache.get(TEST_KEY);
        Boolean value2 = indexCache.get(TEST_KEY_2);

        indexCache.delete(TEST_KEY);
        indexCache.clearAll();
        indexCache.close();

        // Assert
        assertEquals(TEST_VALUE_TRUE, value1);
        assertEquals(TEST_VALUE_FALSE, value2);

        verify(redisCache).put(TEST_KEY, TEST_VALUE_TRUE);
        verify(redisCache).put(TEST_KEY_2, TEST_VALUE_FALSE);
        verify(redisCache).get(TEST_KEY);
        verify(redisCache).get(TEST_KEY_2);
        verify(redisCache).delete(TEST_KEY);
        verify(redisCache).clearAll();
        verify(redisCache).close();
    }
}
