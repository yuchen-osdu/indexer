/*
  Copyright 2020 Google LLC
  Copyright 2020 EPAM Systems, Inc

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.opengroup.osdu.util;

import static org.opengroup.osdu.common.RecordSteps.X_COLLABORATION;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.cluster.HealthRequest;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import org.elasticsearch.client.ResponseException;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.GeoShapeRelation;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.mapping.ObjectProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.ExistsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.GeoShapeFieldQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.GeoShapeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.CloseIndexRequest;
import co.elastic.clients.elasticsearch.indices.CloseIndexResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.Strings;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import javax.net.ssl.SSLContext;
import lombok.extern.java.Log;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.opengroup.osdu.models.record.RecordData;


/**
 * All util methods to use elastic apis for tests
 * It should be used only in the Setup or TearDown phase of the test
 */
@Log
public class ElasticUtils {

    private static final int REST_CLIENT_CONNECT_TIMEOUT = 5000;
    private static final int REST_CLIENT_SOCKET_TIMEOUT = 60000;

    private final Time REQUEST_TIMEOUT = Time.of(builder -> builder.time("1m"));
    private final String username;
    private final String password;
    private final String host;
    private final boolean sslEnabled;
    private final boolean jwtAuthEnabled;
    private ElasticsearchClient elasticsearchClient;
    private RestClient lowLevelRestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ElasticUtils() {
        this.username = Config.getElastic8UserName();
        this.password = Config.getElastic8Password();
        this.host = Config.getElastic8Host();
        this.sslEnabled = Config.isElastic8SslEnabled();
        this.jwtAuthEnabled = Config.isJwtAuthEnabled();
    }

