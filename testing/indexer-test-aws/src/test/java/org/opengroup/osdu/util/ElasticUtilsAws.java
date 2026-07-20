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

package org.opengroup.osdu.util;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

@Slf4j
public class ElasticUtilsAws extends ElasticUtils {

    private static final int REST_CLIENT_CONNECT_TIMEOUT = 60000;
    private static final int REST_CLIENT_SOCKET_TIMEOUT = 60000;
    private static final int REST_CLIENT_RETRY_TIMEOUT = 60000;

    @Override
    public RestClientBuilder createClientBuilder(String host, String authHeaderValue, int port) {
        port = Integer.parseInt(System.getProperty("ELASTIC_PORT", System.getenv("ELASTIC_PORT")));
        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "https"));
        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder.setConnectTimeout(REST_CLIENT_CONNECT_TIMEOUT)
                .setSocketTimeout(REST_CLIENT_SOCKET_TIMEOUT));

        //dont enforce CA/cert validity for tests
        SSLContext sslContext;            
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{ UnsafeX509ExtendedTrustManager.INSTANCE }, null);
            builder.setHttpClientConfigCallback(httpClientBuilder -> 
            httpClientBuilder.setSSLContext(sslContext)
                            .setSSLHostnameVerifier((s, session) -> true));
        } catch (NoSuchAlgorithmException e) {
            log.error("No such algorithm.", e);
        } catch (KeyManagementException e) {
            log.error("Key management error.", e);
        }

        Header[] defaultHeaders = new Header[]{
                new BasicHeader("client.transport.nodes_sampler_interval", "30s"),
                new BasicHeader("client.transport.ping_timeout", "30s"),
                new BasicHeader("client.transport.sniff", "false"),
                new BasicHeader("request.headers.X-Found-Cluster", Config.getElastic8Host()),
                new BasicHeader("cluster.name", Config.getElastic8Host()),
                new BasicHeader("xpack.security.transport.ssl.enabled", Boolean.toString(true)),
                new BasicHeader("Authorization", authHeaderValue),
        };

        builder.setDefaultHeaders(defaultHeaders);
        return builder;
    }
}
