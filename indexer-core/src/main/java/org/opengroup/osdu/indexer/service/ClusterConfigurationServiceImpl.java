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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.cluster.PutClusterSettingsRequest;
import co.elastic.clients.elasticsearch.cluster.PutClusterSettingsResponse;
import co.elastic.clients.json.JsonData;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import org.opengroup.osdu.indexer.util.ElasticClientHandler;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ClusterConfigurationServiceImpl implements IClusterConfigurationService {

    @Autowired
    private ElasticClientHandler elasticClientHandler;
    
    @Autowired
    private RequestScopedElasticsearchClient requestScopedClient;

    @Override
    public boolean updateClusterConfiguration() throws IOException {
        PutClusterSettingsRequest request = new PutClusterSettingsRequest.Builder()
            .persistent(Map.of("action.auto_create_index", JsonData.from(new StringReader("\"false\""))))
            .timeout(Time.of(builder -> builder.time("1m")))
            .build();

        ElasticsearchClient client = this.requestScopedClient.getClient();
        PutClusterSettingsResponse response = client.cluster().putSettings(request);
        return response.acknowledged();
        }
    }
