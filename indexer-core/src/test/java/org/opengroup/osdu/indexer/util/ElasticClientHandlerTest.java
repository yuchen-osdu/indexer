// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.indexer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.indexer.IElasticSettingService;
import org.opengroup.osdu.core.common.model.search.ClusterSettings;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;

@RunWith(MockitoJUnitRunner.class)
public class ElasticClientHandlerTest {

    private static final boolean SECURITY_HTTPS_CERTIFICATE_TRUST = false;
    private static MockedStatic<RestClient> mockedRestClients;

    @Mock
    private IElasticSettingService elasticSettingService;
    @Mock
    private RestClientBuilder builder;
    @Mock
    private RestClient restClient;
    @Mock
    private TenantInfo tenantInfo;
    @Mock
    private HttpAsyncClientBuilder httpAsyncClientBuilder;

    @Spy
    @InjectMocks
    private ElasticClientHandler elasticClientHandler;

    @Before
    public void setup() {
        mockedRestClients = mockStatic(RestClient.class);

        elasticClientHandler.setSecurityHttpsCertificateTrust(SECURITY_HTTPS_CERTIFICATE_TRUST);

        // Set up default behavior for mocks
        when(httpAsyncClientBuilder.setSSLContext(any(SSLContext.class))).thenReturn(httpAsyncClientBuilder);
        when(httpAsyncClientBuilder.setSSLHostnameVerifier(any())).thenReturn(httpAsyncClientBuilder);
        when(httpAsyncClientBuilder.setMaxConnTotal(anyInt())).thenReturn(httpAsyncClientBuilder);
        when(httpAsyncClientBuilder.setMaxConnPerRoute(anyInt())).thenReturn(httpAsyncClientBuilder);

        // Set default values for configurable connection pool limits
        elasticClientHandler.setMaxConnTotal(50);
        elasticClientHandler.setMaxConnPerRoute(20);
    }

    @After
    public void close() {
        mockedRestClients.close();
    }

    @Test
    public void createRestClient_when_deployment_env_is_saas() {
        ClusterSettings clusterSettings = new ClusterSettings("H", 1, "U:P");
        when(RestClient.builder(new HttpHost("H", 1, "https"))).thenAnswer(invocation -> builder);
        when(builder.build()).thenReturn(restClient);

        this.elasticClientHandler.createRestClient(clusterSettings);

        // Verify the builder was configured correctly
        verify(builder).setRequestConfigCallback(any());
        verify(builder).setHttpClientConfigCallback(any());
        verify(builder).setDefaultHeaders(any());
    }

    @Test(expected = AppException.class)
    public void failed_createRestClientForSaaS_when_getcluster_info_throws_exception() {
        // Arrange
        when(tenantInfo.getDataPartitionId()).thenReturn("test-partition");
        when(elasticSettingService.getElasticClusterInformation()).thenThrow(new AppException(1, "", ""));

        // Act - this should throw the AppException
        this.elasticClientHandler.createRestClientFromClusterInfo();
    }

    @Test
    public void getOrCreateRestClient_should_create_new_client() {
        // Arrange
        String partitionId = "test-partition";
        when(tenantInfo.getDataPartitionId()).thenReturn(partitionId);

        // Mock the creation of a new client
        ClusterSettings clusterSettings = new ClusterSettings("H", 1, "U:P");
        when(elasticSettingService.getElasticClusterInformation()).thenReturn(clusterSettings);
        when(RestClient.builder(new HttpHost("H", 1, "https"))).thenAnswer(invocation -> builder);
        when(builder.build()).thenReturn(restClient);

        // Act
        ElasticsearchClient client = elasticClientHandler.createRestClientFromClusterInfo();

        // Assert
        assertNotNull("Should create a new client", client);
    }

    @Test
    public void createClientBuilder_should_configure_connection_pool_limits() {
        // Arrange
        String host = "test-host";
        String authHeader = "Basic test";
        int port = 9243;
        String scheme = "https";
        String tls = "true";

        when(RestClient.builder(any(HttpHost.class))).thenReturn(builder);
        when(builder.setRequestConfigCallback(any())).thenReturn(builder);
        when(builder.setHttpClientConfigCallback(any())).thenReturn(builder);

        // Act
        elasticClientHandler.createClientBuilder(host, authHeader, port, scheme, tls);

        // Assert
        verify(builder, times(1)).setHttpClientConfigCallback(any());
        verify(builder).setRequestConfigCallback(any());
        verify(builder).setDefaultHeaders(any());
    }

    @Test
    public void should_use_configurable_connection_pool_limits() {
        // Arrange
        int customMaxConnTotal = 100;
        int customMaxConnPerRoute = 40;
        elasticClientHandler.setMaxConnTotal(customMaxConnTotal);
        elasticClientHandler.setMaxConnPerRoute(customMaxConnPerRoute);

        // Act & Assert
        assertEquals(customMaxConnTotal, elasticClientHandler.getMaxConnTotal());
        assertEquals(customMaxConnPerRoute, elasticClientHandler.getMaxConnPerRoute());
    }

