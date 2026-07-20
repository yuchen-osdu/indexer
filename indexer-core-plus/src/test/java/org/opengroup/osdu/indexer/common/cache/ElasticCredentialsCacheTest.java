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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.lettuce.core.RedisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.model.search.ClusterSettings;

@ExtendWith(MockitoExtension.class)
class ElasticCredentialsCacheTest {

    @Mock
    private RedisCache<String, ClusterSettings> redisCache;

    @Mock
    private ClusterSettings clusterSettings;

    private ElasticCredentialsCache elasticCredentialsCache;

    private static final String TEST_KEY = "test-key";
    private static final String TEST_KEY_2 = "test-key-2";

    @BeforeEach
    void setUp() {
        elasticCredentialsCache = new ElasticCredentialsCache(redisCache);
    }

    @Test
    void shouldInstantiateAndImplementAutoCloseable() {
        // Assert
        assertNotNull(elasticCredentialsCache);
        AutoCloseable autoCloseable = elasticCredentialsCache;
        assertNotNull(autoCloseable);
    }

    @Test
    void shouldPutValueIntoCache() {
        // Arrange
        doNothing().when(redisCache).put(anyString(), any(ClusterSettings.class));

        // Act
        elasticCredentialsCache.put(TEST_KEY, clusterSettings);

        // Assert
        verify(redisCache, times(1)).put(TEST_KEY, clusterSettings);
    }

    @Test
    void shouldDelegatePutToRedisCache() {
        // Arrange
        doNothing().when(redisCache).put(anyString(), any(ClusterSettings.class));

        // Act
        elasticCredentialsCache.put(TEST_KEY, clusterSettings);

        // Assert
        verify(redisCache).put(TEST_KEY, clusterSettings);
    }

    @Test
    void shouldHandleMultiplePutOperations() {
        // Arrange
        ClusterSettings settings2 = org.mockito.Mockito.mock(ClusterSettings.class);
        doNothing().when(redisCache).put(anyString(), any(ClusterSettings.class));

        // Act
        elasticCredentialsCache.put(TEST_KEY, clusterSettings);
        elasticCredentialsCache.put(TEST_KEY_2, settings2);

        // Assert
        verify(redisCache, times(1)).put(TEST_KEY, clusterSettings);
        verify(redisCache, times(1)).put(TEST_KEY_2, settings2);
    }

    @Test
    void shouldPutWithDifferentKeys() {
        // Arrange
        String key1 = "key-1";
        String key2 = "key-2";
        doNothing().when(redisCache).put(anyString(), any(ClusterSettings.class));

        // Act
        elasticCredentialsCache.put(key1, clusterSettings);
        elasticCredentialsCache.put(key2, clusterSettings);

        // Assert
        verify(redisCache, times(1)).put(key1, clusterSettings);
        verify(redisCache, times(1)).put(key2, clusterSettings);
    }

    @Test
    void shouldOverwriteExistingKey() {
        // Arrange
        ClusterSettings newSettings = org.mockito.Mockito.mock(ClusterSettings.class);
        doNothing().when(redisCache).put(anyString(), any(ClusterSettings.class));

        // Act
        elasticCredentialsCache.put(TEST_KEY, clusterSettings);
        elasticCredentialsCache.put(TEST_KEY, newSettings);

        // Assert
        InOrder inOrder = inOrder(redisCache);
        inOrder.verify(redisCache).put(TEST_KEY, clusterSettings);
        inOrder.verify(redisCache).put(TEST_KEY, newSettings);
    }

    @Test
    void shouldGetValueFromCache() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(clusterSettings);

        // Act
        ClusterSettings result = elasticCredentialsCache.get(TEST_KEY);

