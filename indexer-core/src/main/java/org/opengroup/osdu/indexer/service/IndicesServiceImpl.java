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

package org.opengroup.osdu.indexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.CharFilter;
import co.elastic.clients.elasticsearch._types.analysis.CharFilterDefinition;
import co.elastic.clients.elasticsearch._types.analysis.CustomAnalyzer;
import co.elastic.clients.elasticsearch._types.analysis.MappingCharFilter;
import co.elastic.clients.elasticsearch._types.analysis.PatternReplaceCharFilter;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest.Builder;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexSettingsAnalysis;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.lettuce.core.RedisException;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.search.IndexInfo;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.core.common.search.Preconditions;
import org.opengroup.osdu.indexer.cache.partitionsafe.IndexCache;
import org.opengroup.osdu.indexer.util.CustomIndexAnalyzerSetting;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;
import org.opengroup.osdu.indexer.util.TypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static co.elastic.clients.elasticsearch._types.HealthStatus.*;

@Service
@RequestScope
public class IndicesServiceImpl implements IndicesService {

    private static final String INDEX_DELETION_ERROR = "Index deletion error";
    private static final String CLIENT_CANNOT_BE_NULL = "client cannot be null";
    private static final String INDEX_CANNOT_BE_NULL = "index cannot be null";

    private static final Time REQUEST_TIMEOUT = Time.of(builder -> builder.time("1m"));

    @Autowired
    private RequestScopedElasticsearchClient requestScopedClient;
    @Autowired
    private ElasticIndexNameResolver elasticIndexNameResolver;
    @Autowired
    private IndexCache indexCache;
    @Inject
    private IndexAliasService indexAliasService;
    @Autowired
    private JaxRsDpsLog log;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomIndexAnalyzerSetting customIndexAnalyzerSetting;
    @Value("${index.health.retry.threshold:5}")
    private int healthRetryThreshold;
    @Value("${index.health.retry.sleepPeriodInMilliseconds:5000}")
    private int healthRetrySleepPeriodInMilliseconds;

