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



package org.opengroup.osdu.indexer.aws.di;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.http.json.HttpResponseBodyMapper;
import static org.junit.jupiter.api.Assertions.*;

@RunWith(MockitoJUnitRunner.class)
public class EntitlementsClientFactoryTest {

    @InjectMocks
    private EntitlementsClientFactory factory;

    @Mock
    private HttpResponseBodyMapper mapper;

    @Test
    public void createInstance_shouldReturn_notNull_EntitlementFactory() throws Exception{
        IEntitlementsFactory entitlementFactory = factory.createInstance();
        assertNotNull(entitlementFactory);
    }

    @Test
    public void getObjectType_shouldReturn_IEntitlementFactoryClass() {
    Class<?> responseClass = factory.getObjectType();
    assertEquals(responseClass, IEntitlementsFactory.class);
    }
}
