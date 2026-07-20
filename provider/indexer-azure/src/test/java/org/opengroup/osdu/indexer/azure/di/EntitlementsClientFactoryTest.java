/*
 * Copyright 2017-2025, Microsoft
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

package org.opengroup.osdu.indexer.azure.di;

import org.junit.jupiter.api.BeforeEach;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.http.json.HttpResponseBodyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

@RunWith(MockitoJUnitRunner.class)
public class EntitlementsClientFactoryTest {

    private static String authorizeApi = "authorizeApi";
    private static String authorizeApiKey = "authorizeApiKey";

    @Mock
    private HttpResponseBodyMapper mapper;

    @InjectMocks
    public EntitlementsClientFactory sut;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(EntitlementsClientFactoryTest.this);
        ReflectionTestUtils.setField(sut, authorizeApi, authorizeApi);
        ReflectionTestUtils.setField(sut, authorizeApiKey, authorizeApiKey);
    }

    @Test
    public void shouldReturn_notNull_EntitlementFactory_when_createInstance_isCalled() throws Exception{
        IEntitlementsFactory entitlementFactory = sut.createInstance();
        assertNotNull(entitlementFactory);
    }

    @Test
    public void shouldReturn_IEntitlementFactoryClass_when_getObjectType_isCalled() {
    Class<?> responseClass = sut.getObjectType();
    assertEquals(responseClass, IEntitlementsFactory.class);
    }
}
