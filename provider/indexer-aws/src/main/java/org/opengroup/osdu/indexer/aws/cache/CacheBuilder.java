/**
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opengroup.osdu.indexer.aws.cache;

import org.opengroup.osdu.core.aws.cache.CacheParameters;
import org.opengroup.osdu.core.aws.cache.NameSpacedCache;
import org.opengroup.osdu.indexer.aws.di.AWSCacheConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;


/*
The way we build the caches are a bit over-complicated, but this is required so the cache is properly
recognized by the `CloudConnectedOuterServicesBuilder` class and listed in the `/info` API.
For this, there should exist a bean with the `RedisCache` type. But of course, we can't build this bean
on local mode without a connection to the Elasticache server. That's why conditional bean injection is required.
 */
@Component
public class CacheBuilder {
    private static final int DEFAULT_MAX_SIZE = 10;

    private <V> NameSpacedCache<V> createCacheFromDefaultCacheParams(String namespace, Class<V> clazzOfV, AWSCacheConfiguration cacheConfiguration) {
        CacheParameters<String, V> cacheParams = CacheParameters.<String, V>builder()
                                                           .defaultHost(cacheConfiguration.getRedisSearchHost())
                                                           .defaultPassword(cacheConfiguration.getRedisSearchKey())
                                                           .defaultPort(cacheConfiguration.getRedisSearchPort())
                                                           .expTimeSeconds(cacheConfiguration.getCacheExpireTimeInSeconds())
                                                           .maxSize(DEFAULT_MAX_SIZE)
                                                           .keyNamespace(namespace)
                                                           .build()
                                                           .initFromLocalParameters(String.class, clazzOfV);
        return new NameSpacedCache<>(cacheParams);
    }
    static final String INDEX_CACHE_NAMESPACE = "indexerIndex";
    static final String SCHEMA_CACHE_NAMESPACE = "indexerSchema";
    @Bean
    public IndexCacheImpl indexInitCache(AWSCacheConfiguration awsAppServiceConfig) {
        NameSpacedCache<Boolean> awsIndexCache = this.createCacheFromDefaultCacheParams(INDEX_CACHE_NAMESPACE, Boolean.class, awsAppServiceConfig);
        return new IndexCacheImpl(awsIndexCache);
    }

    @Bean
    public SchemaCacheImpl schemaInitCache(AWSCacheConfiguration awsAppServiceConfig) {
        NameSpacedCache<String> awsSchemaCache = this.createCacheFromDefaultCacheParams(SCHEMA_CACHE_NAMESPACE, String.class, awsAppServiceConfig);
        return new SchemaCacheImpl(awsSchemaCache);
    }
}
