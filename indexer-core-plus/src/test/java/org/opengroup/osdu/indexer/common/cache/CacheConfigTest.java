/*
 *  Copyright 2020-2023 Google LLC
 *  Copyright 2020-2023 EPAM Systems, Inc
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.cache.RedisCacheBuilder;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.model.search.ClusterSettings;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticCredentialsCache;
import org.opengroup.osdu.core.common.provider.interfaces.IIndexCache;
import org.opengroup.osdu.indexer.common.di.CorePlusConfigurationProperties;
import org.opengroup.osdu.indexer.provider.interfaces.ISchemaCache;

@ExtendWith(MockitoExtension.class)
class CacheConfigTest {

    @Mock
    private RedisCacheBuilder<String, String> schemaCacheBuilder;

    @Mock
    private RedisCacheBuilder<String, ClusterSettings> clusterSettingsCacheBuilder;

    @Mock
    private RedisCacheBuilder<String, Boolean> redisCacheBuilder;

    @Mock
    private CorePlusConfigurationProperties appProperties;

    @Mock
    private RedisCache<String, String> mockSchemaCache;

    @Mock
    private RedisCache<String, ClusterSettings> mockElasticCache;

    @Mock
    private RedisCache<String, Boolean> mockRedisCache;

    private CacheConfig cacheConfig;

    private static final String TEST_REDIS_HOST = "test-redis-host";
    private static final String TEST_REDIS_PORT = "6379";
    private static final String TEST_REDIS_PASSWORD = "test-password";
    private static final int TEST_EXPIRATION = 3600;
    private static final boolean TEST_WITH_SSL = true;

    @BeforeEach
    void setUp() {
        cacheConfig = new CacheConfig(schemaCacheBuilder, clusterSettingsCacheBuilder, redisCacheBuilder);

        // Use lenient() because not all tests use these stubbings
        lenient().when(appProperties.getRedisSearchHost()).thenReturn(TEST_REDIS_HOST);
        lenient().when(appProperties.getRedisSearchPort()).thenReturn(TEST_REDIS_PORT);
        lenient().when(appProperties.getRedisSearchPassword()).thenReturn(TEST_REDIS_PASSWORD);
        lenient().when(appProperties.getRedisSearchExpiration()).thenReturn(TEST_EXPIRATION);
        lenient().when(appProperties.getRedisSearchWithSsl()).thenReturn(TEST_WITH_SSL);
    }

    @Test
    void shouldInstantiateConfigSuccessfully() {
        assertNotNull(cacheConfig);
    }

    @Test
    void shouldCreateFieldTypeMappingCache() {
        // Act
        ISchemaCache result = cacheConfig.fieldTypeMappingCache(mockSchemaCache);

        // Assert
        assertNotNull(result);
        assertInstanceOf(SchemaCache.class, result);
    }

    @Test
    void shouldCreateSchemaGCCache() {
        // Arrange
        when(schemaCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                eq(String.class), eq(String.class)
        )).thenReturn(mockSchemaCache);

        // Act
        RedisCache<String, String> result = cacheConfig.schemaGCCache(appProperties);

        // Assert
        assertNotNull(result);
        assertEquals(mockSchemaCache, result);
    }

    @Test
    void schemaGCCacheShouldUseCorrectParameters() {
        // Arrange
        when(schemaCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                any(), any()
        )).thenReturn(mockSchemaCache);

        // Act
        cacheConfig.schemaGCCache(appProperties);

        // Assert
        verify(schemaCacheBuilder).buildRedisCache(
                TEST_REDIS_HOST,
                Integer.parseInt(TEST_REDIS_PORT),
                TEST_REDIS_PASSWORD,
                TEST_EXPIRATION,
                TEST_WITH_SSL,
                String.class,
                String.class
        );
    }

    @Test
    void shouldCreateElasticCredentialsCache() {
        // Act
        IElasticCredentialsCache<String, ClusterSettings> result =
                cacheConfig.elasticCredentialsCache(mockElasticCache);

        // Assert
        assertNotNull(result);
        assertInstanceOf(ElasticCredentialsCache.class, result);
    }

    @Test
    void shouldCreateElasticCache() {
        // Arrange
        when(clusterSettingsCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                eq(String.class), eq(ClusterSettings.class)
        )).thenReturn(mockElasticCache);

        // Act
        RedisCache<String, ClusterSettings> result = cacheConfig.elasticCache(appProperties);

        // Assert
        assertNotNull(result);
        assertEquals(mockElasticCache, result);
    }

    @Test
    void elasticCacheShouldUseCorrectParameters() {
        // Arrange
        when(clusterSettingsCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                any(), any()
        )).thenReturn(mockElasticCache);

        // Act
        cacheConfig.elasticCache(appProperties);

        // Assert
        verify(clusterSettingsCacheBuilder).buildRedisCache(
                TEST_REDIS_HOST,
                Integer.parseInt(TEST_REDIS_PORT),
                TEST_REDIS_PASSWORD,
                TEST_EXPIRATION,
                TEST_WITH_SSL,
                String.class,
                ClusterSettings.class
        );
    }

    @Test
    void shouldCreateCursorCache() {
        // Act
        IIndexCache result = cacheConfig.cursorCache(mockRedisCache);

        // Assert
        assertNotNull(result);
        assertInstanceOf(IndexCache.class, result);
    }

    @Test
    void shouldCreateRedisCache() {
        // Arrange
        when(redisCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                eq(String.class), eq(Boolean.class)
        )).thenReturn(mockRedisCache);

        // Act
        RedisCache<String, Boolean> result = cacheConfig.redisCache(appProperties);

        // Assert
        assertNotNull(result);
        assertEquals(mockRedisCache, result);
    }

    @Test
    void redisCacheShouldUseCorrectParameters() {
        // Arrange
        when(redisCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                any(), any()
        )).thenReturn(mockRedisCache);

        // Act
        cacheConfig.redisCache(appProperties);

        // Assert
        verify(redisCacheBuilder).buildRedisCache(
                TEST_REDIS_HOST,
                Integer.parseInt(TEST_REDIS_PORT),
                TEST_REDIS_PASSWORD,
                TEST_EXPIRATION,
                TEST_WITH_SSL,
                String.class,
                Boolean.class
        );
    }

    @Test
    void shouldCreateFunctionalAndIsolatedPartitionInfoCache() {
        // Act
        ICache<String, PartitionInfo> cache1 = cacheConfig.partitionInfoCache();
        ICache<String, PartitionInfo> cache2 = cacheConfig.partitionInfoCache();

        // Assert - correct type and distinct instances
        assertNotNull(cache1);
        assertNotNull(cache2);
        assertInstanceOf(VmCache.class, cache1);
        assertNotSame(cache1, cache2);

        // Assert - get on missing key returns null
        assertNull(cache1.get("non-existent-key"));

        // Assert - put and get round-trip returns the exact same object
        PartitionInfo partitionInfo = new PartitionInfo();
        cache1.put("partition-key-1", partitionInfo);
        assertSame(partitionInfo, cache1.get("partition-key-1"));

        // Assert - overwrite replaces the previous value
        PartitionInfo updated = new PartitionInfo();
        cache1.put("partition-key-1", updated);
        assertSame(updated, cache1.get("partition-key-1"));
        assertNotSame(partitionInfo, cache1.get("partition-key-1"));

        // Assert - delete removes the entry
        cache1.delete("partition-key-1");
        assertNull(cache1.get("partition-key-1"));

        // Assert - multiple keys are independent
        PartitionInfo p1 = new PartitionInfo();
        PartitionInfo p2 = new PartitionInfo();

        cache1.put("key-A", p1);
        cache1.put("key-B", p2);
        assertSame(p1, cache1.get("key-A"));
        assertSame(p2, cache1.get("key-B"));

        // Assert - deleting one key does not affect the other
        cache1.delete("key-A");
        assertNull(cache1.get("key-A"));
        assertSame(p2, cache1.get("key-B"));

        // Assert - state written to cache1 is not visible in cache2 (instance isolation)
        assertNull(cache2.get("key-B"));
    }

    @Test
    void partitionInfoCacheShouldBeVmCache() {
        // Act
        ICache<String, PartitionInfo> result = cacheConfig.partitionInfoCache();

        // Assert
        assertInstanceOf(VmCache.class, result);
    }

    @Test
    void partitionInfoCacheShouldNotRequireParameters() {
        // Act
        ICache<String, PartitionInfo> result = cacheConfig.partitionInfoCache();

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof VmCache);
    }

    @Test
    void partitionInfoCacheShouldBeInstantiableMultipleTimes() {
        // Act
        ICache<String, PartitionInfo> cache1 = cacheConfig.partitionInfoCache();
        ICache<String, PartitionInfo> cache2 = cacheConfig.partitionInfoCache();

        // Assert
        assertNotNull(cache1);
        assertNotNull(cache2);
        // Each call creates a new instance
        assertNotSame(cache1, cache2);
    }

    @Test
    void shouldReadAllPropertiesForSchemaCache() {
        // Arrange
        when(schemaCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                any(), any()
        )).thenReturn(mockSchemaCache);

        // Act
        cacheConfig.schemaGCCache(appProperties);

        // Assert
        verify(appProperties).getRedisSearchHost();
        verify(appProperties).getRedisSearchPort();
        verify(appProperties).getRedisSearchPassword();
        verify(appProperties).getRedisSearchExpiration();
        verify(appProperties).getRedisSearchWithSsl();
    }

    @Test
    void shouldReadAllPropertiesForElasticCache() {
        // Arrange
        when(clusterSettingsCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                any(), any()
        )).thenReturn(mockElasticCache);

        // Act
        cacheConfig.elasticCache(appProperties);

        // Assert
        verify(appProperties).getRedisSearchHost();
        verify(appProperties).getRedisSearchPort();
        verify(appProperties).getRedisSearchPassword();
        verify(appProperties).getRedisSearchExpiration();
        verify(appProperties).getRedisSearchWithSsl();
    }

    @Test
    void shouldReadAllPropertiesForRedisCache() {
        // Arrange
        when(redisCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                any(), any()
        )).thenReturn(mockRedisCache);

        // Act
        cacheConfig.redisCache(appProperties);

        // Assert
        verify(appProperties).getRedisSearchHost();
        verify(appProperties).getRedisSearchPort();
        verify(appProperties).getRedisSearchPassword();
        verify(appProperties).getRedisSearchExpiration();
        verify(appProperties).getRedisSearchWithSsl();
    }

    @Test
    void shouldParseRedisPortCorrectly() {
        // Arrange
        when(appProperties.getRedisSearchPort()).thenReturn("9999");
        when(schemaCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                any(), any()
        )).thenReturn(mockSchemaCache);

        // Act
        cacheConfig.schemaGCCache(appProperties);

        // Assert
        verify(schemaCacheBuilder).buildRedisCache(
                anyString(),
                eq(9999),
                anyString(),
                anyInt(),
                anyBoolean(),
                any(),
                any()
        );
    }

    @Test
    void shouldHandleDifferentRedisHosts() {
        // Arrange
        String customHost = "custom-redis-host.example.com";
        when(appProperties.getRedisSearchHost()).thenReturn(customHost);
        when(schemaCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                any(), any()
        )).thenReturn(mockSchemaCache);

        // Act
        cacheConfig.schemaGCCache(appProperties);

        // Assert
        verify(schemaCacheBuilder).buildRedisCache(
                eq(customHost),
                anyInt(),
                anyString(),
                anyInt(),
                anyBoolean(),
                any(),
                any()
        );
    }

    @Test
    void shouldHandleDifferentExpirationValues() {
        // Arrange
        int customExpiration = 7200;
        when(appProperties.getRedisSearchExpiration()).thenReturn(customExpiration);
        when(clusterSettingsCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                any(), any()
        )).thenReturn(mockElasticCache);

        // Act
        cacheConfig.elasticCache(appProperties);

        // Assert
        verify(clusterSettingsCacheBuilder).buildRedisCache(
                anyString(),
                anyInt(),
                anyString(),
                eq(customExpiration),
                anyBoolean(),
                any(),
                any()
        );
    }

    @Test
    void shouldHandleSslDisabled() {
        // Arrange
        when(appProperties.getRedisSearchWithSsl()).thenReturn(false);
        when(redisCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                any(), any()
        )).thenReturn(mockRedisCache);

        // Act
        cacheConfig.redisCache(appProperties);

        // Assert
        verify(redisCacheBuilder).buildRedisCache(
                anyString(),
                anyInt(),
                anyString(),
                anyInt(),
                eq(false),
                any(),
                any()
        );
    }

    @Test
    void shouldHandleEmptyPassword() {
        // Arrange
        when(appProperties.getRedisSearchPassword()).thenReturn("");
        when(schemaCacheBuilder.buildRedisCache(
                anyString(), anyInt(), anyString(), anyInt(), anyBoolean(),
                any(), any()
        )).thenReturn(mockSchemaCache);

        // Act
        cacheConfig.schemaGCCache(appProperties);

        // Assert
        verify(schemaCacheBuilder).buildRedisCache(
                anyString(),
                anyInt(),
                eq(""),
                anyInt(),
                anyBoolean(),
                any(),
                any()
        );
    }

}
