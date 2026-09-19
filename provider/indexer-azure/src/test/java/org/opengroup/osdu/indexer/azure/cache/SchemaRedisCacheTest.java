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

import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.indexer.azure.di.RedisConfig;

/**
 * Unit tests for SchemaRedisCache to verify AMR-safe clearAll() behavior.
 *
 * <p>These tests ensure that clearAll() delegates to deleteByPattern with the
 * cache-specific pattern instead of falling back to FLUSHDB, which would clear
 * the entire Redis database on Azure Managed Redis (AMR).
 */
@RunWith(MockitoJUnitRunner.class)
public class SchemaRedisCacheTest extends RedisCacheTestBase<String, SchemaRedisCache> {

    @Override
    protected SchemaRedisCache createCache(RedisConfig redisConfig) {
        return new SchemaRedisCache(redisConfig);
    }

    @Override
    protected String getExpectedPattern() {
        return "*-schemacache-*";
    }
}
