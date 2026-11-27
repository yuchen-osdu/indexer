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

import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.model.search.ClusterSettings;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticCredentialsCache;
import org.opengroup.osdu.core.common.provider.interfaces.IIndexCache;
import org.opengroup.osdu.core.cache.RedisCacheBuilder;
import org.opengroup.osdu.indexer.cache.interfaces.IAugmenterConfigurationCache;
import org.opengroup.osdu.indexer.cache.interfaces.IAugmenterConfigurationEnabledCache;
import org.opengroup.osdu.indexer.cache.interfaces.IChildrenKindsCache;
import org.opengroup.osdu.indexer.cache.interfaces.IKindCache;
import org.opengroup.osdu.indexer.cache.interfaces.IParentChildRelationshipSpecsCache;
import org.opengroup.osdu.indexer.cache.interfaces.IRecordChangeInfoCache;
import org.opengroup.osdu.indexer.cache.interfaces.IRelatedObjectCache;
import org.opengroup.osdu.indexer.common.di.CorePlusConfigurationProperties;
import org.opengroup.osdu.indexer.model.Constants;
import org.opengroup.osdu.indexer.provider.interfaces.ISchemaCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private final RedisCacheBuilder<String, String> schemaCacheBuilder;
    private final RedisCacheBuilder<String, ClusterSettings> clusterSettingsCacheBuilder;
    private final RedisCacheBuilder<String, Boolean> redisCacheBuilder;

    @Bean
    public ISchemaCache fieldTypeMappingCache(RedisCache<String, String> schemaCache) {
        return new SchemaCache(schemaCache);
    }

    @Bean
    public RedisCache<String, String> schemaGCCache(CorePlusConfigurationProperties appProperties) {
        return schemaCacheBuilder.buildRedisCache(
                appProperties.getRedisSearchHost(),
                Integer.parseInt(appProperties.getRedisSearchPort()),
                appProperties.getRedisSearchPassword(),
                appProperties.getRedisSearchExpiration(),
                appProperties.getRedisSearchWithSsl(),
                String.class,
                String.class
        );
    }

    @Bean
    public IElasticCredentialsCache<String, ClusterSettings> elasticCredentialsCache(RedisCache<String, ClusterSettings> elasticCache) {
        return new ElasticCredentialsCache(elasticCache);
    }

    @Bean
    public RedisCache<String, ClusterSettings> elasticCache(CorePlusConfigurationProperties appServiceConfig) {
        return clusterSettingsCacheBuilder.buildRedisCache(
                appServiceConfig.getRedisSearchHost(),
                Integer.parseInt(appServiceConfig.getRedisSearchPort()),
                appServiceConfig.getRedisSearchPassword(),
                appServiceConfig.getRedisSearchExpiration(),
                appServiceConfig.getRedisSearchWithSsl(),
                String.class,
                ClusterSettings.class
        );
    }

    @Bean
    public IIndexCache cursorCache(RedisCache<String, Boolean> redisCache) {
        return new IndexCache(redisCache);
    }

    @Bean
    public RedisCache<String, Boolean> redisCache(CorePlusConfigurationProperties appServiceConfig) {
        return redisCacheBuilder.buildRedisCache(
                appServiceConfig.getRedisSearchHost(),
                Integer.parseInt(appServiceConfig.getRedisSearchPort()),
                appServiceConfig.getRedisSearchPassword(),
                appServiceConfig.getRedisSearchExpiration(),
                appServiceConfig.getRedisSearchWithSsl(),
                String.class,
                Boolean.class
        );
    }

    @Bean
    @Primary
    public IAugmenterConfigurationCache augmenterConfigurationRedisCache(CorePlusConfigurationProperties appServiceConfig) {
        return new AugmenterConfigurationRedisCache(stringRedisCache(appServiceConfig, Constants.SPEC_CACHE_EXPIRATION));
    }

    @Bean
    @Primary
    public IAugmenterConfigurationEnabledCache augmenterConfigurationEnabledRedisCache(CorePlusConfigurationProperties appServiceConfig) {
        return new AugmenterConfigurationEnabledRedisCache(redisCacheBuilder.buildRedisCache(
                appServiceConfig.getRedisSearchHost(),
                Integer.parseInt(appServiceConfig.getRedisSearchPort()),
                appServiceConfig.getRedisSearchPassword(),
                Constants.SPEC_CACHE_EXPIRATION,
                appServiceConfig.getRedisSearchWithSsl(),
                String.class,
                Boolean.class
        ));
    }

    @Bean
    @Primary
    public IChildrenKindsCache childrenKindsRedisCache(CorePlusConfigurationProperties appServiceConfig) {
        return new ChildrenKindsRedisCache(stringRedisCache(appServiceConfig, Constants.SPEC_CACHE_EXPIRATION));
    }

    @Bean
    @Primary
    public IParentChildRelationshipSpecsCache parentChildRelationshipSpecsRedisCache(CorePlusConfigurationProperties appServiceConfig) {
        return new ParentChildRelationshipSpecsRedisCache(stringRedisCache(appServiceConfig, Constants.SPEC_CACHE_EXPIRATION));
    }

    @Bean
    @Primary
    public IKindCache kindRedisCache(CorePlusConfigurationProperties appServiceConfig) {
        return new KindRedisCache(stringRedisCache(appServiceConfig, Constants.SPEC_CACHE_EXPIRATION));
    }

    @Bean
    @Primary
    public IRelatedObjectCache relatedObjectRedisCache(CorePlusConfigurationProperties appServiceConfig) {
        return new RelatedObjectRedisCache(stringRedisCache(appServiceConfig, Constants.DATA_CACHE_EXPIRATION));
    }

    @Bean
    @Primary
    public IRecordChangeInfoCache recordChangeInfoRedisCache(CorePlusConfigurationProperties appServiceConfig) {
        return new RecordChangeInfoRedisCache(stringRedisCache(appServiceConfig, Constants.DATA_CHANGE_INFO_CACHE_EXPIRATION));
    }

    private RedisCache<String, String> stringRedisCache(CorePlusConfigurationProperties appServiceConfig, int expiration) {
        return schemaCacheBuilder.buildRedisCache(
                appServiceConfig.getRedisSearchHost(),
                Integer.parseInt(appServiceConfig.getRedisSearchPort()),
                appServiceConfig.getRedisSearchPassword(),
                expiration,
                appServiceConfig.getRedisSearchWithSsl(),
                String.class,
                String.class
        );
    }

    @Bean
    public ICache<String, PartitionInfo> partitionInfoCache() {
        return new VmCache<>(600, 2000);
    }
}