    /**
     * Create a new index in Elasticsearch
     *
     * @param client   Elasticsearch client
     * @param index    Index name
     * @param settings Settings if any, null if no specific settings
     * @param mapping  mapping if any, must be a json map
     * @throws ElasticsearchException,IOException if it cannot create index
     */
    public boolean createIndex(ElasticsearchClient client, String index, IndexSettings settings, Map<String, Object> mapping) throws ElasticsearchException, IOException {
        Preconditions.checkArgument(client, Objects::nonNull, CLIENT_CANNOT_BE_NULL);
        Preconditions.checkArgument(index, Objects::nonNull, INDEX_CANNOT_BE_NULL);
        try {
            CreateIndexRequest.Builder createIndexBuilder = new Builder();
            createIndexBuilder.index(index);
            createIndexBuilder.settings(settings != null ? settings : getDefaultIndexSettings());

            if (mapping != null) {
                Map<String, Map<String, Object>> mappings = Map.of("mappings", mapping);
                String mappingJson = objectMapper.writeValueAsString(mappings);
                createIndexBuilder.withJson(new ByteArrayInputStream(mappingJson.getBytes()));
            }
            createIndexBuilder.timeout(REQUEST_TIMEOUT);
            long startTime = System.currentTimeMillis();

            CreateIndexResponse createIndexResponse = client.indices().create(createIndexBuilder.build());
            long stopTime = System.currentTimeMillis();
            // cache the index status
            boolean indexStatus = createIndexResponse.acknowledged() && createIndexResponse.shardsAcknowledged();
            if (indexStatus) {
                this.indexCache.put(index, true);
                this.log.info(String.format("Time taken to successfully create new index %s : %d milliseconds", index, stopTime - startTime));

                // Create alias for index
                indexAliasService.createIndexAlias(client, elasticIndexNameResolver.getKindFromIndexName(index));
            }

            return indexStatus;
        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_BAD_REQUEST && (e.getMessage().contains("resource_already_exists_exception"))) {
                log.info("Index already exists. Ignoring error...");
                // cache the index status
                this.indexCache.put(index, true);
                return true;
            }
            throw e;
        }
    }

    /**
     * Check if an index already exists
     *
     * @param index Index name
     * @return index details if index already exists
     * @throws IOException if request cannot be processed
     */
    public boolean isIndexExist(ElasticsearchClient client, String index) throws IOException {
        try {
            if (this.indexExistInCache(index)) {
                return true;
            }
            ExistsRequest existsRequest = ExistsRequest.of(builder -> builder.index(index));
            BooleanResponse exists = client.indices().exists(existsRequest);
            if (exists.value()) {
                this.indexCache.put(index, true);
            }
            return exists.value();
        } catch (ElasticsearchException exception) {
            if (exception.status() == HttpStatus.SC_NOT_FOUND) {
                return false;
            }
            throw new AppException(
                    exception.status(),
                    exception.getMessage(),
                    String.format("Error getting index: %s status", index),
                    exception);
        }
    }

    /**
     * Check if an index ready for indexing
     *
     * @param index Index name
     * @return index details if index already exists
     * @throws IOException if request cannot be processed
     */

    public boolean isIndexReady(ElasticsearchClient client, String index) throws IOException {
        try {
            if (this.indexExistInCache(index)) {
                return true;
            }
            boolean isHealthy = isIndexHealthy(client, index);
            if (isHealthy) {
                this.indexCache.put(index, true);
            }
            return isHealthy;
        } catch (ElasticsearchException exception) {
            if (exception.status() == HttpStatus.SC_NOT_FOUND) return false;
            throw new AppException(
                    exception.status(),
                    exception.getMessage(),
                    String.format("Error getting index: %s status", index),
                    exception);
        }
    }

    private boolean isIndexHealthy(ElasticsearchClient client, String index) throws IOException {
        ExistsRequest existsRequest = ExistsRequest.of(builder -> builder.index(index));
        BooleanResponse exists = client.indices().exists(existsRequest);
        if (!exists.value()) {
            return false;
        }
        String actualHealthStatus = null;
        for(int retryCount = 0; retryCount <= healthRetryThreshold; retryCount++) {
            List<IndexInfo> indexHealthInfos = this.getIndexInfo(client, index);
            if (!indexHealthInfos.isEmpty()) {
                actualHealthStatus = indexHealthInfos.get(0).getHealth();
                if (Green.jsonValue().equalsIgnoreCase(actualHealthStatus) || Yellow.jsonValue().equalsIgnoreCase(actualHealthStatus)) {
                    return true;
                }
            }
            sleepInMilliSeconds(healthRetrySleepPeriodInMilliseconds);
        }
        if (Red.jsonValue().equalsIgnoreCase(actualHealthStatus)) {
            throw new AppException(HttpStatus.SC_SERVICE_UNAVAILABLE,
                    "Index not available for indexing",
                    String.format("Index: %s primary shards are unassigned", index));
        }
        return false;
    }

    private void sleepInMilliSeconds(int milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private IndexSettings getDefaultIndexSettings() {
        IndexSettings.Builder builder = new IndexSettings.Builder();
        builder.refreshInterval(Time.of(timeBuilder -> timeBuilder.time("30s")));
        builder.numberOfShards("1");
        builder.numberOfReplicas("1");
        if (customIndexAnalyzerSetting.isEnabled()) {
            IndexSettingsAnalysis analysis = getCustomAnalyzer();
            builder.analysis(analysis);
        }
        return builder.build();
    }

    private IndexSettingsAnalysis getCustomAnalyzer() {
        MappingCharFilter mappingCharFilter = new MappingCharFilter.Builder().mappings("_=>\\u0020").build();
        PatternReplaceCharFilter patternReplaceCharFilter = new PatternReplaceCharFilter.Builder().pattern("(\\D)\\.|\\.(?=\\D)").replacement("$1 ").build();

        Map<String, CharFilter> charFilterMap = new HashMap<>();
        charFilterMap.put("osdu_mapping_charFilter_for_underscore", new CharFilter.Builder()
                .definition(new CharFilterDefinition.Builder().mapping(mappingCharFilter).build())
                .build());
        charFilterMap.put("osdu_patternReplace_charFilter_for_dot", new CharFilter.Builder()
                .definition(new CharFilterDefinition.Builder().patternReplace(patternReplaceCharFilter).build())
                .build());
        Analyzer analyzer = new CustomAnalyzer.Builder()
                .tokenizer("standard")
                .filter("lowercase")
                .charFilter(charFilterMap.keySet().stream().toList())
                .build()
                ._toAnalyzer();
        IndexSettingsAnalysis analysis = new IndexSettingsAnalysis.Builder()
                .charFilter(charFilterMap)
                .analyzer(TypeMapper.OSDU_CUSTOM_ANALYZER, analyzer)
                .build();
        return analysis;
    }

    private boolean indexExistInCache(String index) {
        try {
            Boolean isIndexExist = this.indexCache.get(index);
            if (isIndexExist != null && isIndexExist) return true;
        } catch (RedisException ex) {
            //In case the format of cache changes then clean the cache
            this.indexCache.delete(index);
        }
        return false;
    }

    /**
     * Deletes index if user has required role: search.admin
     *
     * @param client Elasticsearch client
     * @param index  Index name
     */
    public boolean deleteIndex(ElasticsearchClient client, String index) throws ElasticsearchException, IOException, AppException {
        List<String> indices = this.resolveIndex(client, index);
        boolean responseStatus = true;
        for (String idx : indices) {
            responseStatus &= removeIndexInElasticsearch(client, idx);
        }
        if (responseStatus) {
            this.clearCacheOnIndexDeletion(index);
        }
        return responseStatus;
    }

    /**
     * Deletes index if user has required role: search.admin
     *
     * @param index Index name
     */
    public boolean deleteIndex(String index) throws ElasticsearchException, IOException, AppException {
        ElasticsearchClient client = requestScopedClient.getClient();
        return deleteIndex(client, index);
    }

    /**
     * Remove index in Elasticsearch
     *
     * @param client Elasticsearch client
     * @param index  Index name
     * @throws Exception Throws {@link AppException} if index is not found or elastic cannot delete the index
     */
    private boolean removeIndexInElasticsearch(ElasticsearchClient client, String index) throws ElasticsearchException, IOException, AppException {
        Preconditions.checkArgument(client, Objects::nonNull, CLIENT_CANNOT_BE_NULL);
        Preconditions.checkArgument(index, Objects::nonNull, INDEX_CANNOT_BE_NULL);

        try {
            DeleteIndexRequest.Builder deleteRequestBuilder = new DeleteIndexRequest.Builder();
            deleteRequestBuilder.index(index);
            deleteRequestBuilder.timeout(REQUEST_TIMEOUT);
            DeleteIndexResponse deleteIndexResponse = client.indices().delete(deleteRequestBuilder.build());
            if (!deleteIndexResponse.acknowledged()) {
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, INDEX_DELETION_ERROR, String.format("Could not delete index %s", index));
            }
            return true;
        } catch (ElasticsearchException exception) {
            if (exception.status() == HttpStatus.SC_NOT_FOUND) {
                throw new AppException(HttpStatus.SC_NOT_FOUND, INDEX_DELETION_ERROR, notFoundErrorMessage(index), exception);
            } else if (exception.status() == HttpStatus.SC_BAD_REQUEST && exception.getMessage().contains("Cannot delete indices that are being snapshotted")) {
                throw new AppException(HttpStatus.SC_CONFLICT, INDEX_DELETION_ERROR, "Unable to delete the index because it is currently locked. Try again in few minutes.", exception);
            }
            throw exception;
        }
    }

    // cron may not have kind but index delete api may
    private String notFoundErrorMessage(String index) {
        String kind = this.elasticIndexNameResolver.getKindFromIndexName(index);
        return Strings.isNullOrEmpty(kind) ? String.format("Index %s not found", index) : String.format("Kind %s not found", kind);
    }

    /**
     * Get index information from Elasticsearch
     *
     * @param client       Elasticsearch client
     * @param indexPattern Index pattern
     * @return List of indices matching indexPattern
     * @throws IOException Throws {@link IOException} if elastic cannot complete the request
     */
    public List<IndexInfo> getIndexInfo(ElasticsearchClient client, String indexPattern) throws IOException {
        Objects.requireNonNull(client, CLIENT_CANNOT_BE_NULL);

        String requestUrl = (indexPattern == null || indexPattern.isEmpty())
                ? "/_cat/indices/*,-.*?h=index,health,docs.count,creation.date&s=docs.count:asc&format=json"
                : String.format("/_cat/indices/%s?h=index,health,docs.count,creation.date&format=json", indexPattern);

    	RestClientTransport clientTransport = (RestClientTransport) client._transport();
    	Request request = new Request("GET", requestUrl);
        Response response = clientTransport.restClient().performRequest(request);
        String responseBody = EntityUtils.toString(response.getEntity());

        Type typeOf = new TypeToken<List<IndexInfo>>() {
        }.getType();
        return new Gson().fromJson(responseBody, typeOf);
    }

    private void clearCacheOnIndexDeletion(String index) {
        final String syncCacheKey = String.format("metaAttributeMappingSynced-%s", index);
        this.indexCache.delete(index);
        this.indexCache.delete(syncCacheKey);
    }

    public List<String> resolveIndex(ElasticsearchClient client, String index) throws IOException {

        Preconditions.checkArgument(client, Objects::nonNull, CLIENT_CANNOT_BE_NULL);
        Preconditions.checkArgument(index, Objects::nonNull, INDEX_CANNOT_BE_NULL);

        try {
            GetIndexRequest request = new GetIndexRequest.Builder().index(index).build();
            GetIndexResponse getIndexResponse = client.indices().get(request);

            String[] indices = getIndexResponse.result().keySet().toArray(new String[0]);
            if (indices.length != 0) {
                return Arrays.asList(indices);
            } else {
                throw new AppException(HttpStatus.SC_NOT_FOUND, "Index resolving error", notFoundErrorMessage(index));
            }
        } catch (ElasticsearchException exception) {
            if (exception.status() == HttpStatus.SC_NOT_FOUND) {
                throw new AppException(HttpStatus.SC_NOT_FOUND, "Index resolving error", notFoundErrorMessage(index), exception);
            }
            throw exception;
        }
    }
}