    @Test
    public void createRestClient_should_use_http_when_https_disabled() {
        // Arrange
        ClusterSettings clusterSettings = new ClusterSettings("test-host", 9200, "user:pass");
        clusterSettings.setHttps(false);

        when(RestClient.builder(any(HttpHost.class))).thenAnswer(invocation -> builder);
        when(builder.build()).thenReturn(restClient);

        // Act
        ElasticsearchClient client = elasticClientHandler.createRestClient(clusterSettings);

        // Assert
        assertNotNull(client);
        // Verify that the HttpHost was created with "http" scheme
        mockedRestClients.verify(() -> RestClient.builder(any(HttpHost.class)));

        // Verify the HttpHost was created with correct parameters
        ArgumentCaptor<HttpHost> hostCaptor = ArgumentCaptor.forClass(HttpHost.class);
        mockedRestClients.verify(() -> RestClient.builder(hostCaptor.capture()));
        HttpHost capturedHost = hostCaptor.getValue();
        assertEquals("http", capturedHost.getSchemeName());
        assertEquals("test-host", capturedHost.getHostName());
        assertEquals(9200, capturedHost.getPort());
    }

    @Test
    public void createRestClient_should_use_non_tls_when_tls_disabled() {
        // Arrange
        ClusterSettings clusterSettings = new ClusterSettings("test-host", 9200, "user:pass");
        clusterSettings.setTls(false);

        when(RestClient.builder(any(HttpHost.class))).thenAnswer(invocation -> builder);
        when(builder.build()).thenReturn(restClient);

        // Act
        elasticClientHandler.createRestClient(clusterSettings);

        // Assert
        ArgumentCaptor<Header[]> headersCaptor = ArgumentCaptor.forClass(Header[].class);
        verify(builder).setDefaultHeaders(headersCaptor.capture());

        // Check that the TLS header is set to "false"
        Header[] headers = headersCaptor.getValue();
        boolean foundTlsHeader = false;
        for (Header header : headers) {
            if ("xpack.security.transport.ssl.enabled".equals(header.getName())) {
                assertEquals("false", header.getValue());
                foundTlsHeader = true;
                break;
            }
        }
        assertTrue("Should have TLS header set to false", foundTlsHeader);
    }

    @Test
    public void createClientBuilder_should_configure_ssl_context_when_certificate_trust_enabled() {
        // Arrange
        String host = "test-host";
        String authHeader = "Basic test";
        int port = 9243;
        String scheme = "https";
        String tls = "true";

        // Enable certificate trust
        elasticClientHandler.setSecurityHttpsCertificateTrust(true);

        when(RestClient.builder(any(HttpHost.class))).thenReturn(builder);
        when(builder.setRequestConfigCallback(any())).thenReturn(builder);

        // Capture the HttpClientConfigCallback to manually invoke it
        ArgumentCaptor<HttpClientConfigCallback> callbackCaptor =
            ArgumentCaptor.forClass(HttpClientConfigCallback.class);
        when(builder.setHttpClientConfigCallback(callbackCaptor.capture())).thenReturn(builder);
        when(builder.setDefaultHeaders(any())).thenReturn(builder);

        // Act
        RestClientBuilder result = elasticClientHandler.createClientBuilder(host, authHeader, port, scheme, tls);

        // Manually invoke the callback to trigger SSL context creation
        callbackCaptor.getValue().customizeHttpClient(httpAsyncClientBuilder);

        // Assert
        assertNotNull(result);
        // Verify that SSL context is set on the HttpAsyncClientBuilder
        verify(httpAsyncClientBuilder).setSSLContext(any(SSLContext.class));
        verify(httpAsyncClientBuilder).setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
    }

    @Test
    public void createRestClient_should_handle_generic_exception_and_wrap_in_appexception() {
        // Arrange
        ClusterSettings clusterSettings = new ClusterSettings("test-host", 9200, "user:pass");

        // Simulate a runtime exception during client creation
        when(RestClient.builder(any(HttpHost.class))).thenThrow(new RuntimeException("Test exception"));

        // Act & Assert
        try {
            elasticClientHandler.createRestClient(clusterSettings);
        } catch (AppException e) {
            assertEquals(500, e.getError().getCode());
            assertEquals("search client error", e.getError().getReason());
            assertEquals("error creating search client", e.getError().getMessage());
            // Check that debug info contains the host information
            String debugInfo = e.getError().toString();
            assertTrue(debugInfo.contains("test-host"));
        }
    }

    @Test
    public void createClientBuilder_should_set_correct_default_headers() {
        // Arrange
        String host = "test-host";
        String authHeader = "Basic test-auth";
        int port = 9243;
        String scheme = "https";
        String tls = "true";

        when(RestClient.builder(any(HttpHost.class))).thenReturn(builder);
        when(builder.setRequestConfigCallback(any())).thenReturn(builder);
        when(builder.setHttpClientConfigCallback(any())).thenReturn(builder);

        // Act
        elasticClientHandler.createClientBuilder(host, authHeader, port, scheme, tls);

        // Assert
        ArgumentCaptor<Header[]> headersCaptor = ArgumentCaptor.forClass(Header[].class);
        verify(builder).setDefaultHeaders(headersCaptor.capture());

        Header[] headers = headersCaptor.getValue();
        assertEquals(7, headers.length);

        // Verify authorization header
        boolean foundAuthHeader = false;
        for (Header header : headers) {
            if ("Authorization".equals(header.getName())) {
                assertEquals(authHeader, header.getValue());
                foundAuthHeader = true;
                break;
            }
        }
        assertTrue("Should have Authorization header", foundAuthHeader);

        // Verify cluster name header
        boolean foundClusterHeader = false;
        for (Header header : headers) {
            if ("cluster.name".equals(header.getName())) {
                assertEquals(host, header.getValue());
                foundClusterHeader = true;
                break;
            }
        }
        assertTrue("Should have cluster.name header", foundClusterHeader);
    }
}
