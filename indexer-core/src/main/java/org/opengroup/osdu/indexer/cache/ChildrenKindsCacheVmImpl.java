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
import org.opengroup.osdu.indexer.cache.interfaces.IChildrenKindsCache;
import org.opengroup.osdu.indexer.model.Constants;
import org.opengroup.osdu.indexer.model.indexproperty.ChildrenKinds;
import org.springframework.stereotype.Component;

@Component
public class ChildrenKindsCacheVmImpl implements IChildrenKindsCache {
    private VmCache<String, ChildrenKinds> cache;

    public ChildrenKindsCacheVmImpl() {
        cache = new VmCache<>(Constants.SPEC_CACHE_EXPIRATION, Constants.SPEC_MAX_CACHE_SIZE);
    }

    @Override
    public void put(String s, ChildrenKinds o) {
        this.cache.put(s, o);
    }

    @Override
    public ChildrenKinds get(String s) {
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
