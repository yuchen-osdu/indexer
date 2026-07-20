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

package org.opengroup.osdu.indexer.service.mock;

import org.opengroup.osdu.indexer.schema.converter.interfaces.IVirtualPropertiesSchemaCache;
import org.opengroup.osdu.indexer.schema.converter.tags.VirtualProperties;

import java.util.HashMap;
import java.util.Map;

public class VirtualPropertiesSchemaCacheMock implements IVirtualPropertiesSchemaCache {
    private Map<String, VirtualProperties> cache = new HashMap<>();

    @Override
    public void put(String s, VirtualProperties o) {
        cache.put(s, o);
    }

    @Override
    public VirtualProperties get(String s) {
        if (cache.containsKey(s))
            return cache.get(s);
        return null;
    }

    @Override
    public void delete(String s) {
        cache.remove(s);
    }

    @Override
    public void clearAll() {
        cache.clear();
    }
}
