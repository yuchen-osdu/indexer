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

import io.lettuce.core.RedisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.model.search.ClusterSettings;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticCredentialsCache;

@Slf4j
@RequiredArgsConstructor
public class ElasticCredentialsCache implements IElasticCredentialsCache<String, ClusterSettings>, AutoCloseable {

    private final RedisCache<String, ClusterSettings> cache;

    @Override
    public void put(String key, ClusterSettings value) {
        this.cache.put(key, value);
    }

    @Override
    public ClusterSettings get(String key) {
        try {
            return this.cache.get(key);
        } catch (RedisException ex) {
            //In case the format of cache changes then clean the cache
            log.error("Unable to get value from Redis, trying to clean up by key.", ex);
            this.cache.delete(key);
            return null;
        }
    }

    @Override
    public void delete(String s) {
        this.cache.delete(s);
    }

    @Override
    public void clearAll() {
        this.cache.clearAll();
    }

    @Override
    public void close() {
        this.cache.close();
    }
}
