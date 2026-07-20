/**
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.indexer.aws.cache;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.cache.CacheFactory;
import org.opengroup.osdu.core.aws.cache.CacheParameters;
import org.opengroup.osdu.core.aws.cache.NameSpacedCache;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.indexer.aws.di.AWSCacheConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CacheBuilderTest {

    @Mock
    AWSCacheConfiguration config;
    @Mock
    ICache cacheMock;
    @Captor
    ArgumentCaptor<CacheParameters<?, ?>> cacheParameterCaptor;
    private final String s = "s";
    private final String so = "o";
    private final boolean o = true;
    private final int port = 1234;
    private final String defaultEndpoint = "some-endpoint";
    private final String defaultPassword = "some-password";
    private final int expireTimeSeconds = 4321;
    MockedConstruction<NameSpacedCache> nameSpacedCache;

    @Before
    public void setup() {
        nameSpacedCache = Mockito.mockConstruction(NameSpacedCache.class, (mock, context) -> {
            validateCacheParams((CacheParameters<String, Object>)context.arguments().get(0));
        });
        when(config.getCacheExpireTimeInSeconds()).thenReturn(expireTimeSeconds);
        when(config.getRedisSearchKey()).thenReturn(defaultPassword);
        when(config.getRedisSearchHost()).thenReturn(defaultEndpoint);
        when(config.getRedisSearchPort()).thenReturn(Integer.toString(port));
    }

    @After
    public void teardown() {
        nameSpacedCache.close();
    }

    private <V> void validateCacheParams(CacheParameters<String, V> cacheParameters) {
        assertEquals(defaultEndpoint, cacheParameters.getDefaultHost());
        assertEquals(port, Integer.parseInt(cacheParameters.getDefaultPort()));
        assertEquals(defaultPassword, cacheParameters.getDefaultPassword());
        assertEquals(expireTimeSeconds, cacheParameters.getExpTimeSeconds());
    }
    @Test
    public void shouldCreateIndexCache_with_ExpectedParameters() {
        CacheBuilder cacheBuilder = new CacheBuilder();
        IndexCacheImpl implCache = cacheBuilder.indexInitCache(config);
        ICache<String, Boolean> cache = (ICache<String, Boolean>) ReflectionTestUtils.getField(implCache, "cache");
        assertEquals(1, nameSpacedCache.constructed().size());
        assertEquals(nameSpacedCache.constructed().get(0), cache);
    }
    @Test
    public void shouldCreateSchemaCache_with_ExpectedParameters() {
        CacheBuilder cacheBuilder = new CacheBuilder();
        SchemaCacheImpl implCache = cacheBuilder.schemaInitCache(config);
        ICache<String, String> cache = (ICache<String, String>) ReflectionTestUtils.getField(implCache, "cache");
        assertEquals(1, nameSpacedCache.constructed().size());
        assertEquals(nameSpacedCache.constructed().get(0), cache);
    }
}
