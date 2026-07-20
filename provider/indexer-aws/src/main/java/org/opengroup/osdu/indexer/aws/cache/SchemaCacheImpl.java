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

import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.indexer.provider.interfaces.ISchemaCache;

public class SchemaCacheImpl implements ISchemaCache<String, String>, AutoCloseable {
    private final ICache<String, String> cache;

    public SchemaCacheImpl(ICache<String, String> cache) {
        this.cache = cache;
    }

    @Override
    public void close() throws Exception {
        if (this.cache instanceof AutoCloseable autoCloseable) {
            autoCloseable.close();
        }
    }

    @Override
    public void put(String s, String o) {
        this.cache.put(s, o);
    }

    @Override
    public String get(String s) {
        return this.cache.get(s);
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
