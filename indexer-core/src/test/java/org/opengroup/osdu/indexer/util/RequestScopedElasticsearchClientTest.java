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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RequestScopedElasticsearchClientTest {

    @Mock
    private ElasticClientHandler elasticClientHandler;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ElasticsearchTransport elasticsearchTransport;

    private RequestScopedElasticsearchClient requestScopedClient;

    @Before
    public void setup() {
        when(elasticClientHandler.createRestClientFromClusterInfo()).thenReturn(elasticsearchClient);
        when(elasticsearchClient._transport()).thenReturn(elasticsearchTransport);
    }

    @Test
    public void constructor_should_create_client_from_handler() {
        // Act
        requestScopedClient = new RequestScopedElasticsearchClient(elasticClientHandler);

        // Assert
        verify(elasticClientHandler).createRestClientFromClusterInfo();
        assertEquals(elasticsearchClient, requestScopedClient.getClient());
    }

    @Test
    public void destroy_should_close_transport_when_client_exists() throws Exception {
        // Arrange
        requestScopedClient = new RequestScopedElasticsearchClient(elasticClientHandler);

        // Act
        requestScopedClient.destroy();

        // Assert
        verify(elasticsearchTransport).close();
    }

    @Test
    public void destroy_should_handle_io_exception_gracefully() throws Exception {
        // Arrange
        requestScopedClient = new RequestScopedElasticsearchClient(elasticClientHandler);
        doThrow(new IOException("Test exception")).when(elasticsearchTransport).close();

        // Act - should not throw exception
        requestScopedClient.destroy();

        // Assert
        verify(elasticsearchTransport).close();
    }

    @Test
    public void destroy_should_handle_null_client_gracefully() throws Exception {
        // Arrange - create a client with null ElasticsearchClient
        // First create with valid handler
        requestScopedClient = new RequestScopedElasticsearchClient(elasticClientHandler);
        
        // Then use reflection to set the client field to null
        java.lang.reflect.Field clientField = RequestScopedElasticsearchClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(requestScopedClient, null);

        // Act - should not throw exception
        requestScopedClient.destroy();

        // Assert - no interactions with mocks, just verifying no exception is thrown
    }

    @Test
    public void getClient_should_return_elasticsearch_client() {
        // Arrange
        requestScopedClient = new RequestScopedElasticsearchClient(elasticClientHandler);

        // Act
        ElasticsearchClient result = requestScopedClient.getClient();

        // Assert
        assertNotNull(result);
        assertEquals(elasticsearchClient, result);
    }
}
