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

package org.opengroup.osdu.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.ToString;
import lombok.extern.java.Log;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Base HTTP client using Apache HttpClient 5.
 * Provides HTTP request capabilities for integration tests.
 */
@Log
@ToString
public abstract class HTTPClient {

    protected static final String HEADER_CORRELATION_ID = "correlation-id";
    private static final Timeout CONNECTION_TIMEOUT = Timeout.of(60, TimeUnit.SECONDS);
    private static final Timeout RESPONSE_TIMEOUT = Timeout.of(60, TimeUnit.SECONDS);

    private volatile CloseableHttpClient sharedClient;
    private final Object clientLock = new Object();

    public abstract String getAccessToken();

    /**
     * Returns a shared HttpClient instance, creating it lazily if needed.
     * The client is reused across requests to take advantage of connection pooling.
     */
    protected CloseableHttpClient getOrCreateHttpClient() {
        if (sharedClient == null) {
            synchronized (clientLock) {
                if (sharedClient == null) {
                    sharedClient = createHttpClient();
                }
            }
        }
        return sharedClient;
    }

    private CloseableHttpClient createHttpClient() {
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                    .build();

            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                            .setSslContext(sslContext)
                            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                            .build())
                    .build();

            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(CONNECTION_TIMEOUT)
                    .setResponseTimeout(RESPONSE_TIMEOUT)
                    .build();

            return HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setDefaultRequestConfig(requestConfig)
                    .build();
        } catch (Exception e) {
            log.warning("Failed to create SSL-configured client, using default: " + e.getMessage());
            return HttpClients.createDefault();
        }
    }

    /**
     * Sends an HTTP request and returns the response.
     *
     * @param httpMethod The HTTP method (GET, POST, PUT, DELETE, PATCH, etc.)
     * @param url        The request URL
     * @param payLoad    The request body (can be null for GET/DELETE)
     * @param headers    Additional headers to include
     * @param token      The authorization token
     * @return HttpResponse containing status, body, and headers
     */
    public HttpResponse send(String httpMethod, String url, String payLoad, Map<String, String> headers, String token) {
        String correlationId = UUID.randomUUID().toString();
        log.info(String.format("Request correlation id: %s", correlationId));

        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put(HEADER_CORRELATION_ID, correlationId);

        log.info("URL: = " + url);
        log.info(String.format("Request method: %s\nHeaders: %s\nRequest Body: %s",
                httpMethod, headers, indentatedResponseBody(payLoad)));

        try {
            CloseableHttpClient client = getOrCreateHttpClient();
            HttpUriRequestBase request = createRequest(httpMethod, url, payLoad);

            // Set headers
            request.setHeader("Authorization", token);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Accept", "application/json");

            for (Map.Entry<String, String> header : headers.entrySet()) {
                request.setHeader(header.getKey(), header.getValue());
            }

            return client.execute(request, response -> {
                int statusCode = response.getCode();
                String responseBody = response.getEntity() != null
                        ? EntityUtils.toString(response.getEntity())
                        : "";

                // Convert headers to Map<String, List<String>>
                Map<String, List<String>> responseHeaders = new HashMap<>();
                for (Header header : response.getHeaders()) {
                    responseHeaders.computeIfAbsent(header.getName(), k -> new ArrayList<>())
                            .add(header.getValue());
                }

                // Determine content type
                String contentType = "application/json";
                Header contentTypeHeader = response.getFirstHeader("Content-Type");
                if (contentTypeHeader != null) {
                    contentType = contentTypeHeader.getValue().split(";")[0].trim();
                }

                log.info(String.format("Response status: %d, Content-Type: %s", statusCode, contentType));

                return new HttpResponse(statusCode, responseBody, responseHeaders, contentType);
            });
        } catch (Exception e) {
            log.log(Level.SEVERE, "Send request error", e);
            throw new AssertionError("Error: Send request error", e);
        }
    }

    /**
     * Creates the appropriate HTTP request based on the method.
     */
    private HttpUriRequestBase createRequest(String httpMethod, String url, String payLoad) {
        HttpUriRequestBase request;

        switch (httpMethod.toUpperCase()) {
            case "GET":
                request = new HttpGet(url);
                break;
            case "POST":
                request = new HttpPost(url);
                break;
            case "PUT":
                request = new HttpPut(url);
                break;
            case "DELETE":
                request = new HttpDelete(url);
                break;
            case "PATCH":
                request = new HttpPatch(url);
                break;
            case "HEAD":
                request = new HttpHead(url);
                break;
            case "OPTIONS":
                request = new HttpOptions(url);
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + httpMethod);
        }

        if (payLoad != null) {
            request.setEntity(new StringEntity(payLoad, ContentType.APPLICATION_JSON));
        }

        return request;
    }

    public Map<String, String> getCommonHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("data-partition-id", Config.getDataPartitionIdTenant1());
        return headers;
    }

    public static Map<String, String> overrideHeader(Map<String, String> currentHeaders, String... partitions) {
        String value = String.join(",", partitions);
        currentHeaders.put("data-partition-id", value);
        return currentHeaders;
    }

    public static String indentatedResponseBody(String responseBody) {
        if (responseBody != null) {
            try {
                JsonElement jsonElement = JsonParser.parseString(responseBody);
                return new GsonBuilder().setPrettyPrinting().create().toJson(jsonElement);
            } catch (Exception e) {
                // Not valid JSON, return as-is
                return responseBody;
            }
        }
        return responseBody;
    }
}
