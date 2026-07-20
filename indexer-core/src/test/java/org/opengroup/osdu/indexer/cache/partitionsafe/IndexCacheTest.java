// Copyright © Schlumberger
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

package org.opengroup.osdu.indexer.cache.partitionsafe;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.provider.interfaces.IIndexCache;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.cache.partitionsafe.IndexCache;
import org.springframework.test.context.junit4.SpringRunner;

import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
public class IndexCacheTest {

    @Mock
    private IIndexCache cache;
    @Mock
    private IRequestInfo requestInfo;
    @InjectMocks
    private IndexCache sut;

    @Test
    public void should_addopendesNamToKey_when_addingToCache() {
        when(this.requestInfo.getPartitionId()).thenReturn("opendes");

        this.sut.put("key", true);

        verify(this.cache, times(1)).put("opendes-indexcache-key", true);
    }

    @Test
    public void should_addopendesNamToKey_when_deletingFromCache() {
        when(this.requestInfo.getPartitionId()).thenReturn("opendes");

        this.sut.delete("key");

        verify(this.cache, times(1)).delete("opendes-indexcache-key");
    }

    @Test
    public void should_addopendesNamToKey_when_retrievingfromCache() {
        when(this.requestInfo.getPartitionId()).thenReturn("opendes");

        this.sut.get("key");

        verify(this.cache, times(1)).get("opendes-indexcache-key");
    }

    @Test
    public void should_callWrappedClearCache() {
        this.sut.clearAll();

        verify(this.cache, times(1)).clearAll();
    }
}
