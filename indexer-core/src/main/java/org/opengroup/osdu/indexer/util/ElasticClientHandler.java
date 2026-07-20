/*
 * Copyright 2017-2025, The Open Group
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
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.indexer.IElasticSettingService;
import org.opengroup.osdu.core.common.model.search.ClusterSettings;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Log
public class ElasticClientHandler {

  // Elastic cluster Rest client settings
  private static final int CLOUD_REST_CLIENT_PORT = 9243;
  private static final int REST_CLIENT_CONNECT_TIMEOUT = 60000;
  private static final int REST_CLIENT_SOCKET_TIMEOUT = 60000;
  private static final int REST_CLIENT_CONNECTION_TTL_SECONDS = 60;

  @Setter
  @Getter
  @Value("${elasticsearch.client.max.conn.total:50}")
  private int maxConnTotal;
  
  @Setter
  @Getter
  @Value("${elasticsearch.client.max.conn.per.route:20}")
  private int maxConnPerRoute;

  @Value("#{new Boolean('${security.https.certificate.trust:false}')}")
  private Boolean isSecurityHttpsCertificateTrust;

  @Autowired
  private IElasticSettingService elasticSettingService;
  
  @Autowired
  private TenantInfo tenantInfo;
  
  /**
   * Get an Elasticsearch client for the current partition.
   * Each call creates a new client with its own connection resources.
   * 
   * @return ElasticsearchClient for the current partition
   */
  public ElasticsearchClient createRestClientFromClusterInfo() {
    String partitionId = tenantInfo.getDataPartitionId();
    log.fine("Creating new Elasticsearch client for partition: " + partitionId);
    return createRestClient(elasticSettingService.getElasticClusterInformation());
  }

  /**
   * Create a new Elasticsearch client for the given cluster settings.
   * 
   * @param clusterSettings The cluster settings to use
   * @return A new ElasticsearchClient
   */
  public ElasticsearchClient createRestClient(final ClusterSettings clusterSettings) {
    String cluster = null;
    String host = null;
    int port = CLOUD_REST_CLIENT_PORT;
    String protocolScheme = "https";
    String tls = "true";

    try {
      cluster = clusterSettings.getHost();
      host = clusterSettings.getHost();
      port = clusterSettings.getPort();
      if (!clusterSettings.isHttps()) {
        protocolScheme = "http";
      }

      if (!clusterSettings.isTls()) {
        tls = "false";
      }
      String basicEncoded = Base64
          .getEncoder().encodeToString(clusterSettings.getUserNameAndPassword().getBytes());
      String basicAuthenticationHeaderVal = String.format("Basic %s", basicEncoded);
      
      RestClientBuilder builder = createClientBuilder(host, basicAuthenticationHeaderVal, port,
          protocolScheme, tls);
      RestClient restClient = builder.build();

      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.configure(SerializationFeature.INDENT_OUTPUT, false);

      RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
      return new ElasticsearchClient(transport);
    } catch (AppException e) {
      throw e;
    } catch (Exception e) {
      throw new AppException(
          HttpStatus.SC_INTERNAL_SERVER_ERROR,
          "search client error",
          "error creating search client",
          String
              .format("Elastic client connection params, cluster: %s, host: %s, port: %s", cluster,
                  host, port),
          e);
    }
  }

  protected RestClientBuilder createClientBuilder(String host, String basicAuthenticationHeaderVal,
      int port, String protocolScheme, String tls) {
    RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, protocolScheme));
    builder.setRequestConfigCallback(
        requestConfigBuilder -> requestConfigBuilder.setConnectTimeout(REST_CLIENT_CONNECT_TIMEOUT)
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
      httpClientBuilder.setMaxConnTotal(maxConnTotal)
          .setMaxConnPerRoute(maxConnPerRoute)
          .setConnectionTimeToLive(REST_CLIENT_CONNECTION_TTL_SECONDS, TimeUnit.SECONDS);
          
      if ("https".equals(protocolScheme) && isSecurityHttpsCertificateTrust) {
        SSLContext sslContext = createSSLContext();
        httpClientBuilder
            .setSSLContext(sslContext)
            .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }
      
      return httpClientBuilder;
    });
    builder.setDefaultHeaders(defaultHeaders);
    return builder;
  }

  protected SSLContext createSSLContext() {
    SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
    try {
      sslContextBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
      return sslContextBuilder.build();
    } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
      log.severe(e.getMessage());
    }
      return null;
  }

  public void setSecurityHttpsCertificateTrust(Boolean isSecurityHttpsCertificateTrust) {
    this.isSecurityHttpsCertificateTrust = isSecurityHttpsCertificateTrust;
  }
}