    /**
     * Create an index with the specified mapping
     * @param index the name of the index to create
     * @param mapping the mapping JSON as a string (can be null for no mapping, or a full index definition with settings)
     */
    public void createIndex(String index, String mapping) {
        try {
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);

            log.info("Creating index with name: " + index);
            CreateIndexRequest.Builder requestBuilder = new CreateIndexRequest.Builder();
            requestBuilder.index(index);
            requestBuilder.timeout(REQUEST_TIMEOUT);

            // Handle different mapping formats
            if (mapping != null && !mapping.isEmpty()) {
                // Check if the mapping contains full index definition (with settings/mappings keys)
                if (mapping.contains("\"settings\"") || mapping.contains("\"mappings\"")) {
                    // Full index definition - use it directly
                    requestBuilder.withJson(new StringReader(mapping));
                } else {
                    // Just the mapping properties - add default settings
                    IndexSettings indexSettings = IndexSettings.of(builder ->
                        builder.numberOfShards("1")
                            .numberOfReplicas("1")
                    );
                    requestBuilder.settings(indexSettings)
                        .mappings(TypeMapping.of(
                            mappingBuilder -> mappingBuilder.withJson(new StringReader(mapping))));
                }
            } else {
                // No mapping provided - use default settings only
                IndexSettings indexSettings = IndexSettings.of(builder ->
                    builder.numberOfShards("1")
                        .numberOfReplicas("1")
                );
                requestBuilder.settings(indexSettings);
            }

            CreateIndexResponse createIndexResponse = client.indices().create(requestBuilder.build());

            //wait for ack
            if (createIndexResponse.acknowledged() && createIndexResponse.shardsAcknowledged()) {
                log.info("Index creation acknowledged immediately");
            } else {
                // Wait for index to be ready using PollingUtils
                log.info("Index creation not immediately acknowledged, waiting for index to be ready...");

                PollingResult<Boolean> result = PollingUtils.pollIndexReady(
                    index,
                    PollingConfig.indexCreation(),
                    indexName -> {
                        try {
                            return waitForIndexGreen(indexName, 5);
                        } catch (IOException e) {
                            log.warning(String.format("Health check failed: %s", e.getMessage()));
                            return false;
                        }
                    }
                );

                if (!result.isSuccess()) {
                    throw new AssertionError(result.getFailureReason());
                }
            }

            log.info("Done creating index with name: " + index);

        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_BAD_REQUEST &&
                (e.getMessage().contains("resource_already_exists_exception") || e.getMessage().contains("IndexAlreadyExistsException"))) {
                log.info("Index already exists. Ignoring error...");
            } else {
                throw new AssertionError("Failed to create index: " + e.getMessage());
            }
        } catch (Exception e) {
            throw new AssertionError(e.getMessage());
        }
    }

    public int indexRecords(String index, String kind, List<Map<String, Object>> testRecords) {
        log.info("Creating records inside index with name: " + index);

        BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
        bulkRequestBuilder.timeout(REQUEST_TIMEOUT);

        List<IndexOperation<Map<String, Object>>> records = ElasticUtils.getIndexReqFromRecord(index, kind, testRecords);
        for (IndexOperation<Map<String, Object>> operation : records) {
            bulkRequestBuilder.operations(new BulkOperation.Builder().index(operation).build());
        }

        BulkResponse bulkResponse = null;
        try {
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);
            bulkResponse = client.bulk(bulkRequestBuilder.build());
            log.info("Done creating records inside index with name: " + index);

        } catch (IOException e) {
            log.log(Level.SEVERE, "bulk indexing failed", e);
        }

        // Double check failures
        if (bulkResponse != null && bulkResponse.errors()) {
            throw new AssertionError("setup failed in data post to Index");
        }

        try {
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);

            RefreshRequest request = RefreshRequest.of(builder -> builder.index(index));
            RefreshResponse refreshResponse = client.indices().refresh(request);
            log.info(String.format("refreshed index, acknowledged shards: %s | failed shards: %s | total shards: %s ",
                refreshResponse.shards().successful(), refreshResponse.shards().failed(),
                refreshResponse.shards().total()));

        } catch (IOException | ElasticsearchException e) {
            log.log(Level.SEVERE, "index refresh failed", e);
        }

        return records.size();
    }

    public void deleteIndex(String index) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            //retry if the elastic cluster is snapshotting and we cant delete it
            PollingResult<Boolean> result = PollingUtils.pollWithRetry(
                PollingConfig.indexDeletion(),
                () -> {
                    try {
                        log.info("Attempting to delete index: " + index);
                        DeleteIndexRequest request = DeleteIndexRequest.of(builder -> builder.index(index));
                        client.indices().delete(request);
                        log.info("Done deleting index with name: " + index);
                        return true;
                    } catch (ElasticsearchException e) {
                        if (e.status() == HttpStatus.SC_NOT_FOUND) {
                            log.info("Index not found, considering deletion successful");
                            return true;
                        } else if (e.getMessage().contains("Cannot delete indices that are being snapshotted")) {
                            closeIndex(client, index);
                            log.info(String.format("skipping %s index delete, as snapshot is being run, closing the index instead", index));
                            return true;
                        } else {
                            log.info("Delete failed with error: " + e.getMessage());
                            return false;
                        }
                    } catch (IOException e) {
                        log.warning("IO error during delete: " + e.getMessage());
                        return false;
                    }
                },
                success -> success,
                null
            );

            if (!result.isSuccess()) {
                // Last resort - try to close the index
                closeIndex(client, index);
                log.info(String.format("Could not delete index %s after retries, closed it instead", index));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while deleting index: " + index);
        }
    }

    /**
     * Delete stale test indices older than {@code maxAgeMs} milliseconds.
     * <p>
     * Test indices are identified by the strict pattern {@code *-indexer\d{13,17}-*}
     * (the digits encode the epoch-millis timestamp appended at test-run time).
     * Index age is determined from Elasticsearch's {@code creation_date} setting,
     * not from the name, so the decision is reliable even if naming conventions evolve.
     * <p>
     * This method is best-effort: all errors are logged and swallowed so that a
     * cleanup failure never blocks the actual test run. Both open and closed indices
     * are included (via {@code expand_wildcards=all}).
     *
     * @param maxAgeMs minimum age in milliseconds before an index is considered stale
     * @return the number of indices deleted (0 on any error)
     */
    public int deleteStaleTestIndices(long maxAgeMs) {
        // Strict pattern: index name must contain "-indexer" followed by 13-17 digits
        java.util.regex.Pattern testIndexPattern =
                java.util.regex.Pattern.compile(".*-indexer\\d{13,17}-.*");

        try {
            RestClient client = getOrCreateLowLevelClient();

            // _cat/indices with creation_date, expand_wildcards=all to include closed indices
            Request catRequest = new Request("GET",
                    "/_cat/indices/*-indexer*?h=index,creation.date.string,creation.date&format=json&expand_wildcards=all");
            Response catResponse = client.performRequest(catRequest);
            String body = EntityUtils.toString(catResponse.getEntity());
            JsonNode indices = objectMapper.readTree(body);

            if (!indices.isArray() || indices.isEmpty()) {
                log.info("No test indices found matching '*-indexer*'");
                return 0;
            }

            long now = System.currentTimeMillis();
            int deleted = 0;
            int total = 0;

            for (JsonNode entry : indices) {
                String indexName = entry.path("index").asText("");
                if (!testIndexPattern.matcher(indexName).matches()) {
                    continue;
                }
                total++;

                long creationDate = entry.path("creation.date").asLong(0);
                if (creationDate <= 0) {
                    continue;
                }

                long ageMs = now - creationDate;
                if (ageMs > maxAgeMs) {
                    log.info(String.format("Deleting stale test index: %s (age: %ds)",
                            indexName, ageMs / 1000));
                    try {
                        deleteIndex(indexName);
                        deleted++;
                    } catch (Exception e) {
                        log.warning("Failed to delete stale index " + indexName + ": " + e.getMessage());
                    }
                }
            }

            log.info(String.format("Stale index cleanup: deleted %d of %d test indices (threshold: %ds)",
                    deleted, total, maxAgeMs / 1000));
            return deleted;

        } catch (Exception e) {
            log.warning("Stale test index cleanup failed (non-fatal): " + e.getMessage());
            return 0;
        }
    }

    public long fetchRecords(String index) throws IOException {
        try {
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);

            SearchRequest request = SearchRequest.of(builder -> builder.index(index));
            SearchResponse<Void> searchResponse = client.search(request, Void.class);
            return searchResponse.hits().total().value();

        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            return -1;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 503) {
                log.log(Level.INFO, String.format("Index %s shards not available yet (503): %s", index, e.getMessage()));
                return -1;
            }
            throw e;
        }
    }

    public long fetchRecordsByTags(String index, String tagKey, String tagValue) throws IOException {
        try {
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);

            TermsQueryField termsQueryField = new TermsQueryField.Builder()
                .value(List.of(FieldValue.of(tagValue)))
                .build();

            TermsQuery termsQuery = new TermsQuery.Builder()
                .field("tags.%s".formatted(tagKey))
                .terms(termsQueryField)
                .build();

            BoolQuery boolQuery = new BoolQuery.Builder()
                .must(termsQuery._toQuery())
                .build();

            SearchRequest searchRequest = new SearchRequest.Builder()
                .index(index)
                .query(boolQuery._toQuery())
                .build();

            SearchResponse<Void> searchResponse = client.search(searchRequest, Void.class);
            return searchResponse.hits().total().value();

        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            return -1;
        }
    }

    public long fetchRecordsByFieldAndFieldValue(String index, String fieldKey, String fieldValue) throws IOException {
        try {
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);

            SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder()
                .index(index);

            if (!Strings.isNullOrEmpty(fieldKey)) {
                FieldValue elkFieldValue = new FieldValue.Builder()
                    .stringValue(fieldValue)
                    .build();

                MatchQuery matchQuery = new MatchQuery.Builder()
                    .field(fieldKey)
                    .query(elkFieldValue)
                    .build();

                BoolQuery boolQuery = new BoolQuery.Builder()
                    .must(matchQuery._toQuery())
                    .build();

                searchRequestBuilder.query(Query.of(builder -> builder.bool(boolQuery)));
            }
            SearchResponse<Void> searchResponse = client.search(searchRequestBuilder.build(), Void.class);
            return searchResponse.hits().total().value();
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            return -1;
        }
    }

    /**
     * Search for documents where the given field matches the boolean value using a TermQuery.
     * A TermQuery with booleanValue ONLY matches on ES boolean-typed fields — it does NOT match
     * text fields containing the string "true". This is the definitive proof that ES stored and
     * indexed the value as a native boolean.
     */
    public long fetchRecordsByBooleanFieldValue(String index, String fieldKey, boolean booleanValue) {
        try {
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);
            FieldValue elkFieldValue = FieldValue.of(b -> b.booleanValue(booleanValue));
            TermQuery termQuery = TermQuery.of(b -> b.field(fieldKey).value(elkFieldValue));
            SearchRequest searchRequest = SearchRequest.of(b -> b.index(index).query(termQuery._toQuery()));
            SearchResponse<Void> response = client.search(searchRequest, Void.class);
            return response.hits().total().value();
        } catch (IOException | ElasticsearchException e) {
            throw new RuntimeException(String.format(
                "fetchRecordsByBooleanFieldValue failed for index '%s', field '%s': %s",
                index, fieldKey, e.getMessage()), e);
        }
    }

    public String[] fetchIndexList() throws IOException {
        try {
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);
            // List all index names
            GetIndexRequest request = GetIndexRequest.of(builder -> builder.index("*"));
            GetIndexResponse response = client.indices().get(request);
            Set<String> keySet = response.result().keySet();
            return keySet.toArray(new String[0]);
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            return new String[]{};
        }
    }

    public List<Map<String, Object>> fetchRecordsByAttribute(String index, String attributeKey, String attributeValue) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);

            TermsQueryField termsQueryField = new TermsQueryField.Builder()
                .value(List.of(FieldValue.of(attributeValue)))
                .build();

            TermsQuery termsQuery = new TermsQuery.Builder()
                .field(attributeKey)
                .terms(termsQueryField)
                .build();

            BoolQuery boolQuery = new BoolQuery.Builder()
                .must(termsQuery._toQuery())
                .build();

            SearchRequest searchRequest = new SearchRequest.Builder()
                .index(index)
                .query(boolQuery._toQuery())
                .build();

            TypeReference<Map<String, Object>> typeReference = new TypeReference<>() {};
            Type type = typeReference.getType();
            SearchResponse<Map<String, Object>> searchResponse = client.search(searchRequest, type);
            for (Hit<Map<String, Object>> searchHit : searchResponse.hits().hits()) {
                Map<String, Object> source = searchHit.source();
                out.add(source);
            }
            return out;
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            return out;
        }
    }

    public long fetchRecordsByExistQuery(String index, String attributeName) throws Exception {
        try {
            TimeUnit.SECONDS.sleep(40);
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);
            ExistsQuery existsQuery = new ExistsQuery.Builder()
                .field(attributeName)
                .build();

            SearchRequest searchRequest = new SearchRequest.Builder()
                .index(index)
                .query(Query.of(builder -> builder.exists(existsQuery)))
                .build();

            SearchResponse searchResponse = client.search(searchRequest, Void.class);
            return searchResponse.hits().total().value();
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            return -1;
        }
    }
    
    public long fetchRecordsByGeoWithinQuery(String index, String field, Double topLatitude, Double topLongitude,
        Double bottomLatitude, Double bottomLongitude) throws Exception {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {

            StringReader jsonData = new StringReader("""
                {
                    "type":"envelope",
                    "coordinates": [[%f, %f], [%f, %f] ]
                }
                """.formatted(topLongitude, topLatitude, bottomLongitude, bottomLatitude));

            GeoShapeFieldQuery geoShapeFieldQuery = GeoShapeFieldQuery.of(
                geoShapeFieldQueryBuilder ->
                    geoShapeFieldQueryBuilder.relation(GeoShapeRelation.Within)
                        .shape(JsonData.from(jsonData))
            );

            GeoShapeQuery geoShapeQuery = GeoShapeQuery.of(geoShapeQueryBuilder ->
                geoShapeQueryBuilder.field(field)
                    .ignoreUnmapped(false)
                    .boost(1.0f)
                    .shape(geoShapeFieldQuery));

            BoolQuery boolQuery = BoolQuery.of(boolQueryBuilder ->
                boolQueryBuilder.must(queryBuilder -> queryBuilder
                    .geoShape(geoShapeQuery)).boost(1.0f));

            SearchRequest searchRequest = new SearchRequest.Builder()
                .index(index)
                .query(boolQuery._toQuery())
                .build();

            SearchResponse searchResponse = client.search(searchRequest, Void.class);
            return searchResponse.hits().total().value();
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            return -1;
        }
    }

    public long fetchRecordsByAsIngestedCoordinates(String index, String pointX, Double topPointX, Double bottomPointX,
        String pointY, Double topPointY, Double bottomPointY) throws Exception {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            RangeQuery xRangeQuery = RangeQuery.of(builder -> builder.untyped(
                untypedBuilder -> untypedBuilder.field(pointX).lte(JsonData.of(topPointX))
                    .gte(JsonData.of(bottomPointX))));

            RangeQuery yRangeQuery = RangeQuery.of(builder -> builder.untyped(
                untypedBuilder -> untypedBuilder.field(pointY).lte(JsonData.of(topPointY))
                    .gte(JsonData.of(bottomPointY))));

            BoolQuery boolQuery = BoolQuery.of(
                builder -> builder.must(xRangeQuery._toQuery()).must(yRangeQuery._toQuery()));

            SearchRequest searchRequest = SearchRequest.of(builder -> builder.index(index).query(boolQuery._toQuery()));

            SearchResponse searchResponse = client.search(searchRequest, Void.class);
            return searchResponse.hits().total().value();
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            return -1;
        }
    }

    public SearchResponse<Record> fetchRecordsByIdAndMustHaveXcollab(String index, String id, String xCollab) throws Exception {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try{
            TermQuery idTerm = TermQuery.of(builder -> builder.field("id").value(id));
            TermQuery xCollabTerm = TermQuery.of(builder -> builder.field(X_COLLABORATION).value(xCollab));
            BoolQuery of = BoolQuery.of(builder -> builder.must(idTerm._toQuery()).must(xCollabTerm._toQuery()));

            SearchRequest searchRequest = SearchRequest.of(builder -> builder.index(index).query(of._toQuery()));

            log.log(Level.INFO,
                String.format("xcollab feature: print searchRequest to get record by id and x-collab value: %s",
                    searchRequest));

            return client.search(searchRequest, Record.class);
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            throw e;
        }
    }


    public DeleteResponse deleteRecordsById(String index, String id) throws Exception {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            DeleteRequest request = DeleteRequest.of(builder -> builder.index(index).id(id));
            return client.delete(request);
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            throw e;
        }
    }

    /**
     * Deletes the first document found in the given index. Useful for creating
     * document count mismatches in validation tests.
     * @return the ID of the deleted document
     */
    public String deleteFirstDocument(String index) throws Exception {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            SearchResponse<Map<String, Object>> searchResponse =
                client.search(s -> s.index(index).size(1), (Class<Map<String, Object>>) (Class<?>) Map.class);
            if (searchResponse.hits().hits().isEmpty()) {
                throw new IllegalStateException("No documents found in index: " + index);
            }
            String docId = searchResponse.hits().hits().get(0).id();
            log.info(String.format("Deleting first document '%s' from index '%s'", docId, index));
            deleteRecordsById(index, docId);
            refreshIndex(index);
            return docId;
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception while deleting first document from index '%s': %s", index, e.getMessage()));
            throw e;
        }
    }

    /**
     * Deletes all documents from the given index using delete-by-query with
     * match_all. The index itself (and its mapping) is preserved — only the
     * documents are removed. Useful for testing reindex-v2 against an empty
     * source index.
     * @return the number of documents deleted
     */
    public long deleteAllDocuments(String index) throws Exception {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            DeleteByQueryRequest request = DeleteByQueryRequest.of(builder -> builder
                .index(index)
                .query(q -> q.matchAll(m -> m)));
            DeleteByQueryResponse response = client.deleteByQuery(request);
            long deleted = response.deleted() != null ? response.deleted() : 0;
            log.info(String.format("Deleted all %d documents from index '%s'", deleted, index));
            refreshIndex(index);
            return deleted;
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format(
                "Elastic search threw exception while deleting all documents from index '%s': %s",
                index, e.getMessage()));
            throw e;
        }
    }

    public long fetchRecordsByNestedQuery(String index, String path, String firstNestedField, String firstNestedValue,
        String secondNestedField, String secondNestedValue) throws Exception {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            FieldValue firstFieldValue = FieldValue.of(firstNestedValue);
            FieldValue secondFieldValue = FieldValue.of(secondNestedValue);
            MatchQuery firstMatch = MatchQuery.of(
                matchBuilder -> matchBuilder.field(firstNestedField).query(firstFieldValue));
            MatchQuery secondMatch = MatchQuery.of(
                matchBuilder -> matchBuilder.field(secondNestedField + ".keyword").query(secondFieldValue));
            BoolQuery boolQuery = BoolQuery.of(
                boolBuilder -> boolBuilder.must(firstMatch._toQuery()).must(secondMatch._toQuery()));

            NestedQuery nestedQuery = NestedQuery.of(
                builder -> builder.path(path).query(boolQuery._toQuery()).scoreMode(ChildScoreMode.Avg));

            SearchRequest searchRequest = SearchRequest.of(
                builder -> builder.index(index).query(nestedQuery._toQuery()));

            SearchResponse searchResponse = client.search(searchRequest, Void.class);
            return searchResponse.hits().total().value();
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            return -1;
        }
    }


    public long fetchRecordsWithFlattenedFieldsQuery(String index, String flattenedField, String flattenedFieldValue)
        throws IOException {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            MatchQuery matchQuery = MatchQuery.of(
                builder -> builder.field(flattenedField).query(FieldValue.of(flattenedFieldValue)));

            BoolQuery boolQuery = BoolQuery.of(builder -> builder.must(matchQuery._toQuery()));

            SearchRequest searchRequest = SearchRequest.of(builder -> builder.index(index).query(boolQuery._toQuery()));

            SearchResponse searchResponse = client.search(searchRequest, Void.class);
            return searchResponse.hits().total().value();
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            return -1;
        }
    }

    public RecordData fetchDataFromObjectsArrayRecords(String index) throws IOException {
        try {
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);

            SortOptions idSort = SortOptions.of(
                sortBuilder -> sortBuilder.field(
                    FieldSort.of(fieldSortBuilder -> fieldSortBuilder.field("id"))
                )
            );

            SearchRequest request = SearchRequest.of(builder -> builder.index(index).sort(idSort));
            SearchResponse<RecordData> searchResponse = client.search(request, RecordData.class);

            HitsMetadata<RecordData> hits = searchResponse.hits();
            if (hits.hits().size() != 0) {
                return hits.hits().get(0).source();
            }
            return null;

        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Elastic search threw exception: %s", e.getMessage()));
            return null;
        }
    }

    public Map<String, IndexMappingRecord> getMapping(String index) throws IOException {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        GetMappingRequest.Builder getMappingBuilder = new GetMappingRequest.Builder();
        getMappingBuilder.index(index);
        getMappingBuilder.masterTimeout(REQUEST_TIMEOUT);
        GetMappingResponse mappingResponse = client.indices().getMapping(getMappingBuilder.build());
        Map<String, IndexMappingRecord> result = mappingResponse.result();
        return result;
    }

    public void refreshIndex(String index) throws IOException {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            RefreshRequest refreshRequest = RefreshRequest.of(builder -> builder.index(index));
            client.indices().refresh(refreshRequest);
        } catch (ElasticsearchException exception) {
            log.info(String.format("index: %s refresh failed. message: %s", index, exception.getMessage()));
        }

    }

    public boolean waitForIndexGreen(String index, int timeoutSeconds) throws IOException {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            HealthRequest healthRequest = HealthRequest.of(builder -> builder
                .index(index)
                .waitForStatus(HealthStatus.Yellow)
                .timeout(Time.of(t -> t.time(timeoutSeconds + "s"))));

            HealthResponse response = client.cluster().health(healthRequest);
            boolean isReady = response.status() == HealthStatus.Green || response.status() == HealthStatus.Yellow;

            if (isReady) {
                log.info(String.format("Index %s is ready with status: %s", index, response.status()));
            } else {
                log.warning(String.format("Index %s not ready, status: %s", index, response.status()));
            }

            return isReady;
        } catch (ElasticsearchException e) {
            log.warning(String.format("Failed to check health of index %s: %s", index, e.getMessage()));
            return false;
        } catch (ResponseException e) {
            // Handle timeout (408) and other HTTP errors
            int statusCode = e.getResponse().getStatusLine().getStatusCode();
            if (statusCode == 408) {
                log.info(String.format("Health check timed out for index %s after %d seconds (index may not exist yet)", index, timeoutSeconds));
            } else if (statusCode == 404) {
                log.info(String.format("Index %s does not exist yet", index));
            } else {
                log.warning(String.format("Health check failed for index %s with HTTP %d: %s",
                    index, statusCode, e.getMessage()));
            }
            return false;
        }
    }

    private boolean closeIndex(ElasticsearchClient client, String index) {
        try {
            CloseIndexRequest request = CloseIndexRequest.of(builder -> builder.index(index).timeout(Time.of(timeBuilder -> timeBuilder.time("1m"))));
            CloseIndexResponse closeIndexResponse = client.indices().close(request);
            return closeIndexResponse.acknowledged();
        } catch (ElasticsearchException | IOException exception) {
            log.info(String.format("index: %s close failed. message: %s", index, exception.getMessage()));
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<IndexOperation<Map<String, Object>>> getIndexReqFromRecord(String index, String kind, List<Map<String, Object>> testRecords) {
        List<IndexOperation<Map<String, Object>>> dataList = new ArrayList<>();
        Gson gson = new Gson();
        try {
            for (Map<String, Object> record : testRecords) {
                IndexOperation<Map<String, Object>> indexOperation = new IndexOperation.Builder<Map<String, Object>>()
                    .index(index)
                    .id((String) record.get("id"))
                    .document(gson.fromJson(gson.toJson(record), Map.class))
                    .build();

                dataList.add(indexOperation);
            }
        } catch (Exception e) {
            throw new AssertionError(e.getMessage());
        }
        return dataList;
    }


    private ElasticsearchClient getOrCreateClient(String username, String password, String host) {
        if (this.elasticsearchClient == null) {
            ElasticsearchClient restHighLevelClient;
            int port = Config.getElastic8Port();
            try {
                String authHeaderValue;
                if (jwtAuthEnabled) {
                    // Use JWT authentication
                    String jwtToken = Config.getJwtToken();
                    if (jwtToken != null && !jwtToken.isEmpty()) {
                        authHeaderValue = jwtToken;
                    } else {
                        log.warning("JWT authentication enabled but no token found, falling back to Basic auth");
                        String rawString = String.format("%s:%s", username, password);
                        authHeaderValue = String.format("Basic %s", Base64.getEncoder().encodeToString(rawString.getBytes()));
                    }
                } else {
                    // Use Basic authentication
                    String rawString = String.format("%s:%s", username, password);
                    authHeaderValue = String.format("Basic %s", Base64.getEncoder().encodeToString(rawString.getBytes()));
                }

                RestClientBuilder builder = createClientBuilder(host, authHeaderValue, port);
                RestClientTransport transport = new RestClientTransport(builder.build(), new JacksonJsonpMapper());
                restHighLevelClient = new ElasticsearchClient(transport);

            } catch (Exception e) {
                throw new AssertionError("Setup elastic error: %s" + e.getMessage());
            }
            this.elasticsearchClient = restHighLevelClient;
        }
        return this.elasticsearchClient;
    }

    public RestClientBuilder createClientBuilder(String url, String authHeaderValue, int port) throws Exception {
        String scheme = this.sslEnabled ? "https" : "http";

        url = url.trim().replaceAll("^(?i)(https?)://", "");
        URI uri = new URI(scheme + "://" + url);

        RestClientBuilder builder = RestClient.builder(new HttpHost(uri.getHost(), port, uri.getScheme()));
        if (!Strings.isNullOrEmpty(uri.getPath())) {
            builder.setPathPrefix(uri.getPath());
        }

        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder.setConnectTimeout(REST_CLIENT_CONNECT_TIMEOUT)
                .setSocketTimeout(REST_CLIENT_SOCKET_TIMEOUT));

        Header[] defaultHeaders = new Header[]{
                new BasicHeader("client.transport.nodes_sampler_interval", "30s"),
                new BasicHeader("client.transport.ping_timeout", "30s"),
                new BasicHeader("client.transport.sniff", "false"),
                new BasicHeader("request.headers.X-Found-Cluster", Config.getElastic8Host()),
                new BasicHeader("cluster.name", Config.getElastic8Host()),
                new BasicHeader("xpack.security.transport.ssl.enabled", Boolean.toString(true)),
                new BasicHeader("Authorization", authHeaderValue),
        };

        boolean isSecurityHttpsCertificateTrust = Config.isSecurityHttpsCertificateTrust();
        log.info(String.format(
                "Elastic client connection uses protocolScheme = %s with a flag "
                        + "'security.https.certificate.trust' = %s, JWT auth enabled = %s",
                scheme, isSecurityHttpsCertificateTrust, jwtAuthEnabled));

        if ("https".equals(scheme) && isSecurityHttpsCertificateTrust) {
            log.warning("Elastic client connection uses TrustSelfSignedStrategy()");
            SSLContext sslContext = createSSLContext();
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                httpClientBuilder.setSSLContext(sslContext)
                    .setSSLHostnameVerifier(
                        NoopHostnameVerifier.INSTANCE));
        }

        builder.setDefaultHeaders(defaultHeaders);
        return builder;
    }

    private SSLContext createSSLContext() {
        SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
        try {
            sslContextBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            return sslContextBuilder.build();
        } catch (NoSuchAlgorithmException e) {
            log.severe(e.getMessage());
        } catch (KeyStoreException e) {
            log.severe(e.getMessage());
        } catch (KeyManagementException e) {
            log.severe(e.getMessage());
        }
        return null;
    }

    public boolean isIndexExist(String index) {
        boolean exists = false;
        try {
            exists = createRestClientAndCheckIndexExist(index);
        } catch (ElasticsearchException e) {
            log.log(Level.INFO, String.format("Error getting index: %s %s", index, e.getMessage()));
        }
        return exists;
    }

    private boolean createRestClientAndCheckIndexExist(String index) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            ExistsRequest request = ExistsRequest.of(builder -> builder.index(index));
            return client.indices().exists(request).value();
        } catch (IOException e) {
            log.log(Level.INFO, String.format("Error getting index: %s %s", index, e.getMessage()));
        }
        return false;
    }

    /**
     * Check if a given name is an alias in Elasticsearch
     * @param aliasName the name to check
     * @return true if it's an alias, false otherwise
     */
    public boolean aliasExists(String aliasName) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            GetAliasRequest request = GetAliasRequest.of(builder -> builder.name(aliasName));
            GetAliasResponse response = client.indices().getAlias(request);
            return !response.result().isEmpty();
        } catch (IOException | ElasticsearchException e) {
            log.log(Level.INFO, String.format("Error checking alias: %s %s", aliasName, e.getMessage()));
            return false;
        }
    }

    /**
     * Get the physical index that an alias points to
     * @param aliasName the alias name
     * @return the physical index name, or null if not an alias or has multiple targets
     */
    public String getPhysicalIndexFromAlias(String aliasName) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            GetAliasRequest request = GetAliasRequest.of(builder -> builder.name(aliasName));
            GetAliasResponse response = client.indices().getAlias(request);

            if (response.result().size() == 1) {
                // Return the first (and only) index that this alias points to
                return response.result().keySet().iterator().next();
            } else if (response.result().size() > 1) {
                log.warning(String.format("Alias %s points to multiple indices: %s",
                    aliasName, response.result().keySet()));
            }
        } catch (IOException | ElasticsearchException e) {
            log.log(Level.INFO, String.format("Error getting physical index for alias: %s %s",
                aliasName, e.getMessage()));
        }
        return null;
    }

    /**
     * Get all physical indexes matching a pattern
     * @param pattern the pattern to match (e.g., "index-r*")
     * @return list of matching index names
     */
    public List<String> getPhysicalIndexesByPattern(String pattern) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        List<String> matchingIndexes = new ArrayList<>();
        try {
            GetIndexRequest request = GetIndexRequest.of(builder -> builder.index(pattern));
            GetIndexResponse response = client.indices().get(request);
            matchingIndexes.addAll(response.result().keySet());
        } catch (IOException | ElasticsearchException e) {
            log.log(Level.INFO, String.format("Error getting indexes by pattern: %s %s",
                pattern, e.getMessage()));
        }
        return matchingIndexes;
    }

    /**
     * Get complete alias to physical index mappings
     * @param indexPattern pattern to filter results (use "*" for all)
     * @return map of alias names to list of physical index names
     */
    public Map<String, List<String>> getAliasMapping(String indexPattern) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        Map<String, List<String>> aliasMap = new HashMap<>();

        try {
            GetAliasRequest request = GetAliasRequest.of(builder -> builder.index(indexPattern));
            GetAliasResponse response = client.indices().getAlias(request);

            // Process the response to build alias -> indexes mapping
            for (Map.Entry<String, IndexAliases> entry : response.result().entrySet()) {
                String indexName = entry.getKey();
                IndexAliases aliases = entry.getValue();

                if (aliases.aliases() != null) {
                    for (String aliasName : aliases.aliases().keySet()) {
                        aliasMap.computeIfAbsent(aliasName, k -> new ArrayList<>()).add(indexName);
                    }
                }
            }
        } catch (IOException | ElasticsearchException e) {
            log.log(Level.INFO, String.format("Error getting alias mappings: %s %s",
                indexPattern, e.getMessage()));
        }

        return aliasMap;
    }

    /**
     * Check if index name follows revision pattern (ends with -r{number})
     * @param indexName the index name to check
     * @return true if it follows the revision pattern
     */
    public boolean isPhysicalIndexWithRevision(String indexName) {
        Pattern revisionPattern = Pattern.compile(".*-r\\d+$");
        return revisionPattern.matcher(indexName).matches();
    }

    /**
     * Extract revision number from a physical index name
     * @param physicalIndexName the index name (e.g., "index-r2")
     * @return the revision number, or -1 if not found
     */
    public int extractRevisionNumber(String physicalIndexName) {
        Pattern revisionPattern = Pattern.compile(".*-r(\\d+)$");
        Matcher matcher = revisionPattern.matcher(physicalIndexName);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

    /**
     * Check if a physical index exists with the given name.
     * This method specifically checks for physical indices only, not aliases.
     *
     * IMPORTANT: This will return false even if an alias with the same name exists.
     * Use this when you need to verify that a physical index (not an alias) exists.
     *
     * Note: This method checks for an exact index name match. Wildcards in the indexName
     * parameter are supported by the underlying API, but this method will only return true
     * if the exact name provided exists as a physical index in the results.
     *
     * @param indexName the physical index name to check
     * @return true if a physical index with this exact name exists, false if it's an alias or doesn't exist
     */
    public boolean physicalIndexExists(String indexName) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            GetIndexRequest request = GetIndexRequest.of(builder -> builder.index(indexName));
            GetIndexResponse response = client.indices().get(request);

            // Check if the exact index name exists in the result
            // This handles cases where wildcards might match multiple indices
            return response.result().containsKey(indexName);
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                return false;
            }
            log.log(Level.INFO, String.format("Error checking physical index: %s %s", indexName, e.getMessage()));
            return false;
        } catch (IOException e) {
            log.log(Level.INFO, String.format("Error checking physical index: %s %s", indexName, e.getMessage()));
            return false;
        }
    }

    /**
     * Check if an index exists (works for both aliases and physical indexes)
     * @param indexName the index or alias name
     * @return true if exists (as either alias or physical index)
     */
    public boolean indexOrAliasExists(String indexName) {
        return isIndexExist(indexName) || aliasExists(indexName);
    }


    /**
     * Get the mapping of an index
     * @param indexName the index name
     * @return the mapping as a Map
     */
    public Map<String, Property> getIndexMappingProperties(String indexName) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            GetMappingRequest request = GetMappingRequest.of(builder -> builder.index(indexName));
            GetMappingResponse response = client.indices().getMapping(request);

            IndexMappingRecord mappingRecord = response.result().get(indexName);
            if (mappingRecord == null && !response.result().isEmpty()) {
                // If indexName is an alias, use the first entry from the result
                mappingRecord = response.result().values().iterator().next();
            }
            if (mappingRecord != null && mappingRecord.mappings() != null) {
                return mappingRecord.mappings().properties();
            }
        } catch (IOException | ElasticsearchException e) {
            log.log(Level.WARNING, String.format("Error getting mapping for index %s: %s",
                indexName, e.getMessage()));
        }
        return new HashMap<>();
    }

    /**
     * Extract data properties from ES mapping structure.
     * Navigates through the mapping to get data.properties.
     * @param mappingProperties the mapping properties from getIndexMappingProperties
     * @return the data properties map
     */
    public Map<String, Property> extractDataProperties(Map<String, Property> mappingProperties) {
        if (mappingProperties != null && mappingProperties.containsKey("data")) {
            ObjectProperty dataProperty = mappingProperties.get("data").object();
            if (dataProperty != null && dataProperty.properties() != null) {
                return dataProperty.properties();
            }
        }
        return new HashMap<>();
    }

    /**
     * Check if specified fields exist in the data properties of an index mapping.
     * @param indexName the index name
     * @param fieldNames array of field names to check
     * @return true if all fields exist, false otherwise
     */
    public boolean checkMappingFieldsExist(String indexName, String[] fieldNames) {
        Map<String, Property> mappingProperties = getIndexMappingProperties(indexName);
        if (mappingProperties == null) {
            return false;
        }

        Map<String, Property> dataProps = extractDataProperties(mappingProperties);
        if (dataProps == null) {
            return false;
        }

        // Check if all fields are present
        for (String fieldName : fieldNames) {
            String trimmedFieldName = fieldName.trim();
            if (!dataProps.containsKey(trimmedFieldName)) {
                log.info(String.format("Field '%s' not yet present in mapping for index %s",
                    trimmedFieldName, indexName));
                return false;
            }
        }

        return true;
    }

    /**
     * Get the ES field type for a given field path within an index.
     * Supports dot-separated paths (e.g., "data.IsActive").
     * @param indexName the index name
     * @param fieldPath dot-separated path to the field (e.g., "data.IsActive")
     * @return the ES type string (e.g., "boolean", "text", "keyword"), or null if not found
     */
    public String getFieldType(String indexName, String fieldPath) {
        Map<String, Property> properties = getIndexMappingProperties(indexName);
        if (properties == null || properties.isEmpty()) {
            log.log(Level.WARNING, String.format("No mapping properties found for index: %s", indexName));
            return null;
        }
        String[] parts = fieldPath.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
            Property prop = properties.get(parts[i]);
            if (prop == null) {
                log.log(Level.WARNING, String.format("Path segment '%s' not found in mapping for index: %s", parts[i], indexName));
                return null;
            }
            if (prop.isObject()) {
                properties = prop.object().properties();
            } else if (prop.isNested()) {
                properties = prop.nested().properties();
            } else {
                log.log(Level.WARNING, String.format("Path segment '%s' is not an object/nested type in index: %s", parts[i], indexName));
                return null;
            }
            if (properties == null || properties.isEmpty()) {
                log.log(Level.WARNING, String.format("Path segment '%s' has no child properties in index: %s", parts[i], indexName));
                return null;
            }
        }
        String leafName = parts[parts.length - 1];
        Property leaf = (properties == null) ? null : properties.get(leafName);
        if (leaf == null) {
            log.log(Level.WARNING, String.format("Leaf field '%s' not found in mapping for index: %s", leafName, indexName));
            return null;
        }
        return leaf._kind().jsonValue();
    }

    /**
     * Convert a kind to an index name
     * @param kind the kind string
     * @return the index name
     */
    public String getIndexNameFromKind(String kind) {
        // Convert kind to index name following OSDU convention
        // e.g., "tenant1:indexer:test--Integration:1.0.0" -> "tenant1-indexer-test--integration-1.0.0"
        return kind.replace(":", "-").toLowerCase();
    }

    /**
     * Search for documents by a specific field value
     * @param indexName the index to search
     * @param field the field to search
     * @param value the value to search for
     * @return the count of matching documents
     */
    public long searchByField(String indexName, String field, String value) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            TermQuery termQuery = TermQuery.of(builder ->
                builder.field(field).value(value));

            SearchRequest searchRequest = SearchRequest.of(builder ->
                builder.index(indexName).query(termQuery._toQuery()));

            SearchResponse<Void> response = client.search(searchRequest, Void.class);
            return response.hits().total().value();
        } catch (IOException | ElasticsearchException e) {
            log.log(Level.WARNING, String.format("Error searching by field '%s' in index '%s': %s",
                field, indexName, e.getMessage()));
            return 0;
        }
    }

    /**
     * Adds a new alias pointing to a physical index.
     *
     * <p>This method is intended for adding aliases that do not yet exist on any index.
     * To prevent accidental multi-index aliases, it checks whether the alias already
     * exists on a <em>different</em> physical index before proceeding. If it does, an
     * {@link IllegalStateException} is thrown so callers know they should use the
     * service's {@code switchAlias} endpoint instead.
     *
     * @param physicalIndex the physical index to add the alias to
     * @param aliasName     the alias name to add (must not already exist on a different index)
     * @return true if the alias was added successfully
     * @throws IllegalStateException if the alias already exists on a different physical index
     */
    public boolean addAlias(String physicalIndex, String aliasName) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            // Guard: if the alias already exists on a different index, fail fast rather
            // than silently creating a multi-index alias (which is never the intent here).
            try {
                GetAliasResponse existing = client.indices().getAlias(
                    GetAliasRequest.of(r -> r.name(aliasName)));
                for (Map.Entry<String, IndexAliases> entry : existing.result().entrySet()) {
                    if (!entry.getKey().equals(physicalIndex)) {
                        throw new IllegalStateException(String.format(
                            "Alias '%s' already exists on index '%s'; use switchAlias to move it",
                            aliasName, entry.getKey()));
                    }
                }
            } catch (ElasticsearchException e) {
                if (e.status() != 404) {
                    throw e; // 404 = alias doesn't exist yet, which is the expected case
                }
            }

            co.elastic.clients.elasticsearch.indices.PutAliasRequest request =
                co.elastic.clients.elasticsearch.indices.PutAliasRequest.of(builder ->
                    builder.index(physicalIndex).name(aliasName).timeout(REQUEST_TIMEOUT));
            co.elastic.clients.elasticsearch.indices.PutAliasResponse response =
                client.indices().putAlias(request);
            if (response.acknowledged()) {
                log.info(String.format("Added alias '%s' to index '%s'", aliasName, physicalIndex));
                return true;
            }
            return false;
        } catch (IOException | ElasticsearchException e) {
            log.log(Level.WARNING, String.format("Error adding alias '%s' to index '%s': %s",
                aliasName, physicalIndex, e.getMessage()));
            return false;
        }
    }

    /**
     * Get all alias names on a specific physical index.
     * @param physicalIndex the physical index name
     * @return list of alias names, or empty list if none
     */
    public List<String> getAliasesOnIndex(String physicalIndex) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        List<String> aliases = new ArrayList<>();
        try {
            GetAliasRequest request = GetAliasRequest.of(builder -> builder.index(physicalIndex));
            GetAliasResponse response = client.indices().getAlias(request);

            IndexAliases indexAliases = response.result().get(physicalIndex);
            if (indexAliases != null && indexAliases.aliases() != null) {
                aliases.addAll(indexAliases.aliases().keySet());
            }
        } catch (IOException | ElasticsearchException e) {
            log.log(Level.WARNING, String.format("Error getting aliases on index '%s': %s",
                physicalIndex, e.getMessage()));
        }
        return aliases;
    }

    /**
     * Get the index settings (number_of_shards, number_of_replicas) for a given index.
     * @param indexName the physical index name
     * @return a map with keys "number_of_shards" and "number_of_replicas" (as Strings)
     */
    public Map<String, String> getIndexSettings(String indexName) {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        Map<String, String> result = new HashMap<>();
        try {
            GetIndexRequest request = GetIndexRequest.of(builder -> builder.index(indexName));
            GetIndexResponse response = client.indices().get(request);

            IndexSettings settings = response.result().get(indexName).settings();
            if (settings != null && settings.index() != null) {
                if (settings.index().numberOfShards() != null) {
                    result.put("number_of_shards", settings.index().numberOfShards());
                }
                if (settings.index().numberOfReplicas() != null) {
                    result.put("number_of_replicas", settings.index().numberOfReplicas());
                }
            }
        } catch (IOException | ElasticsearchException e) {
            log.log(Level.WARNING, String.format("Error getting settings for index '%s': %s",
                indexName, e.getMessage()));
        }
        return result;
    }

    // ========== Snapshot utility methods ==========

    private RestClient getOrCreateLowLevelClient() throws Exception {
        if (this.lowLevelRestClient == null) {
            String authHeaderValue;
            if (jwtAuthEnabled) {
                String jwtToken = Config.getJwtToken();
                authHeaderValue = (jwtToken != null && !jwtToken.isEmpty())
                    ? jwtToken
                    : "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            } else {
                authHeaderValue = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            }
            this.lowLevelRestClient = createClientBuilder(host, authHeaderValue, Config.getElastic8Port()).build();
        }
        return this.lowLevelRestClient;
    }

    /**
     * Throttles the given snapshot repository to 1 byte/sec so snapshots progress
     * too slowly to complete, making them deterministically IN_PROGRESS for testing.
     */
    public void throttleSnapshotRepository(String repoName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        Request getReq = new Request("GET", "/_snapshot/" + repoName);
        Response getResp = client.performRequest(getReq);
        String body = EntityUtils.toString(getResp.getEntity());
        JsonNode root = objectMapper.readTree(body);
        JsonNode repoNode = root.get(repoName);
        String type = repoNode.get("type").asText();
        ObjectNode settings = (ObjectNode) repoNode.get("settings").deepCopy();
        settings.put("max_snapshot_bytes_per_sec", "1b");
        Map<String, Object> putBody = new HashMap<>();
        putBody.put("type", type);
        putBody.put("settings", objectMapper.convertValue(settings, Map.class));
        Request putReq = new Request("PUT", "/_snapshot/" + repoName);
        putReq.setEntity(new StringEntity(objectMapper.writeValueAsString(putBody), ContentType.APPLICATION_JSON));
        client.performRequest(putReq);
        log.info(String.format("Throttled snapshot repository '%s' to 1b/s", repoName));
    }

    /**
     * Restores the snapshot repository settings by removing the throttle cap.
     */
    public void restoreSnapshotRepositoryThrottle(String repoName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        Request getReq = new Request("GET", "/_snapshot/" + repoName);
        Response getResp = client.performRequest(getReq);
        String body = EntityUtils.toString(getResp.getEntity());
        JsonNode root = objectMapper.readTree(body);
        JsonNode repoNode = root.get(repoName);
        String type = repoNode.get("type").asText();
        ObjectNode settings = (ObjectNode) repoNode.get("settings").deepCopy();
        settings.remove("max_snapshot_bytes_per_sec");
        Map<String, Object> putBody = new HashMap<>();
        putBody.put("type", type);
        putBody.put("settings", objectMapper.convertValue(settings, Map.class));
        Request putReq = new Request("PUT", "/_snapshot/" + repoName);
        putReq.setEntity(new StringEntity(objectMapper.writeValueAsString(putBody), ContentType.APPLICATION_JSON));
        client.performRequest(putReq);
        log.info(String.format("Restored snapshot repository '%s' throttle settings", repoName));
    }

    /**
     * Starts a snapshot asynchronously (wait_for_completion=false).
     * When {@code indices} are provided the snapshot is scoped to only those indices,
     * preventing interference with concurrent tests on unrelated indices.
     * Returns the snapshot name that was used.
     */
    public String startSnapshot(String repoName, String snapshotName, String... indices) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        Request req = new Request("PUT", "/_snapshot/" + repoName + "/" + snapshotName);
        req.addParameter("wait_for_completion", "false");

        String body;
        if (indices != null && indices.length > 0) {
            ObjectNode bodyNode = objectMapper.createObjectNode();
            var arrayNode = bodyNode.putArray("indices");
            for (String idx : indices) {
                arrayNode.add(idx);
            }
            bodyNode.put("include_global_state", false);
            body = objectMapper.writeValueAsString(bodyNode);
        } else {
            body = "{}";
        }
        req.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        client.performRequest(req);
        log.info(String.format("Started snapshot '%s' in repository '%s' (indices: %s)",
                snapshotName, repoName, indices != null && indices.length > 0 ? String.join(",", indices) : "all"));
        return snapshotName;
    }

    /**
     * Returns the current state of a snapshot (e.g., "IN_PROGRESS", "SUCCESS").
     * Returns "NOT_FOUND" if the snapshot does not exist.
     */
    public String getSnapshotState(String repoName, String snapshotName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        try {
            // Use the snapshot info endpoint (not _status) — the info endpoint returns
            // standardized states: IN_PROGRESS, SUCCESS, FAILED, PARTIAL.
            // The _status endpoint returns different shard-level states (STARTED, ABORTED, etc.)
            // which do not match the IN_PROGRESS state used by waitForSnapshotInProgress.
            Request req = new Request("GET", "/_snapshot/" + repoName + "/" + snapshotName);
            Response resp = client.performRequest(req);
            String respBody = EntityUtils.toString(resp.getEntity());
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode snapshots = root.get("snapshots");
            if (snapshots != null && snapshots.isArray() && snapshots.size() > 0) {
                return snapshots.get(0).get("state").asText();
            }
            return "UNKNOWN";
        } catch (org.elasticsearch.client.ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return "NOT_FOUND";
            }
            throw e;
        }
    }

    /**
     * Deletes (aborts) a snapshot. Ignores 404 if the snapshot does not exist.
     *
     * <p>Uses {@code wait_for_completion=false} so the request returns immediately after
     * Elasticsearch marks the snapshot as ABORTED, without waiting for the backend
     * cleanup to finish. Callers that need to confirm no active snapshots should
     * poll {@link #hasActiveSnapshots}. To verify the specific snapshot is deleted,
     * use {@link #getSnapshotState(String, String)} and check for "NOT_FOUND".
     *
     * <p>Background: the synchronous DELETE used to carry a 300-second socket timeout
     * because aborting an in-progress snapshot on a throttled Azure repository could
     * block for several minutes while ES cleaned up partial blob files. With
     * {@code wait_for_completion=false} the connection is released immediately and the
     * cleanup proceeds server-side, eliminating the client-side timeout risk entirely.
     */
    public void deleteSnapshot(String repoName, String snapshotName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        try {
            Request req = new Request("DELETE", "/_snapshot/" + repoName + "/" + snapshotName);
            req.addParameter("wait_for_completion", "false");
            client.performRequest(req);
            log.info(String.format("Initiated snapshot deletion for '%s' in repository '%s'", snapshotName, repoName));
        } catch (org.elasticsearch.client.ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                log.info(String.format("Snapshot '%s' not found in '%s', nothing to delete", snapshotName, repoName));
            } else {
                throw e;
            }
        }
    }

    /**
     * Returns true if there is at least one snapshot currently running in the repository.
     * Returns false if the repo doesn't exist or there are no active snapshots.
     */
    public boolean hasActiveSnapshots(String repoName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        try {
            Request req = new Request("GET", "/_snapshot/" + repoName + "/_status");
            Response resp = client.performRequest(req);
            String respBody = EntityUtils.toString(resp.getEntity());
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode snapshots = root.get("snapshots");
            return snapshots != null && snapshots.isArray() && snapshots.size() > 0;
        } catch (org.elasticsearch.client.ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                log.info(String.format("Snapshot repository '%s' not found, treating as no active snapshots", repoName));
                return false;
            }
            throw e;
        }
    }

    /**
     * Ensures the snapshot repository exists, creating it if necessary.
     * <p>
     * If the repository already exists this is a no-op and returns {@code false}.
     * <p>
     * If it is missing (404), the method locates any existing {@code azure}-type repository
     * on the cluster and creates {@code repoName} with the same plugin settings, using a
     * test-specific {@code base_path}. This makes the {@code @snapshot-conflict} integration
     * test self-contained on clusters where the repository has not been pre-provisioned.
     * <p>
     * If an existing {@code azure}-type repository is found on the cluster its container/account
     * settings are cloned (only {@code base_path} is overridden). When the cluster has no
     * existing repositories the ADME standard container name {@code snapshots-{dataPartitionId}}
     * is derived from {@code repoName} (e.g. {@code opendes-primary} → {@code snapshots-opendes}).
     *
     * @return {@code true} if the repository was created by this call, {@code false} if it already existed
     */
    public boolean ensureSnapshotRepository(String repoName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();

        try {
            client.performRequest(new Request("GET", "/_snapshot/" + repoName));
            log.info(String.format("Snapshot repository '%s' already exists", repoName));
            return false;
        } catch (org.elasticsearch.client.ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                throw e;
            }
        }

        log.info(String.format("Snapshot repository '%s' not found — attempting to create it for this test run", repoName));

        // Try to clone settings from an existing azure-type repository on this cluster.
        String allBody;
        try {
            Response allResp = client.performRequest(new Request("GET", "/_snapshot/_all"));
            allBody = EntityUtils.toString(allResp.getEntity());
        } catch (Exception e) {
            throw new IllegalStateException(
                String.format("Snapshot repository '%s' does not exist and /_snapshot/_all failed (%s). " +
                    "Pre-register the repository before running @snapshot-conflict tests.", repoName, e.getMessage()));
        }

        JsonNode allRepos = objectMapper.readTree(allBody);
        ObjectNode repoSettings = null;
        String logSuffix = null;
        java.util.Iterator<Map.Entry<String, JsonNode>> it = allRepos.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            if ("azure".equals(entry.getValue().get("type").asText())) {
                repoSettings = (ObjectNode) entry.getValue().get("settings").deepCopy();
                repoSettings.put("base_path", "integration-test-snapshots/" + repoName);
                logSuffix = String.format("settings cloned from existing repo '%s'", entry.getKey());
                break;
            }
        }

        // No existing azure repo to clone from — derive the container name using the ADME
        // standard naming convention: snapshots-{dataPartitionId}.
        // Repo names follow the pattern {dataPartitionId}-{clusterRole} (e.g. opendes-primary).
        if (repoSettings == null) {
            int lastDash = repoName.lastIndexOf('-');
            String dataPartitionId = lastDash > 0 ? repoName.substring(0, lastDash) : repoName;
            String container = "snapshots-" + dataPartitionId;
            log.info(String.format(
                "No existing azure repo found — creating '%s' using ADME standard container '%s'",
                repoName, container));
            repoSettings = objectMapper.createObjectNode();
            repoSettings.put("container", container);
            repoSettings.put("compress", "true");
            repoSettings.put("base_path", "integration-test-snapshots/" + repoName);
            logSuffix = String.format("ADME standard container '%s'", container);
        }

        Map<String, Object> createBody = new HashMap<>();
        createBody.put("type", "azure");
        createBody.put("settings", objectMapper.convertValue(repoSettings, Map.class));

        Request createReq = new Request("PUT", "/_snapshot/" + repoName);
        createReq.setEntity(new StringEntity(objectMapper.writeValueAsString(createBody), ContentType.APPLICATION_JSON));
        try {
            client.performRequest(createReq);
        } catch (org.elasticsearch.client.ResponseException e) {
            String body = EntityUtils.toString(e.getResponse().getEntity());
            if (e.getResponse().getStatusLine().getStatusCode() == 500 && body.contains("Unable to find client")) {
                throw new IllegalStateException(
                    "SNAPSHOT_NOT_SUPPORTED: Azure snapshot storage client is not configured on this Elasticsearch " +
                    "cluster. The @snapshot-conflict scenario requires an azure-type snapshot repository. " +
                    "Original error: " + body);
            }
            throw e;
        }
        log.info(String.format("Created snapshot repository '%s' (%s)", repoName, logSuffix));
        return true;
    }

    /**
     * Deletes a snapshot repository. Ignores 404 if the repository is already gone.
     */
    public void deleteSnapshotRepository(String repoName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        try {
            client.performRequest(new Request("DELETE", "/_snapshot/" + repoName));
            log.info(String.format("Deleted snapshot repository '%s'", repoName));
        } catch (org.elasticsearch.client.ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                throw e;
            }
            log.info(String.format("Snapshot repository '%s' was already gone", repoName));
        }
    }

    /**
     * Returns {@code true} if the named snapshot repository exists on the cluster.
     * Unlike {@link #ensureSnapshotRepository}, this method never creates the repository
     * and returns {@code false} (rather than throwing) when it is absent.
     */
    public boolean checkSnapshotRepositoryExists(String repoName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        try {
            client.performRequest(new Request("GET", "/_snapshot/" + repoName));
            return true;
        } catch (org.elasticsearch.client.ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Returns the {@code type} field of the named snapshot repository (e.g. {@code "azure"}, {@code "fs"}).
     * Throws {@link IllegalStateException} if the repository does not exist.
     */
    public String getSnapshotRepositoryType(String repoName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        Response resp = client.performRequest(new Request("GET", "/_snapshot/" + repoName));
        String body = EntityUtils.toString(resp.getEntity());
        JsonNode root = objectMapper.readTree(body);
        JsonNode repoNode = root.get(repoName);
        if (repoNode == null) {
            throw new IllegalStateException(String.format(
                "Snapshot repository '%s' not found in GET /_snapshot/%s response", repoName, repoName));
        }
        return repoNode.get("type").asText();
    }

    /**
     * Polls the snapshot state until it reaches a terminal state ({@code SUCCESS}, {@code FAILED},
     * or {@code PARTIAL}) or the timeout elapses.
     *
     * @param repoName        the snapshot repository name
     * @param snapshotName    the snapshot name to wait on
     * @param timeoutSeconds  maximum seconds to wait
     * @return the final state string, or {@code "TIMED_OUT"} if the snapshot was still
     *         {@code IN_PROGRESS} when the deadline was reached
     */
    public String waitForSnapshotCompletion(String repoName, String snapshotName, int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            String state = getSnapshotState(repoName, snapshotName);
            if (!"IN_PROGRESS".equals(state)) {
                log.info(String.format("Snapshot '%s' in repo '%s' reached terminal state: %s",
                    snapshotName, repoName, state));
                return state;
            }
            Thread.sleep(3000);
        }
        log.warning(String.format("Timed out waiting for snapshot '%s' in repo '%s' to complete after %d seconds",
            snapshotName, repoName, timeoutSeconds));
        return "TIMED_OUT";
    }

    /**
     * Returns the names of all SLM policies whose {@code repository} field matches {@code repoName}.
     * Returns an empty list when no SLM policies are configured or none target the given repository.
     */
    public java.util.List<String> getSlmPoliciesForRepository(String repoName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        java.util.List<String> matchingPolicies = new java.util.ArrayList<>();
        try {
            Response resp = client.performRequest(new Request("GET", "/_slm/policy"));
            String body = EntityUtils.toString(resp.getEntity());
            JsonNode root = objectMapper.readTree(body);
            root.fields().forEachRemaining(entry -> {
                JsonNode policy = entry.getValue().path("policy");
                String repository = policy.path("repository").asText(null);
                if (repoName.equals(repository)) {
                    matchingPolicies.add(entry.getKey());
                }
            });
        } catch (org.elasticsearch.client.ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                log.info("No SLM policies found on this cluster (404 from /_slm/policy)");
            } else {
                throw e;
            }
        }
        return matchingPolicies;
    }

    /**
     * Returns the cron schedule string for the given SLM policy
     * (e.g. {@code "0 0 *&#47;4 * * ?"} for every 4 hours).
     * Returns {@code null} if the policy does not exist or has no schedule.
     */
    public String getSlmPolicySchedule(String policyName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        try {
            Response resp = client.performRequest(new Request("GET", "/_slm/policy/" + policyName));
            String body = EntityUtils.toString(resp.getEntity());
            JsonNode root = objectMapper.readTree(body);
            JsonNode scheduleNode = root.path(policyName).path("policy").path("schedule");
            return scheduleNode.isMissingNode() ? null : scheduleNode.asText(null);
        } catch (org.elasticsearch.client.ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Returns the {@code retention.expire_after} value for the given SLM policy (e.g. {@code "30d"}).
     * Returns {@code null} if the policy does not exist or has no retention configured.
     */
    public String getSlmPolicyRetentionExpireAfter(String policyName) throws Exception {
        RestClient client = getOrCreateLowLevelClient();
        try {
            Response resp = client.performRequest(new Request("GET", "/_slm/policy/" + policyName));
            String body = EntityUtils.toString(resp.getEntity());
            JsonNode root = objectMapper.readTree(body);
            JsonNode expireAfterNode = root.path(policyName).path("policy").path("retention").path("expire_after");
            return expireAfterNode.isMissingNode() ? null : expireAfterNode.asText(null);
        } catch (org.elasticsearch.client.ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

}
