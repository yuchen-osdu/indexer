/*
 * Copyright © Schlumberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.indexer.azure.cache;

import org.opengroup.osdu.azure.cache.RedisAzureCache;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.indexer.azure.di.RedisConfig;
import org.opengroup.osdu.indexer.cache.interfaces.IRelatedObjectCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(value = "runtime.env.local", havingValue = "false", matchIfMissing = true)
public class RelatedObjectRedisCache  extends RedisAzureCache<RecordData> implements IRelatedObjectCache {
    public RelatedObjectRedisCache(final RedisConfig redisConfig) {
        super(RecordData.class, redisConfig.createConfiguration(redisConfig.getRecordsTtl()));
    }
}
