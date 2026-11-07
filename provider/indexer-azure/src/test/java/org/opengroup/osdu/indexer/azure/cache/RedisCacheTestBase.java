/*
 * Copyright © Microsoft Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.indexer.azure.cache;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opengroup.osdu.azure.cache.RedisAzureCache;
import org.opengroup.osdu.azure.di.RedisAzureConfiguration;
import org.opengroup.osdu.indexer.azure.di.RedisConfig;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Abstract base test class for Redis cache implementations.
 * Verifies that clearAll() uses pattern-based deletion instead of FLUSHDB,
 * ensuring Azure Managed Redis (AMR) compatibility.
 *
 * @param <T> The cache value type
 * @param <C> The cache implementation type
 */
public abstract class RedisCacheTestBase<T, C extends RedisAzureCache<T>> {

    @Mock
    protected RedisConfig redisConfig;

    protected C cache;

    /**
     * Create the cache instance under test.
     * This method should instantiate the specific cache type being tested.
     *
     * @param redisConfig The mocked RedisConfig
     * @return The cache instance
     */
    protected abstract C createCache(RedisConfig redisConfig);

    /**
     * Get the expected pattern that should be passed to deleteByPattern.
     *
     * @return The expected Redis key pattern (e.g., "*-indexcache-*")
     */
    protected abstract String getExpectedPattern();

    @Before
    public void setUp() {
        // Mock RedisConfig to return a minimal RedisAzureConfiguration
        RedisAzureConfiguration mockConfig = new RedisAzureConfiguration(
            0,      // database
            3600,   // ttl
            6379,   // port
            15,     // connectionTimeout
            5,      // commandTimeout
            null,   // principalId
            "localhost"  // hostname
        );
        when(redisConfig.createConfiguration(anyInt())).thenReturn(mockConfig);

        // Mock all TTL getters to return 3600 (lenient so unused mocks don't fail tests)
        lenient().when(redisConfig.getIndexRedisTtl()).thenReturn(3600);
        lenient().when(redisConfig.getSchemaTtl()).thenReturn(3600);

        cache = createCache(redisConfig);
    }

    /**
     * Verifies that clearAll() uses pattern-based deletion with the correct pattern.
     * This test uses a spy to intercept the deleteByPattern call without requiring
     * an actual Redis connection.
     */
    @Test
    public void clearAll_shouldUsePatternDeletion_notFlushDb() {
        C spyCache = spy(cache);
        
        // Mock deleteByPattern to avoid actual Redis calls (returns number of keys deleted)
        doReturn(0L).when(spyCache).deleteByPattern(anyString());
        
        // Execute clearAll
        spyCache.clearAll();
        
        // Verify pattern-based deletion was used with the correct pattern
        verify(spyCache).deleteByPattern(getExpectedPattern());
        
        // Ensure clearAll delegates correctly (no infinite recursion)
        verify(spyCache, times(1)).clearAll();
    }
}
