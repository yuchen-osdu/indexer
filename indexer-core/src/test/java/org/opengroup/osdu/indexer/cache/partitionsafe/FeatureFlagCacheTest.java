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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.cache.partitionsafe.FeatureFlagCache;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.inject.Inject;

import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class FeatureFlagCacheTest {
    private static final String VALID_KEY = "Tenant1-indexer-decimation-enabled";
    private static final String INVALID_KEY = "Tenant2-indexer-decimation-enabled";

    @InjectMocks
    FeatureFlagCache cache;

    @Mock
    private IRequestInfo requestInfo;

    @Before
    public void setup() {
        when(requestInfo.getPartitionId()).thenReturn("data-partition-id");
        cache.put(VALID_KEY, true);
    }

    @Test
    public void getValidKey() {
        Assert.assertNotNull(cache.get(VALID_KEY));
        Assert.assertTrue(cache.get(VALID_KEY));
    }

    @Test
    public void getInvalidKey() {
        Assert.assertNull(cache.get(INVALID_KEY));;
    }
}
