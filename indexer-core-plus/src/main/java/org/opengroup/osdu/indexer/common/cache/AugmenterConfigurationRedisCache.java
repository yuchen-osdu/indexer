/*
  Copyright 2026 EPAM Systems, Inc

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.opengroup.osdu.indexer.common.cache;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.indexer.cache.interfaces.IAugmenterConfigurationCache;
import org.opengroup.osdu.indexer.model.indexproperty.AugmenterConfiguration;

// Stored as Gson JSON: Jackson cannot round-trip AugmenterConfiguration (PropertyPathDeserializer
// reads only the flattened RelatedObjectsSpec.* / ValueExtraction.* keys).
@RequiredArgsConstructor
public class AugmenterConfigurationRedisCache implements IAugmenterConfigurationCache, AutoCloseable {

    private static final Gson GSON = new Gson();

    private final RedisCache<String, String> cache;

    @Override
    public void close() throws Exception {
        this.cache.close();
    }

    @Override
    public void put(String s, AugmenterConfiguration o) {
        this.cache.put(s, GSON.toJson(o));
    }

    @Override
    public AugmenterConfiguration get(String s) {
        String json = this.cache.get(s);
        return json == null ? null : GSON.fromJson(json, AugmenterConfiguration.class);
    }

    @Override
    public void delete(String s) {
        this.cache.delete(s);
    }

    @Override
    public void clearAll() {
        this.cache.clearAll();
    }
}
