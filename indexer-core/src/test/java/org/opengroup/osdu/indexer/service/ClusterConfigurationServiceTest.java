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

package org.opengroup.osdu.indexer.service;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.ElasticsearchClusterClient;
import co.elastic.clients.elasticsearch.cluster.PutClusterSettingsRequest;
import co.elastic.clients.elasticsearch.cluster.PutClusterSettingsResponse;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;

@RunWith(MockitoJUnitRunner.class)
public class ClusterConfigurationServiceTest {

    @Mock
    private RequestScopedElasticsearchClient requestScopedClient;
    @InjectMocks
    private ClusterConfigurationServiceImpl sut;

    private ElasticsearchClient restHighLevelClient;
    private ElasticsearchClusterClient clusterClient;

    @Before
    public void setup() {
        initMocks(this);
        clusterClient = mock(ElasticsearchClusterClient.class);
        restHighLevelClient = mock(ElasticsearchClient.class);
    }

    @Test
    public void should_updateClusterConfiguration() throws IOException {
        PutClusterSettingsResponse clusterUpdateSettingsResponse = mock(PutClusterSettingsResponse.class);
        when(requestScopedClient.getClient()).thenReturn(restHighLevelClient);
        when(clusterUpdateSettingsResponse.acknowledged()).thenReturn(true);
        doReturn(clusterClient).when(restHighLevelClient).cluster();
        doReturn(clusterUpdateSettingsResponse).when(clusterClient).putSettings(any(PutClusterSettingsRequest.class));

        boolean result = this.sut.updateClusterConfiguration();

        assertTrue(result);
    }
}
