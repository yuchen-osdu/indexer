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

package org.opengroup.osdu.indexer.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.partition.*;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.opengroup.osdu.indexer.cache.partitionsafe.FeatureFlagCache;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class BooleanFeatureFlagClientTest {
    private static final String PROPERTY_NAME =  "any-feature-enabled";

    @InjectMocks
    private BooleanFeatureFlagClient sut;

    @Mock
    private FeatureFlagCache cache;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private DpsHeaders headers;

    @Mock
    private IPartitionFactory factory;

    @Mock
    private IServiceAccountJwtClient tokenService;


    @Mock
    IPartitionProvider partitionProvider;

    @Before
    public void setup() {
        when(this.headers.getPartitionId()).thenReturn("dataPartitionId");
        when(this.headers.getHeaders()).thenReturn(new HashMap());
        when(this.factory.create(any())).thenReturn(partitionProvider);
        when(this.tokenService.getIdToken(anyString())).thenReturn("token");
    }

    @Test
    public void isEnabled_return_true() throws PartitionException {
        PartitionInfo partitionInfo = new PartitionInfo();
        Property property = new Property();
        property.setSensitive(false);
        property.setValue("true");
        partitionInfo.getProperties().put(PROPERTY_NAME, property);
        when(this.partitionProvider.get(anyString())).thenReturn(partitionInfo);
        when(this.cache.get(anyString())).thenReturn(null);

        // Default value won't take any effect
        boolean enabled = sut.isEnabled(PROPERTY_NAME, true);
        Assert.assertTrue(enabled);

        enabled = sut.isEnabled(PROPERTY_NAME, false);
        Assert.assertTrue(enabled);
    }

    @Test
    public void isEnabled_return_false_when_property_set_to_false() throws PartitionException {
        PartitionInfo partitionInfo = new PartitionInfo();
        Property property = new Property();
        property.setSensitive(false);
        property.setValue("false");
        partitionInfo.getProperties().put(PROPERTY_NAME, property);
        when(this.partitionProvider.get(anyString())).thenReturn(partitionInfo);
        when(this.cache.get(anyString())).thenReturn(null);

        // Default value won't take any effect
        boolean enabled = sut.isEnabled(PROPERTY_NAME, true);
        Assert.assertFalse(enabled);

        enabled = sut.isEnabled(PROPERTY_NAME, false);
        Assert.assertFalse(enabled);
    }

    @Test
    public void isEnabled_return_default_value_when_property_does_not_exist() throws PartitionException {
        // The feature flag is enabled by default
        PartitionInfo partitionInfo = new PartitionInfo();
        when(this.partitionProvider.get(anyString())).thenReturn(partitionInfo);
        when(this.cache.get(anyString())).thenReturn(null);
        boolean enabled = sut.isEnabled(PROPERTY_NAME, true);;
        Assert.assertTrue(enabled);

        enabled = sut.isEnabled(PROPERTY_NAME, false);;
        Assert.assertFalse(enabled);
    }

    @Test
    public void isEnabled_return_default_value_when_partitionProvider_throws_exception() throws PartitionException {
        // The feature flag is enabled by default
        when(this.partitionProvider.get(anyString())).thenThrow(PartitionException.class);
        when(this.cache.get(anyString())).thenReturn(null);
        boolean enabled = sut.isEnabled(PROPERTY_NAME, true);;
        Assert.assertTrue(enabled);

        enabled = sut.isEnabled(PROPERTY_NAME, false);;
        Assert.assertFalse(enabled);
    }

    @Test
    public void isEnabled_return_true_from_cache() throws PartitionException {
        when(this.cache.get(anyString())).thenReturn(true);
        boolean enabled = sut.isEnabled(PROPERTY_NAME, false);;
        Assert.assertTrue(enabled);
        verify(headers, Mockito.times(0)).getHeaders();
        verify(factory, Mockito.times(0)).create(any());
    }

    @Test
    public void isEnabled_return_false_from_cache() throws PartitionException {
        when(this.cache.get(anyString())).thenReturn(false);
        boolean enabled = sut.isEnabled(PROPERTY_NAME, false);;
        Assert.assertFalse(enabled);
        verify(headers, Mockito.times(0)).getHeaders();
        verify(factory, Mockito.times(0)).create(any());
    }

}
