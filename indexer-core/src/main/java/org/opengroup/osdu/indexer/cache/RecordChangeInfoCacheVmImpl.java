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

package org.opengroup.osdu.indexer.cache;

import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.indexer.cache.interfaces.IRecordChangeInfoCache;
import org.opengroup.osdu.indexer.model.Constants;
import org.opengroup.osdu.indexer.model.RecordChangeInfo;
import org.springframework.stereotype.Component;

@Component
public class RecordChangeInfoCacheVmImpl implements IRecordChangeInfoCache {
    private VmCache<String, RecordChangeInfo> cache;

    public RecordChangeInfoCacheVmImpl() {
        cache = new VmCache<>(Constants.DATA_CHANGE_INFO_CACHE_EXPIRATION, Constants.DATA_MAX_CACHE_SIZE);
    }

    @Override
    public void put(String s, RecordChangeInfo o) {
        this.cache.put(s, o);
    }

    @Override
    public RecordChangeInfo get(String s) {
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
