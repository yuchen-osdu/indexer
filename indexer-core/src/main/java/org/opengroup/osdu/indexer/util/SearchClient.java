/*
 * Copyright © Schlumberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.indexer.util;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.*;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ClosePointInTimeRequest;
import co.elastic.clients.elasticsearch.core.OpenPointInTimeRequest;
import co.elastic.clients.elasticsearch.core.OpenPointInTimeResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import com.google.gson.Gson;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.search.RecordMetaAttribute;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.indexer.model.SearchRecord;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SearchClient {

    @Inject
    private ElasticClientHandler elasticClientHandler;

    @Inject
    private ElasticIndexNameResolver elasticIndexNameResolver;

    @Inject
    private JaxRsDpsLog logger;
    
    @Inject
    private RequestScopedElasticsearchClient requestScopedClient;

    private final static int MAX_PAGE_SIZE = 5000; //5k
    private final static int MAX_RECORDS_COUNT = Integer.MAX_VALUE;
    private final Time SEARCH_TIMEOUT = Time.of(t -> t.time("120s"));

    private final int MAX_SEARCH_RETRY = 3;
    private final long BACKOFF_TIME_UNIT = 1000; //ms


    // if returnedField contains property matching from excludes than query result will NOT include that property
    private final Set<String> excludes =
            new HashSet<>(Collections.singletonList(RecordMetaAttribute.X_ACL.getValue()));

    // queryableExcludes properties can be returned by query results
    private final Set<String> queryableExcludes =
            new HashSet<>(Collections.singletonList(RecordMetaAttribute.INDEX_STATUS.getValue()));

    private final Gson gson = new Gson();

    public List<SearchRecord> search(String kind, Query query, List<SortOptions> sortOptions, List<String> returnedFields, int limit) throws Exception {
        if(StringUtils.isEmpty(kind)) {
            throw new Exception("kind can't be null or empty");
        }
        return search(List.of(kind), query, sortOptions, returnedFields, limit);
    }

    public List<SearchRecord> search(List<String> kinds, Query query, List<SortOptions> sortOptions, List<String> returnedFields, int limit) throws Exception {
        if(kinds == null || kinds.isEmpty()) {
            throw new Exception("kinds can't be null or empty");
        }
        if(query == null) {
            throw new Exception("query can't be null");
        }

        ElasticsearchClient client = this.requestScopedClient.getClient();
        String index = this.getIndex(kinds);
        limit = (limit <= 0)? MAX_RECORDS_COUNT : limit;
        int pageSize = (limit > MAX_PAGE_SIZE)? MAX_PAGE_SIZE: limit;

        // Use normal query without pagination
        List<SearchRecord> records = query(client, index, query, sortOptions, returnedFields, pageSize);
        if(records.size() < MAX_PAGE_SIZE || records.size() >= limit) {
            return records;
        }

        // Use search_after and PIT to do pagination if the number of records is larger than one page size
        String pitId = null;
        try {
            pitId = openPointInTime(client, index);
            return queryWithPit(client, pitId, query, sortOptions, returnedFields, pageSize, limit);
        }
        finally {
            if(pitId != null) {
                closePointInTime(client, pitId);
            }
        }
    }

    private List<SearchRecord> query(ElasticsearchClient client, String index, Query query, List<SortOptions> sortOptions, List<String> returnedFields, int pageSize) throws Exception {
        // Build the SearchRequest
        SearchRequest.Builder queryBuilder = createSearchBuilder(query, sortOptions, returnedFields, pageSize);
        SearchRequest elasticSearchRequest = queryBuilder
                .index(index)
                .from(0)
                .allowNoIndices(true)
                .expandWildcards(ExpandWildcard.Open, ExpandWildcard.Closed)
                .ignoreUnavailable(true)
                .ignoreThrottled(true)
                .ccsMinimizeRoundtrips(true)
                .build();

        // Execute
        SearchResponse<Map<String, Object>> searchResponse = searchWithRetry(client, elasticSearchRequest);

        // Convert SearchResponse
        List<SearchRecord> results = getSearchRecords(searchResponse);

        return results;
    }

    private List<SearchRecord> queryWithPit(ElasticsearchClient client, String pitId, Query query, List<SortOptions> sortOptions, List<String> returnedFields, int pageSize, int limit) throws Exception {
        List<SearchRecord> results = new ArrayList<>();
        List<FieldValue> fieldValues = null;
        // SortOptions can't be null or empty in search with search_after and PIT
        // Otherwise, the returned fieldValues will be empty and cause infinite loop
        if(sortOptions == null || sortOptions.isEmpty()) {
            sortOptions = new ArrayList<>();
            sortOptions.add(SortOptions.of(so -> so.score(s -> s.order(SortOrder.Desc))));
        }
        while(true) {
            // Build the SearchRequest
            SearchRequest.Builder queryBuilder = createSearchBuilder(query, sortOptions, returnedFields, pageSize);
            if(fieldValues != null) {
                queryBuilder.searchAfter(fieldValues);
            }
            SearchRequest elasticSearchRequest = queryBuilder
                    .pit(pit -> pit.id(pitId).keepAlive(SEARCH_TIMEOUT))
                    .build();

            // Execute
            SearchResponse<Map<String, Object>> searchResponse = searchWithRetry(client, elasticSearchRequest);

            // Convert SearchResponse
            List<SearchRecord> batch = getSearchRecords(searchResponse);
            results.addAll(batch);

            if(batch.size() < pageSize || results.size() >= limit) {
                break; // Done
            }
            else {
                // Prepare for next page
                HitsMetadata<Map<String, Object>> searchHits = searchResponse.hits();
                int length = searchHits.hits().size();
                fieldValues = searchHits.hits().get(length -1).sort();
                if(results.size() + pageSize > limit) {
                    pageSize = limit - results.size();
                }
            }
        }

        return results;
    }

    private SearchResponse<Map<String, Object>> searchWithRetry(ElasticsearchClient client, SearchRequest elasticSearchRequest) throws IOException, ElasticsearchException {
        int nTry = 0;

        SearchResponse<Map<String, Object>> results = null;
        while(nTry <= MAX_SEARCH_RETRY) {
            nTry++;
            try {
                results = client.search(elasticSearchRequest, (Type) Map.class);
                break;
            } catch (ElasticsearchException e) {
                int statusCode = e.status();
                if (nTry <= MAX_SEARCH_RETRY && (statusCode == 408 || statusCode == 429 || statusCode >= 500)) {
                    logger.debug(String.format("Retry search %d times with status code %d", nTry, statusCode));
                    doExponentialBackOff(nTry);
                }
                else {
                    throw e;
                }
            } catch (IOException e) {
                if (nTry <= MAX_SEARCH_RETRY) {
                    logger.debug(String.format("Retry search %d times because of IOException", nTry));
                    doExponentialBackOff(nTry);
                }
                else {
                    throw e;
                }
            }
        }
        if(results == null) {
            results = new SearchResponse.Builder<Map<String, Object>>().build();
        }
        return results;
    }

    private void doExponentialBackOff(int factor) {
        try {
            Thread.sleep(((long)Math.pow(2, factor)) * BACKOFF_TIME_UNIT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private SearchRequest.Builder createSearchBuilder(Query query, List<SortOptions> sortOptions, List<String> returnedFields, int pageSize) {
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                                                        .query(query)
                                                        .size(pageSize)
                                                        .searchType(SearchType.QueryThenFetch)
                                                        .batchedReduceSize(512L);
        if(sortOptions != null) {
            searchBuilder.sort(sortOptions);
        }

        // set the return fields
        if (returnedFields == null) {
            returnedFields = new ArrayList<>();
        }
        Set<String> returnedFieldsSet = new HashSet<>(returnedFields);
        // remove all matching returnedField and queryable from excludes
        Set<String> requestQueryableExcludes = new HashSet<>(queryableExcludes);
        Set<String> requestExcludes = new HashSet<>(excludes);
        requestQueryableExcludes.removeAll(returnedFields);
        requestExcludes.addAll(requestQueryableExcludes);
        searchBuilder.source(SourceConfig.of(
                sc ->sc.filter(
                        f ->f.includes(returnedFieldsSet.stream().toList())
                                .excludes(requestExcludes.stream().toList()))));
        return searchBuilder;
    }

    private List<SearchRecord> getSearchRecords(
            ResponseBody<Map<String, Object>> searchResponse) {
        List<SearchRecord> results = new ArrayList<>();
        HitsMetadata<Map<String, Object>> searchHits = searchResponse.hits();
        if (searchHits.hits() != null) {
            for (Hit<Map<String, Object>> hit : searchHits.hits()) {
                Map<String, Object> hitFields = hit.source();
                results.add(gson.fromJson(gson.toJson(hitFields), SearchRecord.class));
            }
        }
        return results;
    }

    private String getIndex(List<String> kinds) {
        List<String> indices = new ArrayList<>();
        for(String kind : kinds) {
            String index = this.elasticIndexNameResolver.getIndexNameFromKind(kind);
            indices.add(index);
        }
        return String.join(",", indices);
    }

    private String openPointInTime(ElasticsearchClient client, String index) throws IOException {
        OpenPointInTimeRequest openRequest = OpenPointInTimeRequest.of(builder ->
                builder.index(index)
                        .ignoreUnavailable(true)
                        .keepAlive(SEARCH_TIMEOUT));
        OpenPointInTimeResponse openResponse = client.openPointInTime(openRequest);
        return openResponse.id();
    }

    private void closePointInTime(ElasticsearchClient client, String pitId) {
        ClosePointInTimeRequest closeRequest = ClosePointInTimeRequest.of(builder -> builder.id(pitId));
        try {
            client.closePointInTime(closeRequest);
        }
        catch(Exception ex) {
            logger.warning("Failed to close point in time", ex);
        }
    }

}
