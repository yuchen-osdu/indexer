/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.RedisCache;

@RunWith(MockitoJUnitRunner.class)
public abstract class BaseCacheImplTest<K, V> {
    @Mock
    private ICache<K, V> normalCache;
    @Mock
    private RedisCache<K, V> redisCache;
    abstract ICache<K, V> getCache(ICache<K, V> supplied);
    abstract AutoCloseable getCloseable(ICache<K, V> supplied);
    abstract K getKey();
    abstract V getVal();

    @Test
    public void should_autoCloseRedis_when_redisCacheIsUsed() throws Exception {
        AutoCloseable closeable = getCloseable(redisCache);
        closeable.close();
        verify(redisCache, times(1)).close();
    }

    @Test
    public void should_notError_when_nonRedisCacheIsUsed() throws Exception {
        AutoCloseable closeable = getCloseable(normalCache);
        assertDoesNotThrow(closeable::close);
    }

    @Test
    public void should_passThrough_get() {
        when(normalCache.get(getKey())).thenReturn(getVal());
        ICache<K, V> cache = getCache(normalCache);
        V val = cache.get(getKey());
        assertEquals(getVal(), val);
        verify(normalCache, times(1)).get(any());
        verify(normalCache, never()).put(any(),any());
        verify(normalCache, never()).delete(any());
        verify(normalCache, never()).clearAll();
    }

    @Test
    public void should_passThrough_put() {
        ICache<K, V> cache = getCache(normalCache);
        cache.put(getKey(), getVal());
        verify(normalCache, never()).get(any());
        verify(normalCache, times(1)).put(any(),any());
        verify(normalCache, times(1)).put(getKey(),getVal());
        verify(normalCache, never()).delete(any());
        verify(normalCache, never()).clearAll();
    }

    @Test
    public void should_passThrough_delete() {
        ICache<K, V> cache = getCache(normalCache);
        cache.delete(getKey());
        verify(normalCache, never()).get(any());
        verify(normalCache, never()).put(any(),any());
        verify(normalCache, times(1)).delete(any());
        verify(normalCache, times(1)).delete(getKey());
        verify(normalCache, never()).clearAll();
    }

    @Test
    public void should_passThrough_clearAll() {
        ICache<K, V> cache = getCache(normalCache);
        cache.clearAll();
        verify(normalCache, never()).get(any());
        verify(normalCache, never()).put(any(),any());
        verify(normalCache, never()).delete(any());
        verify(normalCache, times(1)).clearAll();
    }
}
