/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.opengroup.osdu.indexer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.search.ClusterSettings;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticCredentialsCache;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticRepository;
import org.opengroup.osdu.core.common.multitenancy.ITenantInfoService;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticSettingServiceTest {

    @Mock
    private ITenantInfoService tenantInfoService;
    @Mock
    private IElasticRepository elasticRepository;
    @Mock
    private IElasticCredentialsCache elasticCredentialCache;
    @Mock
    private IndexerConfigurationProperties configurationProperties;
    @Mock
    private TenantInfo tenantInfo;
    @InjectMocks
    private ElasticSettingServiceImpl sut;
    @Mock
    private ClusterSettings clusterSettings;
    @Mock
    private DpsHeaders headersInfo;
    @Mock
    private JaxRsDpsLog log;

    public String GAE_SERVICE = "indexer";

    private final String host = "db5c51c1.us-central1.gcp.cloud.es.io";
    private final int port = 9243;
    private final String credentials = "name:password";

    String cacheKey = "";

    @BeforeEach
    void setup() {
        when(tenantInfo.getName()).thenReturn("tenant1");
        lenient().when(this.tenantInfoService.getTenantInfo()).thenReturn(tenantInfo);
        lenient().when(this.headersInfo.getPartitionId()).thenReturn("tenant1");
        lenient().when(configurationProperties.getGaeService()).thenReturn("indexer");
        clusterSettings = ClusterSettings.builder().host(host).port(port).userNameAndPassword(credentials).build();
        cacheKey = String.format("%s-%s", GAE_SERVICE, tenantInfo.getName());
    }

    @Test
    void should_getValid_clusterSettings_fromCache() {
        when(this.elasticCredentialCache.get(cacheKey)).thenReturn(clusterSettings);

        ClusterSettings response = this.sut.getElasticClusterInformation();
        assertNotNull(response);
        assertEquals(response.getHost(), host);
        assertEquals(response.getPort(), port);
        assertEquals(response.getUserNameAndPassword(), credentials);
    }

    @Test
    void should_getValid_clusterSettings_fromCosmosDB() {
        // No cache hit — falls through to repository
        when(this.elasticRepository.getElasticClusterSettings(tenantInfo)).thenReturn(clusterSettings);

        ClusterSettings response = this.sut.getElasticClusterInformation();
        assertNotNull(response);
        assertEquals(response.getHost(), host);
        assertEquals(response.getPort(), port);
        assertEquals(response.getUserNameAndPassword(), credentials);
    }

    @Test
    void should_throwAppException_when_tenantClusterInfo_not_found() {
        when(this.elasticRepository.getElasticClusterSettings(tenantInfo)).thenReturn(null);

        assertThrows(AppException.class, () -> this.sut.getElasticClusterInformation());
    }
}
