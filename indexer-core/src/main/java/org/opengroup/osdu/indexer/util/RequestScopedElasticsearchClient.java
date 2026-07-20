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

package org.opengroup.osdu.indexer.util;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.Getter;
import lombok.extern.java.Log;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.io.IOException;

/**
 * A request-scoped bean that manages the lifecycle of an Elasticsearch client.
 * Each HTTP request will get its own instance of this bean, and the client will be
 * automatically closed when the request completes.
 */
@Getter
@Component
@RequestScope
@Log
public class RequestScopedElasticsearchClient implements DisposableBean {

    private final ElasticsearchClient client;

    @Autowired
    public RequestScopedElasticsearchClient(ElasticClientHandler elasticClientHandler) {
        log.fine("Creating new request-scoped Elasticsearch client");
        this.client = elasticClientHandler.createRestClientFromClusterInfo();
    }

    /**
     * Close the client when the bean is destroyed (at the end of the request).
     */
    @Override
    public void destroy() throws Exception {
        if (client != null) {
            log.fine("Closing request-scoped Elasticsearch client");
            try {
                // Access the transport and close it
                client._transport().close();
            } catch (IOException e) {
                log.warning("Error closing Elasticsearch client: " + e.getMessage());
            }
        }
    }
}