        // Assert
        assertNotNull(result);
        assertEquals(clusterSettings, result);
        verify(redisCache, times(1)).get(TEST_KEY);
    }

    @Test
    void shouldDelegateGetToRedisCache() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(clusterSettings);

        // Act
        elasticCredentialsCache.get(TEST_KEY);

        // Assert
        verify(redisCache).get(TEST_KEY);
    }

    @Test
    void shouldReturnNullWhenKeyNotFound() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(null);

        // Act
        ClusterSettings result = elasticCredentialsCache.get(TEST_KEY);

        // Assert
        assertNull(result);
        verify(redisCache, times(1)).get(TEST_KEY);
    }

    @Test
    void shouldHandleMultipleGetOperations() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(clusterSettings);
        when(redisCache.get(TEST_KEY_2)).thenReturn(clusterSettings);

        // Act
        elasticCredentialsCache.get(TEST_KEY);
        elasticCredentialsCache.get(TEST_KEY_2);

        // Assert
        verify(redisCache, times(1)).get(TEST_KEY);
        verify(redisCache, times(1)).get(TEST_KEY_2);
    }

    @Test
    void shouldGetDifferentValuesForDifferentKeys() {
        // Arrange
        ClusterSettings settings2 = org.mockito.Mockito.mock(ClusterSettings.class);
        when(redisCache.get(TEST_KEY)).thenReturn(clusterSettings);
        when(redisCache.get(TEST_KEY_2)).thenReturn(settings2);

        // Act
        ClusterSettings result1 = elasticCredentialsCache.get(TEST_KEY);
        ClusterSettings result2 = elasticCredentialsCache.get(TEST_KEY_2);

        // Assert
        assertEquals(clusterSettings, result1);
        assertEquals(settings2, result2);
    }

    @Test
    void shouldHandleRedisExceptionAndReturnNull() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenThrow(new RedisException("Redis connection error"));
        doNothing().when(redisCache).delete(TEST_KEY);

        // Act
        ClusterSettings result = elasticCredentialsCache.get(TEST_KEY);

        // Assert
        assertNull(result);
    }

    @Test
    void shouldDeleteKeyOnRedisException() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenThrow(new RedisException("Format changed"));
        doNothing().when(redisCache).delete(TEST_KEY);

        // Act
        elasticCredentialsCache.get(TEST_KEY);

        // Assert
        verify(redisCache, times(1)).delete(TEST_KEY);
    }

    @Test
    void shouldCallGetBeforeDeleteOnException() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenThrow(new RedisException("Error"));
        doNothing().when(redisCache).delete(TEST_KEY);

        // Act
        elasticCredentialsCache.get(TEST_KEY);

        // Assert
        verify(redisCache, times(1)).get(TEST_KEY);
        verify(redisCache, times(1)).delete(TEST_KEY);
    }

    @Test
    void shouldNotDeleteKeyOnSuccessfulGet() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(clusterSettings);

        // Act
        elasticCredentialsCache.get(TEST_KEY);

        // Assert
        verify(redisCache, never()).delete(anyString());
    }

    @Test
    void shouldHandleRedisExceptionWithDifferentMessages() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenThrow(new RedisException("Connection timeout"));
        doNothing().when(redisCache).delete(TEST_KEY);

        // Act
        ClusterSettings result = elasticCredentialsCache.get(TEST_KEY);

        // Assert
        assertNull(result);
        verify(redisCache, times(1)).delete(TEST_KEY);
    }

    @Test
    void shouldReturnNullForEachExceptionThrown() {
        // Arrange
        when(redisCache.get(TEST_KEY))
                .thenThrow(new RedisException("Error 1"))
                .thenThrow(new RedisException("Error 2"));
        doNothing().when(redisCache).delete(TEST_KEY);

        // Act
        ClusterSettings result1 = elasticCredentialsCache.get(TEST_KEY);
        ClusterSettings result2 = elasticCredentialsCache.get(TEST_KEY);

        // Assert
        assertNull(result1);
        assertNull(result2);
        verify(redisCache, times(2)).delete(TEST_KEY);
    }

    @Test
    void shouldDeleteKeyFromCache() {
        // Arrange
        doNothing().when(redisCache).delete(TEST_KEY);

        // Act
        elasticCredentialsCache.delete(TEST_KEY);

        // Assert
        verify(redisCache, times(1)).delete(TEST_KEY);
    }

    @Test
    void shouldDelegateDeleteToRedisCache() {
        // Arrange
        doNothing().when(redisCache).delete(anyString());

        // Act
        elasticCredentialsCache.delete(TEST_KEY);

        // Assert
        verify(redisCache).delete(TEST_KEY);
    }

    @Test
    void shouldHandleMultipleDeleteOperations() {
        // Arrange
        doNothing().when(redisCache).delete(anyString());

        // Act
        elasticCredentialsCache.delete(TEST_KEY);
        elasticCredentialsCache.delete(TEST_KEY_2);

        // Assert
        verify(redisCache, times(1)).delete(TEST_KEY);
        verify(redisCache, times(1)).delete(TEST_KEY_2);
    }

    @Test
    void shouldDeleteDifferentKeys() {
        // Arrange
        String key1 = "key-to-delete-1";
        String key2 = "key-to-delete-2";
        doNothing().when(redisCache).delete(anyString());

        // Act
        elasticCredentialsCache.delete(key1);
        elasticCredentialsCache.delete(key2);

        // Assert
        verify(redisCache, times(1)).delete(key1);
        verify(redisCache, times(1)).delete(key2);
    }

    @Test
    void shouldClearAllEntriesFromCache() {
        // Arrange
        doNothing().when(redisCache).clearAll();

        // Act
        elasticCredentialsCache.clearAll();

        // Assert
        verify(redisCache, times(1)).clearAll();
    }

    @Test
    void shouldDelegateClearAllToRedisCache() {
        // Arrange
        doNothing().when(redisCache).clearAll();

        // Act
        elasticCredentialsCache.clearAll();

        // Assert
        verify(redisCache).clearAll();
    }

    @Test
    void shouldHandleMultipleClearAllCalls() {
        // Arrange
        doNothing().when(redisCache).clearAll();

        // Act
        elasticCredentialsCache.clearAll();
        elasticCredentialsCache.clearAll();
        elasticCredentialsCache.clearAll();

        // Assert
        verify(redisCache, times(3)).clearAll();
    }

    @Test
    void shouldCloseCache() {
        // Arrange
        doNothing().when(redisCache).close();

        // Act
        elasticCredentialsCache.close();

        // Assert
        verify(redisCache, times(1)).close();
    }

    @Test
    void shouldDelegateCloseToRedisCache() {
        // Arrange
        doNothing().when(redisCache).close();

        // Act
        elasticCredentialsCache.close();

        // Assert
        verify(redisCache).close();
    }

    @Test
    void shouldHandleMultipleCloseCalls() {
        // Arrange
        doNothing().when(redisCache).close();

        // Act
        elasticCredentialsCache.close();
        elasticCredentialsCache.close();

        // Assert
        verify(redisCache, times(2)).close();
    }

    @Test
    void shouldHandlePutGetDeleteSequence() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(clusterSettings);
        doNothing().when(redisCache).put(anyString(), any(ClusterSettings.class));
        doNothing().when(redisCache).delete(anyString());

        // Act
        elasticCredentialsCache.put(TEST_KEY, clusterSettings);
        ClusterSettings result = elasticCredentialsCache.get(TEST_KEY);
        elasticCredentialsCache.delete(TEST_KEY);

        // Assert
        assertEquals(clusterSettings, result);
        verify(redisCache, times(1)).put(TEST_KEY, clusterSettings);
        verify(redisCache, times(1)).get(TEST_KEY);
        verify(redisCache, times(1)).delete(TEST_KEY);
    }

    @Test
    void shouldHandleGetAfterException() {
        // Arrange
        when(redisCache.get(TEST_KEY))
                .thenThrow(new RedisException("First error"))
                .thenReturn(clusterSettings);
        doNothing().when(redisCache).delete(anyString());

        // Act
        ClusterSettings result1 = elasticCredentialsCache.get(TEST_KEY);
        ClusterSettings result2 = elasticCredentialsCache.get(TEST_KEY);

        // Assert
        assertNull(result1);
        assertEquals(clusterSettings, result2);
        verify(redisCache, times(1)).delete(TEST_KEY);
    }

    @Test
    void shouldHandleOperationsAfterClearAll() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(clusterSettings);
        doNothing().when(redisCache).clearAll();
        doNothing().when(redisCache).put(anyString(), any(ClusterSettings.class));

        // Act
        elasticCredentialsCache.clearAll();
        elasticCredentialsCache.put(TEST_KEY, clusterSettings);
        ClusterSettings result = elasticCredentialsCache.get(TEST_KEY);

        // Assert
        verify(redisCache, times(1)).clearAll();
        assertEquals(clusterSettings, result);
    }

    @Test
    void shouldMaintainStateAcrossMultipleOperations() {
        // Arrange
        when(redisCache.get(TEST_KEY)).thenReturn(clusterSettings);
        when(redisCache.get(TEST_KEY_2)).thenReturn(clusterSettings);
        doNothing().when(redisCache).put(anyString(), any(ClusterSettings.class));
        doNothing().when(redisCache).delete(anyString());

        // Act & Assert
        elasticCredentialsCache.put(TEST_KEY, clusterSettings);
        verify(redisCache, times(1)).put(TEST_KEY, clusterSettings);

        ClusterSettings result1 = elasticCredentialsCache.get(TEST_KEY);
        assertEquals(clusterSettings, result1);

        elasticCredentialsCache.put(TEST_KEY_2, clusterSettings);
        verify(redisCache, times(1)).put(TEST_KEY_2, clusterSettings);

        ClusterSettings result2 = elasticCredentialsCache.get(TEST_KEY_2);
        assertEquals(clusterSettings, result2);

        elasticCredentialsCache.delete(TEST_KEY);
        verify(redisCache, times(1)).delete(TEST_KEY);
    }
}
