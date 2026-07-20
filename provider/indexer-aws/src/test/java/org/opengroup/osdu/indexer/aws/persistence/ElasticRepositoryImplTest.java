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

package org.opengroup.osdu.indexer.aws.persistence;


import com.fasterxml.jackson.core.JsonProcessingException;
import org.opengroup.osdu.core.aws.v2.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.opengroup.osdu.core.aws.v2.ssm.K8sLocalParameterProvider;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

@RunWith(SpringJUnit4ClassRunner.class)
public class ElasticRepositoryImplTest {

    private final String s = "s";
    private final String o = "o";
    private final String username = "username";
    private final String password = "password";
    private final String host = "host";
    private final String port = "6369";


    @Test
    public void test_PostConstruct() throws K8sParameterNotFoundException, JsonProcessingException {
        K8sLocalParameterProvider provider = Mockito.mock(K8sLocalParameterProvider.class);
        TenantInfo tenantInfo = Mockito.mock(TenantInfo.class);
        when(provider.getParameterAsStringOrDefault("ELASTICSEARCH_HOST", null)).thenReturn(host);
        when(provider.getParameterAsStringOrDefault("ELASTICSEARCH_PORT", "0")).thenReturn("12345");
        ElasticRepositoryImpl elasticImpl = new ElasticRepositoryImpl(provider);
        elasticImpl.postConstruct();
        assertEquals(host, elasticImpl.getElasticClusterSettings(tenantInfo).getHost());
    }

    @Test
    public void getElasticClusterSettings_Null_User_Test() {
        
        try (MockedConstruction<K8sLocalParameterProvider> provider = Mockito.mockConstruction(K8sLocalParameterProvider.class, (mockProvider, context) -> {
                                                                                                                when(mockProvider.getLocalMode()).thenReturn(false);
                                                                                                                when(mockProvider.getParameterAsStringOrDefault(eq("ELASTICSEARCH_HOST"), any())).thenReturn(host);
                                                                                                                when(mockProvider.getParameterAsStringOrDefault(eq("ELASTICSEARCH_PORT"), any())).thenReturn(port);
                                                                                                                when(mockProvider.getCredentialsAsMap(eq("ELASTICSEARCH_CREDENTIALS"))).thenReturn(null);
                                                                                                            })) {                        
            ElasticRepositoryImpl elasticImpl = new ElasticRepositoryImpl();
                
            assertNotNull(elasticImpl.getElasticClusterSettings(new TenantInfo()));
            
        }

    }
    @Test
    public void getElasticClusterSettings_not_Null_User_Test() {

        Map<String, String> map = new HashMap<String, String>();
        map.put("username", username);
        map.put("password", password);
        
        try (MockedConstruction<K8sLocalParameterProvider> provider = Mockito.mockConstruction(K8sLocalParameterProvider.class, (mockProvider, context) -> {
                                                                                                                when(mockProvider.getLocalMode()).thenReturn(false);
                                                                                                                when(mockProvider.getParameterAsStringOrDefault(eq("ELASTICSEARCH_HOST"), any())).thenReturn(host);
                                                                                                                when(mockProvider.getParameterAsStringOrDefault(eq("ELASTICSEARCH_PORT"), any())).thenReturn(port);
                                                                                                                when(mockProvider.getCredentialsAsMap(eq("ELASTICSEARCH_CREDENTIALS"))).thenReturn(map);
                                                                                                            })) {                        
            ElasticRepositoryImpl elasticImpl = new ElasticRepositoryImpl();
            elasticImpl.isHttps = true;
            assertNotNull(elasticImpl.getElasticClusterSettings(new TenantInfo()));
            
        }

    }
}
