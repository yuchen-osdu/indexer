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

package org.opengroup.osdu.indexer.aws.service;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import javax.net.ssl.SSLContext;
import java.lang.reflect.Method;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ElasticClientHandlerAwsTest {

    @Mock
    private RestClientBuilder mockBuilder;
    
    @Mock
    private HttpAsyncClientBuilder httpAsyncClientBuilder;
    
    private ElasticClientHandlerAws handler;

    @Before
    public void setup() {
        handler = new ElasticClientHandlerAws();
        // Set default values for configurable connection pool limits
        handler.setMaxConnTotal(50);
        handler.setMaxConnPerRoute(20);

        // Set disableSslCertificateTrust to false by default
        ReflectionTestUtils.setField(handler, "disableSslCertificateTrust", false);
        
        // Configure HttpAsyncClientBuilder to return itself when methods are called
        when(httpAsyncClientBuilder.setSSLHostnameVerifier(any())).thenReturn(httpAsyncClientBuilder);
        when(httpAsyncClientBuilder.setMaxConnTotal(anyInt())).thenReturn(httpAsyncClientBuilder);
        when(httpAsyncClientBuilder.setMaxConnPerRoute(anyInt())).thenReturn(httpAsyncClientBuilder);
    }

    @Test
    public void createClientBuilder_basic_functionality() {
        try (MockedStatic<RestClient> mockedRestClient = Mockito.mockStatic(RestClient.class)) {
            // Arrange
            mockedRestClient.when(() -> RestClient.builder(any(HttpHost.class))).thenReturn(mockBuilder);
            when(mockBuilder.setRequestConfigCallback(any())).thenReturn(mockBuilder);
            when(mockBuilder.setHttpClientConfigCallback(any())).thenReturn(mockBuilder);
            when(mockBuilder.setDefaultHeaders(any(Header[].class))).thenReturn(mockBuilder);

            // Act
            RestClientBuilder result = handler.createClientBuilder("example.com", "Bearer token", 9200, "https", "true");

            // Assert
            verify(mockBuilder).setRequestConfigCallback(any());
            verify(mockBuilder).setHttpClientConfigCallback(any());
            verify(mockBuilder).setDefaultHeaders(any(Header[].class));
            assertEquals(mockBuilder, result);
        }
    }

    @Test
    public void createClientBuilder_should_configure_connection_pool_limits() {
        // Arrange
        int customMaxConnTotal = 100;
        int customMaxConnPerRoute = 40;
        handler.setMaxConnTotal(customMaxConnTotal);
        handler.setMaxConnPerRoute(customMaxConnPerRoute);

        // Act
        handler.createClientBuilder("example.com", "Bearer", 6469, "https", "tls");

        // Assert
        assertEquals(customMaxConnTotal, handler.getMaxConnTotal());
        assertEquals(customMaxConnPerRoute, handler.getMaxConnPerRoute());
    }

    @Test
    public void createClientBuilder_should_set_correct_headers() {
        try (MockedStatic<RestClient> mockedRestClient = Mockito.mockStatic(RestClient.class)) {
            // Arrange
            String host = "example.com";
            String authHeader = "Bearer token";
            String tls = "true";

            mockedRestClient.when(() -> RestClient.builder(any(HttpHost.class))).thenReturn(mockBuilder);
            when(mockBuilder.setRequestConfigCallback(any())).thenReturn(mockBuilder);
            when(mockBuilder.setHttpClientConfigCallback(any())).thenReturn(mockBuilder);
            when(mockBuilder.setDefaultHeaders(any(Header[].class))).thenReturn(mockBuilder);

            // Act
            handler.createClientBuilder(host, authHeader, 9200, "https", tls);

            // Assert
            ArgumentCaptor<Header[]> headersCaptor = ArgumentCaptor.forClass(Header[].class);
            verify(mockBuilder).setDefaultHeaders(headersCaptor.capture());

            Header[] headers = headersCaptor.getValue();
            assertEquals(7, headers.length);

            // Verify authorization header
            boolean foundAuthHeader = false;
            boolean foundTlsHeader = false;
            boolean foundClusterHeader = false;

            for (Header header : headers) {
                if ("Authorization".equals(header.getName())) {
                    assertEquals(authHeader, header.getValue());
                    foundAuthHeader = true;
                }
                if ("xpack.security.transport.ssl.enabled".equals(header.getName())) {
                    assertEquals(tls, header.getValue());
                    foundTlsHeader = true;
                }
                if ("cluster.name".equals(header.getName())) {
                    assertEquals(host, header.getValue());
                    foundClusterHeader = true;
                }
            }

            assertTrue("Should have Authorization header", foundAuthHeader);
            assertTrue("Should have TLS header", foundTlsHeader);
            assertTrue("Should have cluster.name header", foundClusterHeader);
        }
    }

    @Test
    public void createClientBuilder_should_configure_ssl_for_localhost() {
        try (MockedStatic<RestClient> mockedRestClient = Mockito.mockStatic(RestClient.class)) {
            // Arrange
            String host = "localhost";

            mockedRestClient.when(() -> RestClient.builder(any(HttpHost.class))).thenReturn(mockBuilder);
            when(mockBuilder.setRequestConfigCallback(any())).thenReturn(mockBuilder);

            // Capture the HttpClientConfigCallback to manually invoke it
            ArgumentCaptor<org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback> callbackCaptor =
                ArgumentCaptor.forClass(org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback.class);
            when(mockBuilder.setHttpClientConfigCallback(callbackCaptor.capture())).thenReturn(mockBuilder);
            when(mockBuilder.setDefaultHeaders(any(Header[].class))).thenReturn(mockBuilder);

            // Configure HttpAsyncClientBuilder to return itself when methods are called
            when(httpAsyncClientBuilder.setSSLContext(any(SSLContext.class))).thenReturn(httpAsyncClientBuilder);
            when(httpAsyncClientBuilder.setSSLHostnameVerifier(any())).thenReturn(httpAsyncClientBuilder);

            // Act
            handler.createClientBuilder(host, "Bearer token", 9200, "https", "true");

            // Manually invoke the callback to trigger SSL context creation
            callbackCaptor.getValue().customizeHttpClient(httpAsyncClientBuilder);

            // Assert
            // Verify that SSL context is configured for localhost
            verify(httpAsyncClientBuilder).setSSLContext(any(SSLContext.class));
            verify(httpAsyncClientBuilder).setSSLHostnameVerifier(any());
        }
    }

    @Test
    public void createClientBuilder_should_configure_ssl_when_disableSslCertificateTrust_is_true() {
        try (MockedStatic<RestClient> mockedRestClient = Mockito.mockStatic(RestClient.class)) {
            // Arrange
            String host = "example.com"; // Not localhost
            ReflectionTestUtils.setField(handler, "disableSslCertificateTrust", true);

            mockedRestClient.when(() -> RestClient.builder(any(HttpHost.class))).thenReturn(mockBuilder);
            when(mockBuilder.setRequestConfigCallback(any())).thenReturn(mockBuilder);

            // Capture the HttpClientConfigCallback to manually invoke it
            ArgumentCaptor<org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback> callbackCaptor =
                ArgumentCaptor.forClass(org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback.class);
            when(mockBuilder.setHttpClientConfigCallback(callbackCaptor.capture())).thenReturn(mockBuilder);
            when(mockBuilder.setDefaultHeaders(any(Header[].class))).thenReturn(mockBuilder);

            // Configure HttpAsyncClientBuilder to return itself when methods are called
            when(httpAsyncClientBuilder.setSSLContext(any(SSLContext.class))).thenReturn(httpAsyncClientBuilder);
            when(httpAsyncClientBuilder.setSSLHostnameVerifier(any())).thenReturn(httpAsyncClientBuilder);

            // Act
            handler.createClientBuilder(host, "Bearer token", 9200, "https", "true");

            // Manually invoke the callback to trigger SSL context creation
            callbackCaptor.getValue().customizeHttpClient(httpAsyncClientBuilder);

            // Assert
            // Verify that SSL context is configured when disableSslCertificateTrust is true
            verify(httpAsyncClientBuilder).setSSLContext(any(SSLContext.class));
            verify(httpAsyncClientBuilder).setSSLHostnameVerifier(any());
        }
    }

    @Test
    public void createClientBuilder_should_not_configure_ssl_for_non_localhost_with_trust_enabled() {
        try (MockedStatic<RestClient> mockedRestClient = Mockito.mockStatic(RestClient.class)) {
            // Arrange
            String host = "example.com"; // Not localhost
            ReflectionTestUtils.setField(handler, "disableSslCertificateTrust", false);

            mockedRestClient.when(() -> RestClient.builder(any(HttpHost.class))).thenReturn(mockBuilder);
            when(mockBuilder.setRequestConfigCallback(any())).thenReturn(mockBuilder);

            // Capture the HttpClientConfigCallback to manually invoke it
            ArgumentCaptor<org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback> callbackCaptor =
                ArgumentCaptor.forClass(org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback.class);
            when(mockBuilder.setHttpClientConfigCallback(callbackCaptor.capture())).thenReturn(mockBuilder);
            when(mockBuilder.setDefaultHeaders(any(Header[].class))).thenReturn(mockBuilder);

            // Act
            handler.createClientBuilder(host, "Bearer token", 9200, "https", "true");

            // Manually invoke the callback to trigger SSL context creation
            callbackCaptor.getValue().customizeHttpClient(httpAsyncClientBuilder);

            // Assert
            // Verify that SSL context is NOT configured for non-localhost when disableSslCertificateTrust is false
            verify(httpAsyncClientBuilder, never()).setSSLContext(any(SSLContext.class));
            verify(httpAsyncClientBuilder, never()).setSSLHostnameVerifier(any());
        }
    }

    @Test
    public void isLocalHost_should_identify_localhost_correctly() throws Exception {
        // Use reflection to access private method
        Method isLocalHostMethod = ElasticClientHandlerAws.class.getDeclaredMethod("isLocalHost", String.class);
        isLocalHostMethod.setAccessible(true);

        boolean isLocalhost1 = (boolean) isLocalHostMethod.invoke(handler, "localhost");
        boolean isLocalhost2 = (boolean) isLocalHostMethod.invoke(handler, "127.0.0.1");
        boolean isLocalhost3 = (boolean) isLocalHostMethod.invoke(handler, "example.com");

        assertTrue("localhost should be identified as localhost", isLocalhost1);
        assertTrue("127.0.0.1 should be identified as localhost", isLocalhost2);
        assertFalse("example.com should not be identified as localhost", isLocalhost3);
    }
}
