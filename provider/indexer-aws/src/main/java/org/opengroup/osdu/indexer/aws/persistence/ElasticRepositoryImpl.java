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
import org.opengroup.osdu.core.common.model.search.ClusterSettings;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticRepository;
import org.opengroup.osdu.core.aws.v2.ssm.K8sLocalParameterProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Component
public class ElasticRepositoryImpl implements IElasticRepository {

    @Value("${aws.es.host}")
    String host;

    @Value("${aws.es.port}")
    int port;

    @Value("${aws.es.isHttps}")
    boolean isHttps;

    @Value("${aws.es.username}")
    String username;

    @Value("${aws.es.password}")
    String password;

    String usernameAndPassword;

    @Value("${aws.region}")
    private String amazonRegion;

    K8sLocalParameterProvider provider;

    public ElasticRepositoryImpl() {
        this(new K8sLocalParameterProvider());
    }

    public ElasticRepositoryImpl(K8sLocalParameterProvider provider) {
        this.provider = provider;
    }

    @PostConstruct
    public void postConstruct() throws K8sParameterNotFoundException, JsonProcessingException {
        host = provider.getParameterAsStringOrDefault("ELASTICSEARCH_HOST", host);
        port = Integer.parseInt(provider.getParameterAsStringOrDefault("ELASTICSEARCH_PORT", String.valueOf(port)));
        Map<String, String> val = provider.getCredentialsAsMap("ELASTICSEARCH_CREDENTIALS");
        if (val != null){
            username = val.get("username");
            password = val.get("password");
        }

        //elastic expects username:password format
        usernameAndPassword = String.format("%s:%s", username, password);
    }

    @Override
    public ClusterSettings getElasticClusterSettings(TenantInfo tenantInfo) {
        ClusterSettings settings = new ClusterSettings(host, port, usernameAndPassword);
        
        if (!isHttps) {
            settings.setHttps(false);
            settings.setTls(false);
        }

        return settings;
    }
}
