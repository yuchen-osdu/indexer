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
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.opengroup.osdu.indexer.util.ElasticClientHandler;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import lombok.extern.java.Log;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
// TODO: Elastic Client Handler should be designed to allow cloud providers to implement their own handler if not we have to inherited
// SPI needs to be refactored
@Primary
@Component
@Log
public class ElasticClientHandlerAws extends ElasticClientHandler {

    private static final int REST_CLIENT_CONNECT_TIMEOUT = 60000;
    private static final int REST_CLIENT_SOCKET_TIMEOUT = 60000;
    private static final int REST_CLIENT_CONNECTION_TTL_SECONDS = 60;

    @Value("${aws.es.certificate.disableTrust:false}")
    // @Value("#{new Boolean('${aws.es.certificate.disableTrust:false}')}")
    private Boolean disableSslCertificateTrust;

    public ElasticClientHandlerAws() {
        //DO nothing here, just a class constructor
    }

    @Override
    public RestClientBuilder createClientBuilder(String host, String basicAuthenticationHeaderVal, int port, String protocolScheme, String tls) {

        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, protocolScheme));
        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(REST_CLIENT_CONNECT_TIMEOUT)
                .setSocketTimeout(REST_CLIENT_SOCKET_TIMEOUT));        

        Header[] defaultHeaders = new Header[]{
                new BasicHeader("client.transport.nodes_sampler_interval", "30s"),
                new BasicHeader("client.transport.ping_timeout", "30s"),
                new BasicHeader("client.transport.sniff", "false"),
                new BasicHeader("request.headers.X-Found-Cluster", host),
                new BasicHeader("cluster.name", host),
                new BasicHeader("xpack.security.transport.ssl.enabled", tls),
                new BasicHeader("Authorization", basicAuthenticationHeaderVal),
        };


    builder.setHttpClientConfigCallback(httpClientBuilder -> {
        httpClientBuilder.setMaxConnTotal(getMaxConnTotal())
            .setMaxConnPerRoute(getMaxConnPerRoute())
            .setConnectionTimeToLive(REST_CLIENT_CONNECTION_TTL_SECONDS, TimeUnit.SECONDS);
            
        if ((isLocalHost(host) || disableSslCertificateTrust)) {
            SSLContext sslContext;            
            try {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{ UnsafeX509ExtendedTrustManager.INSTANCE }, null);
                httpClientBuilder
                    .setSSLContext(sslContext)
                    // Suppressed: java:S4830 - Hostname verification disabled for internal EKS cluster connections
                    // This is controlled by aws.es.certificate.disableTrust configuration property
                    .setSSLHostnameVerifier((s, session) -> true); // NOSONAR
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                log.severe(e.getMessage());
            }
        }
        
        return httpClientBuilder;
    });
        builder.setDefaultHeaders(defaultHeaders);
        return builder;
    }

    private boolean isLocalHost(String uri) {
        return (uri.contains("localhost") || uri.contains("127.0.0.1"));
    }
}
