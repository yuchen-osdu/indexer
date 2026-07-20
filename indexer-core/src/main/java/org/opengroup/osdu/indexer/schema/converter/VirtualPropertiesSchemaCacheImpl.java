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

package org.opengroup.osdu.indexer.schema.converter;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import org.opengroup.osdu.indexer.provider.interfaces.ISchemaCache;
import org.opengroup.osdu.indexer.schema.converter.interfaces.IVirtualPropertiesSchemaCache;
import org.opengroup.osdu.indexer.schema.converter.tags.VirtualProperties;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;

@Component
public class VirtualPropertiesSchemaCacheImpl implements IVirtualPropertiesSchemaCache {
    private static final String VIRTUAL_PROPERTIES_SCHEMA = "_virtual_properties";
    private final Gson gson = new Gson();

    @Inject
    private ISchemaCache schemaCache;

    @Override
    public void put(String s, VirtualProperties o) {
        if (Strings.isNullOrEmpty(s) || o == null)
            return;

        String key = getCacheKey(s);
        schemaCache.put(key, gson.toJson(o));
    }

    @Override
    public VirtualProperties get(String s) {
        if (Strings.isNullOrEmpty(s))
            return null;

        String key = getCacheKey(s);
        String schema = (String) schemaCache.get(key);
        if (!Strings.isNullOrEmpty(schema)) {
            VirtualProperties schemaObj = gson.fromJson(schema, VirtualProperties.class);
            return schemaObj;
        }

        return null;
    }

    @Override
    public void delete(String s) {
        if (Strings.isNullOrEmpty(s))
            return;

        String key = getCacheKey(s);
        String schema = (String) schemaCache.get(key);
        if (!Strings.isNullOrEmpty(schema)) {
            schemaCache.delete(key);
        }
    }

    @Override
    public void clearAll() {
        schemaCache.clearAll();
    }

    private String getCacheKey(String s) {
        return s + VIRTUAL_PROPERTIES_SCHEMA;
    }
}
