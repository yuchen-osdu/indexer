// Copyright 2017-2019, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.indexer.azure.cache;

import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.provider.interfaces.IAttributesCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AttributesCache implements IAttributesCache<String,Set> {

    private VmCache<String, Set> cache;

    public AttributesCache(@Value("${INDEX_CACHE_EXPIRATION}") final String INDEX_CACHE_EXPIRATION,
                           @Value("${MAX_CACHE_VALUE_SIZE}") final String MAX_CACHE_VALUE_SIZE) {
        cache = new VmCache<>(Integer.parseInt(INDEX_CACHE_EXPIRATION) * 60,
                Integer.parseInt(MAX_CACHE_VALUE_SIZE));
    }

    @Override
    public void put(String key, Set value) {
        this.cache.put(key, value);
    }

    @Override
    public Set get(String key) {
        return this.cache.get(key);
    }

    @Override
    public void delete(String key) {
        this.cache.delete(key);
    }

    @Override
    public void clearAll() {
        this.cache.clearAll();
    }

}