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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.GeoShapeRelation;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.client.util.Strings;
import com.google.gson.Gson;
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
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.models.record.RecordData;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.opengroup.osdu.common.RecordSteps.X_COLLABORATION;


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
    private ElasticsearchClient elasticsearchClient;

    public ElasticUtils() {
        this.username = Config.getElastic8UserName();
        this.password = Config.getElastic8Password();
        this.host = Config.getElastic8Host();
        this.sslEnabled = Config.isElastic8SslEnabled();
    }

    public void createIndex(String index, String mapping) {
        try {
            ElasticsearchClient client = this.getOrCreateClient(username, password, host);

            IndexSettings indexSettings = IndexSettings.of(builder ->
                builder.numberOfShards("1")
                    .numberOfReplicas("1")
            );

            // creating index + add mapping to the index
            log.info("Creating index with name: " + index);
            CreateIndexRequest request = CreateIndexRequest.of(builder ->
                builder.index(index)
                    .settings(indexSettings)
                    .mappings(TypeMapping.of(
                        mappingBuilder -> mappingBuilder.withJson(new StringReader(mapping))))
                    .timeout(REQUEST_TIMEOUT)
            );

            CreateIndexResponse createIndexResponse = client.indices().create(request);

            //wait for ack
            for (int i = 0; ; i++) {
                if (createIndexResponse.acknowledged() && createIndexResponse.shardsAcknowledged()) {
                    break;
                } else {
                    log.info("Failed to get confirmation from elastic server, will sleep for 15 seconds");
                    Thread.sleep(15000);
                    if (i > 3) {
                        log.info("Failed to get confirmation from elastic server after 3 retries");
                        throw new AssertionError("Failed to get confirmation from Elastic cluster");
                    }
                }
            }

            log.info("Done creating index with name: " + index);

        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_BAD_REQUEST &&
                (e.getMessage().contains("resource_already_exists_exception") || e.getMessage().contains("IndexAlreadyExistsException"))) {
                log.info("Index already exists. Ignoring error...");
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
        try{
            //retry if the elastic cluster is snapshotting and we cant delete it
            for (int retries = 0; ; retries++) {
                try {
                    log.info("Deleting index with name: " + index + ", retry count: " + retries);
                    DeleteIndexRequest request =  DeleteIndexRequest.of(builder -> builder.index(index));
                    client.indices().delete(request);
                    log.info("Done deleting index with name: " + index);
                    return;
                } catch (ElasticsearchException e) {
                    if (e.status() == HttpStatus.SC_NOT_FOUND) {
                        return;
                    } else if (e.getMessage().contains("Cannot delete indices that are being snapshotted")) {
                        closeIndex(client, index);
                        log.info(String.format("skipping %s index delete, as snapshot is being run, closing the index instead", index));
                        return;
                    } else if (retries < 4) {
                        log.info("Retrying to delete index due to following error: " + e.getMessage());
                        try {
                            Thread.sleep(12000);
                        } catch (InterruptedException e1) {
                            log.warning("Interrupted during index deletion wait: " + e1.getMessage());
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        closeIndex(client, index);
                        log.info(String.format("maximum retries: %s reached for index: %s delete, closing the index instead", retries, index));
                    }
                }
            }
        } catch (IOException e) {
            throw new AssertionError(e.getMessage());
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

    public boolean waitForIndexReady(String index, int timeoutSeconds) throws IOException {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            co.elastic.clients.elasticsearch.cluster.HealthRequest healthRequest = 
                co.elastic.clients.elasticsearch.cluster.HealthRequest.of(b -> b
                    .index(index)
                    .waitForStatus(co.elastic.clients.elasticsearch._types.HealthStatus.Yellow)
                    .timeout(Time.of(t -> t.time(timeoutSeconds + "s"))));
            co.elastic.clients.elasticsearch.cluster.HealthResponse response = client.cluster().health(healthRequest);
            return response.status() == co.elastic.clients.elasticsearch._types.HealthStatus.Green 
                || response.status() == co.elastic.clients.elasticsearch._types.HealthStatus.Yellow;
        } catch (ElasticsearchException e) {
            log.info(String.format("Health check failed for index %s: %s", index, e.getMessage()));
            return false;
        }
    }

    public boolean aliasExists(String aliasName) throws IOException {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            co.elastic.clients.elasticsearch.indices.ExistsAliasRequest request = 
                co.elastic.clients.elasticsearch.indices.ExistsAliasRequest.of(b -> b.name(aliasName));
            return client.indices().existsAlias(request).value();
        } catch (ElasticsearchException e) {
            log.info(String.format("Alias check failed for %s: %s", aliasName, e.getMessage()));
            return false;
        }
    }

    public String getPhysicalIndexFromAlias(String aliasName) throws IOException {
        ElasticsearchClient client = this.getOrCreateClient(username, password, host);
        try {
            co.elastic.clients.elasticsearch.indices.GetAliasRequest request = 
                co.elastic.clients.elasticsearch.indices.GetAliasRequest.of(b -> b.name(aliasName));
            co.elastic.clients.elasticsearch.indices.GetAliasResponse response = client.indices().getAlias(request);
            return response.result().keySet().stream().findFirst().orElse(null);
        } catch (ElasticsearchException e) {
            log.info(String.format("Get alias failed for %s: %s", aliasName, e.getMessage()));
            return null;
        }
    }

    public boolean physicalIndexExists(String indexName) throws IOException {
        return isIndexExist(indexName);
    }

    public boolean checkMappingFieldsExist(String indexName, String[] fieldNames) throws IOException {
        try {
            Map<String, IndexMappingRecord> mapping = getMapping(indexName);
            if (mapping == null || mapping.isEmpty()) return false;
            IndexMappingRecord record = mapping.values().iterator().next();
            if (record.mappings() == null || record.mappings().properties() == null) return false;
            Map<String, ?> props = record.mappings().properties();
            for (String field : fieldNames) {
                if (!props.containsKey(field)) return false;
            }
            return true;
        } catch (Exception e) {
            log.info(String.format("Mapping fields check failed for %s: %s", indexName, e.getMessage()));
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
                String rawString = String.format("%s:%s", username, password);

                RestClientBuilder builder = createClientBuilder(host, rawString, port);
                RestClientTransport transport = new RestClientTransport(builder.build(), new JacksonJsonpMapper());
                restHighLevelClient = new ElasticsearchClient(transport);

            } catch (Exception e) {
                throw new AssertionError("Setup elastic error: %s" + e.getMessage());
            }
            this.elasticsearchClient = restHighLevelClient;
        }
        return this.elasticsearchClient;
    }

    public RestClientBuilder createClientBuilder(String url, String usernameAndPassword, int port) throws Exception {
            String scheme = this.sslEnabled ? "https" : "http";

            url = url.trim().replaceAll("^(?i)(https?)://","");
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
                new BasicHeader("Authorization", String.format("Basic %s", Base64.getEncoder().encodeToString(usernameAndPassword.getBytes()))),
            };

        boolean isSecurityHttpsCertificateTrust = Config.isSecurityHttpsCertificateTrust();
        log.info(String.format(
            "Elastic client connection uses protocolScheme = %s with a flag "
                + "'security.https.certificate.trust' = %s",
            scheme, isSecurityHttpsCertificateTrust));

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

}
