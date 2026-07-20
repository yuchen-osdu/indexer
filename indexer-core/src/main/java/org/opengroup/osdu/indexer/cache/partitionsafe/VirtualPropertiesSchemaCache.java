/*
 * Copyright 2017-2025, The Open Group
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

package org.opengroup.osdu.indexer.cache.partitionsafe;

import org.opengroup.osdu.indexer.schema.converter.interfaces.IVirtualPropertiesSchemaCache;
import org.opengroup.osdu.indexer.schema.converter.tags.VirtualProperties;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;

@Component
public class VirtualPropertiesSchemaCache extends AbstractPartitionSafeCache<String, VirtualProperties> {
    @Inject
    private IVirtualPropertiesSchemaCache virtualPropertiesSchemaCache;

    @Override
    public void put(String s, VirtualProperties o) {
        this.virtualPropertiesSchemaCache.put(cacheKey(s), o);
    }

    @Override
    public VirtualProperties get(String s) {
        return this.virtualPropertiesSchemaCache.get(cacheKey(s));
    }

    @Override
    public void delete(String s) {
        this.virtualPropertiesSchemaCache.delete(cacheKey(s));
    }

    @Override
    public void clearAll() {
        this.virtualPropertiesSchemaCache.clearAll();
    }
}
