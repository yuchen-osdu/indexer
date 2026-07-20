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

package org.opengroup.osdu.indexer.cache.partitionsafe;

import org.opengroup.osdu.indexer.cache.interfaces.IRecordChangeInfoCache;
import org.opengroup.osdu.indexer.model.RecordChangeInfo;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;

@Component
public class RecordChangeInfoCache extends AbstractPartitionSafeCache<String, RecordChangeInfo> {
    @Inject
    private IRecordChangeInfoCache cache;

    @Override
    public void put(String s, RecordChangeInfo o) {
        this.cache.put(cacheKey(s), o);
    }

    @Override
    public RecordChangeInfo get(String s) {
        return this.cache.get(cacheKey(s));
    }

    @Override
    public void delete(String s) {
        this.cache.delete(cacheKey(s));
    }

    @Override
    public void clearAll() {
        this.cache.clearAll();
    }
}
