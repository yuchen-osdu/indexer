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
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.indexer.model.SearchRecord;

import java.lang.reflect.Type;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SearchClientTest {

    @InjectMocks
    SearchClient sut;

    @Mock
    private RequestScopedElasticsearchClient requestScopedClient;

    @Mock
    private ElasticIndexNameResolver elasticIndexNameResolver;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private ElasticsearchClient client;

    @Mock
    private Query query;

    @Mock
    private HitsMetadata<Map<String, Object>> searchHits;

    @Mock
    private Hit<Map<String, Object>> searchHit;

    private static final String kind = "a:b:c:1.0.0";

    private static final String pitId = "pitId";

    @Before
    public void init() {
        doReturn(client).when(requestScopedClient).getClient();
        when(elasticIndexNameResolver.getIndexNameFromKind(anyString())).thenAnswer(invocation -> {
            String kind = invocation.getArgument(0);
            return kind.replace(":", "-").toLowerCase();
        });
    }

    @Test
    public void search_with_normalQuery_whenSearchHitsIsNotEmpty() throws Exception {
        List<Hit<Map<String, Object>>> hits = new ArrayList<>();
        hits.add(searchHit);
        Map<String, Object> hitFields = new HashMap<>();

        SearchResponse searchResponse = mock(SearchResponse.class);
        doReturn(searchResponse).when(client).search(any(SearchRequest.class), eq((Type)Map.class));
        doReturn(searchHits).when(searchResponse).hits();
        doReturn(hits).when(searchHits).hits();
        doReturn(hitFields).when(searchHit).source();

        // act
        List<SearchRecord> records = sut.search(kind, query, null, null, -1);

        // assert
        ArgumentCaptor<SearchRequest> searchRequestArgumentCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(1)).search(searchRequestArgumentCaptor.capture(), eq((Type)Map.class));
        verify(client, times(0)).openPointInTime(any(OpenPointInTimeRequest.class));
        SearchRequest searchRequest = searchRequestArgumentCaptor.getValue();
        assertNull(searchRequest.pit());
        assertEquals(searchRequest.sort().size(), 0);
        assertEquals(searchRequest.source().filter().includes().size(), 0);
        assertEquals(records.size(), 1);
    }

    @Test
    public void search_with_normalQuery_and_retry_when_few_exceptions() throws Exception {
        List<Hit<Map<String, Object>>> hits = new ArrayList<>();
        hits.add(searchHit);
        Map<String, Object> hitFields = new HashMap<>();

        SearchResponse searchResponse = mock(SearchResponse.class);
        doReturn(searchHits).when(searchResponse).hits();
        doReturn(hits).when(searchHits).hits();
        doReturn(hitFields).when(searchHit).source();

        List<ErrorResponse> errorResponses = new ArrayList<>();
        errorResponses.add(ErrorResponse.of(es -> es.status(408).error(ErrorCause.of(ec -> ec.causedBy(by -> by.type("Exception").reason("Request Timeout"))))));
        errorResponses.add(ErrorResponse.of(es -> es.status(429).error(ErrorCause.of(ec -> ec.causedBy(by -> by.type("Exception").reason("Too Many Requests"))))));
        errorResponses.add(ErrorResponse.of(es -> es.status(500).error(ErrorCause.of(ec -> ec.causedBy(by -> by.type("Exception").reason("Internal Server Error"))))));
        when(client.search(any(SearchRequest.class), eq((Type)Map.class))).thenAnswer(invocationOnMock -> {
            if(!errorResponses.isEmpty()) {
                ErrorResponse errorResponse = errorResponses.remove(0);
                throw new ElasticsearchException("bala", errorResponse);
            }
            else {
                return searchResponse;
            }
        });

        // act
        long startTime = System.currentTimeMillis();
        List<SearchRecord> records = sut.search(kind, query, null, null, -1);
        long seconds = (System.currentTimeMillis() - startTime)/1000;

        // assert
        ArgumentCaptor<SearchRequest> searchRequestArgumentCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(4)).search(searchRequestArgumentCaptor.capture(), eq((Type)Map.class));
        verify(client, times(0)).openPointInTime(any(OpenPointInTimeRequest.class));
        SearchRequest searchRequest = searchRequestArgumentCaptor.getValue();
        assertNull(searchRequest.pit());
        assertEquals(searchRequest.sort().size(), 0);
        assertEquals(searchRequest.source().filter().includes().size(), 0);
        assertEquals(records.size(), 1);
        assertEquals(seconds, 14);
    }

    @Test
    public void search_with_normalQuery_and_retry_when_too_many_exceptions() throws Exception {
        SearchResponse searchResponse = mock(SearchResponse.class);
        List<ErrorResponse> errorResponses = new ArrayList<>();
        errorResponses.add(ErrorResponse.of(es -> es.status(408).error(ErrorCause.of(ec -> ec.causedBy(by -> by.type("Exception").reason("Request Timeout"))))));
        errorResponses.add(ErrorResponse.of(es -> es.status(429).error(ErrorCause.of(ec -> ec.causedBy(by -> by.type("Exception").reason("Too Many Requests"))))));
        errorResponses.add(ErrorResponse.of(es -> es.status(500).error(ErrorCause.of(ec -> ec.causedBy(by -> by.type("Exception").reason("Internal Server Error"))))));
        errorResponses.add(ErrorResponse.of(es -> es.status(500).error(ErrorCause.of(ec -> ec.causedBy(by -> by.type("Exception").reason("Internal Server Error"))))));
        when(client.search(any(SearchRequest.class), eq((Type)Map.class))).thenAnswer(invocationOnMock -> {
            if(!errorResponses.isEmpty()) {
                ErrorResponse errorResponse = errorResponses.remove(0);
                throw new ElasticsearchException("bala", errorResponse);
            }
            else {
                return searchResponse;
            }
        });

        // act
        long startTime = System.currentTimeMillis();
        Assert.assertThrows(Exception.class, () -> sut.search(kind, query, null, null, -1));
        long seconds = (System.currentTimeMillis() - startTime)/1000;

        // assert
        verify(client, times(4)).search(any(SearchRequest.class), eq((Type)Map.class));
        verify(client, times(0)).openPointInTime(any(OpenPointInTimeRequest.class));
        assertEquals(seconds, 14);
    }

    @Test
    public void search_with_normalQuery_and_without_retry_when_bad_request_exception() throws Exception {
        SearchResponse searchResponse = mock(SearchResponse.class);
        List<ErrorResponse> errorResponses = new ArrayList<>();
        errorResponses.add(ErrorResponse.of(es -> es.status(400).error(ErrorCause.of(ec -> ec.causedBy(by -> by.type("Exception").reason("Bad Request"))))));
        when(client.search(any(SearchRequest.class), eq((Type)Map.class))).thenAnswer(invocationOnMock -> {
            if(!errorResponses.isEmpty()) {
                ErrorResponse errorResponse = errorResponses.remove(0);
                throw new ElasticsearchException("bala", errorResponse);
            }
            else {
                return searchResponse;
            }
        });

        // act
        long startTime = System.currentTimeMillis();
        Assert.assertThrows(Exception.class, () -> sut.search(kind, query, null, null, -1));
        long seconds = (System.currentTimeMillis() - startTime)/1000;

        // assert
        verify(client, times(1)).search(any(SearchRequest.class), eq((Type)Map.class));
        verify(client, times(0)).openPointInTime(any(OpenPointInTimeRequest.class));
        assertEquals(seconds, 0);
    }


    @Test
    public void search_with_whenSortOptionsAndReturnedFieldsAreNotEmpty() throws Exception {
        List<Hit<Map<String, Object>>> hits = new ArrayList<>();
        hits.add(searchHit);
        Map<String, Object> hitFields = new HashMap<>();

        SearchResponse searchResponse = mock(SearchResponse.class);
        doReturn(searchResponse).when(client).search(any(SearchRequest.class), eq((Type)Map.class));
        doReturn(searchHits).when(searchResponse).hits();
        doReturn(hits).when(searchHits).hits();
        doReturn(hitFields).when(searchHit).source();
        List<SortOptions> sortOptions = List.of(SortOptions.of(so -> so.score(s -> s.order(SortOrder.Desc))));
        List<String> returnedFields = List.of("id", "kind");

        // act
        List<SearchRecord> records = sut.search(kind, query, sortOptions, returnedFields, -1);

        // assert
        ArgumentCaptor<SearchRequest> searchRequestArgumentCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(1)).search(searchRequestArgumentCaptor.capture(), eq((Type)Map.class));
        verify(client, times(0)).openPointInTime(any(OpenPointInTimeRequest.class));
        SearchRequest searchRequest = searchRequestArgumentCaptor.getValue();
        assertEquals(searchRequest.sort(), sortOptions);
        assertEquals(searchRequest.source().filter().includes().size(), 2);
        assertTrue(searchRequest.source().filter().includes().contains("id"));
        assertTrue(searchRequest.source().filter().includes().contains("kind"));
        assertNull(searchRequest.pit());
        assertEquals(records.size(), 1);
    }

    @Test
    public void search_with_queryWithPIT_whenSortOptionsIsNotEmpty() throws Exception {
        List<SortOptions> sortOptions = List.of(SortOptions.of(so -> so.score(s -> s.order(SortOrder.Desc))));
        List<String> returnedFields = List.of("id", "kind");

        int totalRecordCount = 5100;
        prepare_search_with_queryWithPIT(totalRecordCount);

        // act
        List<SearchRecord> records = sut.search(kind, query, sortOptions, returnedFields, -1);

        // assert
        ArgumentCaptor<SearchRequest> searchRequestArgumentCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(3)).search(searchRequestArgumentCaptor.capture(), eq((Type)Map.class));
        verify(client, times(1)).openPointInTime(any(OpenPointInTimeRequest.class));
        verify(client, times(1)).closePointInTime(any(ClosePointInTimeRequest.class));
        SearchRequest searchRequest = searchRequestArgumentCaptor.getValue();
        assertEquals(searchRequest.sort(), sortOptions);
        assertEquals(searchRequest.source().filter().includes().size(), 2);
        assertTrue(searchRequest.source().filter().includes().contains("id"));
        assertTrue(searchRequest.source().filter().includes().contains("kind"));
        assertEquals(searchRequest.pit().id(), pitId);
        assertEquals(records.size(), totalRecordCount);
    }

    @Test
    public void search_with_queryWithPIT_whenSortOptionsIsEmpty() throws Exception {
        List<String> returnedFields = List.of("id", "kind");

        int totalRecordCount = 15000;
        prepare_search_with_queryWithPIT(totalRecordCount);

        // act
        List<SearchRecord> records = sut.search(kind, query, null, returnedFields, -1);

        // assert
        ArgumentCaptor<SearchRequest> searchRequestArgumentCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(5)).search(searchRequestArgumentCaptor.capture(), eq((Type)Map.class));
        verify(client, times(1)).openPointInTime(any(OpenPointInTimeRequest.class));
        verify(client, times(1)).closePointInTime(any(ClosePointInTimeRequest.class));
        SearchRequest searchRequest = searchRequestArgumentCaptor.getValue();
        List<SortOptions> sortOptions = searchRequest.sort();
        assertEquals(sortOptions.size(), 1);
        assertEquals(searchRequest.source().filter().includes().size(), 2);
        assertTrue(searchRequest.source().filter().includes().contains("id"));
        assertTrue(searchRequest.source().filter().includes().contains("kind"));
        assertEquals(searchRequest.pit().id(), pitId);
        assertEquals(records.size(), totalRecordCount);
    }

    private void prepare_search_with_queryWithPIT(int totalRecordCount) throws Exception {
        List<List<Hit<Map<String, Object>>>> batches = new ArrayList<>();
        List<Hit<Map<String, Object>>> hits = new ArrayList<>();
        for(int i = 0; i < totalRecordCount; i++) {
            if(i % 5000 == 0 && i > 0) {
                batches.add(hits);
                hits = new ArrayList<>();
            }
            hits.add(searchHit);
        }
        if(!hits.isEmpty())
            batches.add(hits);
        int batchCount = batches.size();

        Map<String, Object> hitFields = new HashMap<>();
        Map<String, Integer> searchCallsCount = new HashMap<>();
        searchCallsCount.put("Count", 0);
        List<FieldValue> fieldValues = new ArrayList<>();

        OpenPointInTimeResponse openResponse = mock(OpenPointInTimeResponse.class);
        SearchResponse searchResponse = mock(SearchResponse.class);
        doReturn(openResponse).when(client).openPointInTime(any(OpenPointInTimeRequest.class));
        when(client.search(any(SearchRequest.class), eq((Type)Map.class))).thenAnswer(
                invocationOnMock -> {
                    searchCallsCount.put("Count", searchCallsCount.get("Count") + 1);
                    return searchResponse;
                });
        doReturn(pitId).when(openResponse).id();
        doReturn(searchHits).when(searchResponse).hits();
        when(searchHits.hits()).thenAnswer(invocation -> {
            // First call is normal query
            // The second and the rest of the calls are queries with search_after and PIT
            int searchCalls = searchCallsCount.get("Count");
            if(searchCalls < 3) {
                return batches.get(0);
            }
            else {
                if(batches.size() > 0 && batches.size() + searchCalls >= batchCount + 3) {
                    batches.remove(0); // Remove the top batch from the last search
                }
                if(batches.size() > 0) {
                    return batches.get(0);
                }
                else {
                    return new ArrayList<>();
                }
            }

        });
        doReturn(hitFields).when(searchHit).source();
        doReturn(fieldValues).when(searchHit).sort();
    }

    @Test
    public void search_throws_exception_whenKindIsNullOrEmpty() {
        Assert.assertThrows(Exception.class, () -> sut.search((String)null, query, null, null, -1));
        Assert.assertThrows(Exception.class, () -> sut.search("", query, null, null, -1));
    }

    @Test
    public void search_throws_exception_whenKindsAreNullOrEmpty() {
        Assert.assertThrows(Exception.class, () -> sut.search((List)null, query, null, null, -1));
        Assert.assertThrows(Exception.class, () -> sut.search(new ArrayList<>(), query, null, null, -1));
    }

    @Test
    public void search_throws_exception_whenQueryIsNull() {
        Assert.assertThrows(Exception.class, () -> sut.search(kind, null, null, null, -1));
        Assert.assertThrows(Exception.class, () -> sut.search(List.of(kind), null, null, null, -1));
    }
}
