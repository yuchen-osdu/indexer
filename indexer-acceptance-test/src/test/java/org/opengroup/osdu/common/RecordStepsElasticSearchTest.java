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

package org.opengroup.osdu.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ElasticClient;
import org.opengroup.osdu.core.test.util.polling.PollingClient;
import org.opengroup.osdu.core.test.util.polling.PollingResult;
import org.opengroup.osdu.util.Config;

class RecordStepsElasticSearchTest {

    private static final String LOCAL_ELASTIC_TEST_PROPERTY = "runLocalElasticTests";
    private static final Pattern COUNT_PATTERN = Pattern.compile("\"count\"\\s*:\\s*(\\d+)");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Test
    void assertQueryReturnsWaitsForIndexCreationBeforePolling() {
        assumeTrue(Boolean.getBoolean(LOCAL_ELASTIC_TEST_PROPERTY),
            "Set -D" + LOCAL_ELASTIC_TEST_PROPERTY + "=true to run local Elasticsearch tests");

        String index = "recordsteps-local-" + UUID.randomUUID();
        String preflightIndex = "recordsteps-preflight-" + UUID.randomUUID();

        try (ElasticClient elasticClient = elasticClient()) {
            createIndex(preflightIndex);
            elasticClient.awaitIndexCreation(preflightIndex, Duration.ofSeconds(5), Duration.ofSeconds(1));
            deleteIndex(preflightIndex);

            CompletableFuture<Void> indexCreation = CompletableFuture.runAsync(() -> {
                sleep(1_000);
                createIndex(index);
                indexRecord(index);
            });

            try {
                elasticClient.awaitIndexCreation(index, Duration.ofSeconds(15), Duration.ofSeconds(1));
                PollingResult<Long> result = PollingClient.builder()
                    .elasticClient(elasticClient)
                    .maxAttempts(5)
                    .intervalMs(1_000)
                    .build()
                    .pollForQueryResultCount(index, 1, () -> elasticClient.fetchRecords(index));
                assertTrue(result.isSuccess(), result.getFailureReason());
                assertEquals(1, result.getValue().longValue());
                indexCreation.join();
            } finally {
                deleteIndex(index);
            }
        }
    }

    private static void createIndex(String index) {
        String body = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0
              },
              "mappings": {
                "properties": {
                  "id": {
                    "type": "keyword"
                  }
                }
              }
            }
            """;
        HttpResponse<String> response = send(HttpRequest.newBuilder(indexUri(index))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build());
        assertEquals(200, response.statusCode(), response.body());
    }

    private static void indexRecord(String index) {
        String body = "{\"id\":\"record-1\"}";
        HttpResponse<String> response = send(HttpRequest.newBuilder(indexUri(index + "/_doc/record-1?refresh=true"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build());
        assertEquals(201, response.statusCode(), response.body());
    }

    private static void deleteIndex(String index) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(indexUri(index))
            .DELETE()
            .build());
        assertTrue(response.statusCode() == 200 || response.statusCode() == 404, response.body());
    }

    private static long countRecords(String index) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(indexUri(index + "/_count"))
            .GET()
            .build());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(response.body());
        }
        Matcher matcher = COUNT_PATTERN.matcher(response.body());
        assertTrue(matcher.find(), response.body());
        return Long.parseLong(matcher.group(1));
    }

    private static void refreshIndex(String index) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(indexUri(index + "/_refresh"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build());
        assertEquals(200, response.statusCode(), response.body());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to create local Elasticsearch index", e);
        }
    }

    private static ElasticClient elasticClient() {
        return new LocalElasticClient(
            Config.getElastic8UserName(),
            Config.getElastic8Password(),
            Config.getElastic8Host(),
            Config.getElastic8Port(),
            Config.isElastic8SslEnabled(),
            Config.isSecurityHttpsCertificateTrust());
    }

    private static class LocalElasticClient extends ElasticClient {

        LocalElasticClient(String username, String password, String host, int port,
                           boolean sslEnabled, boolean trustCertificates) {
            super(username, password, host, port, sslEnabled, trustCertificates);
        }

        @Override
        public void waitForIndexReady(String index, int timeoutSeconds) {
            fetchRecords(index);
        }

        @Override
        public void refreshIndex(String index) {
            RecordStepsElasticSearchTest.refreshIndex(index);
        }

        @Override
        public long fetchRecords(String index) {
            return countRecords(index);
        }
    }

    private static URI indexUri(String path) {
        String scheme = Config.isElastic8SslEnabled() ? "https" : "http";
        return URI.create(scheme + "://" + Config.getElastic8Host() + ":" + Config.getElastic8Port() + "/" + path);
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP_CLIENT.send(withAuth(request), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("Local Elasticsearch request failed", e);
        }
    }

    private static HttpRequest withAuth(HttpRequest request) {
        String credentials = Config.getElastic8UserName() + ":" + Config.getElastic8Password();
        String authorization = Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
            .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
            .header("Authorization", "Basic " + authorization);
        request.headers().map().forEach((name, values) ->
            values.forEach(value -> builder.header(name, value)));
        return builder.build();
    }
}
