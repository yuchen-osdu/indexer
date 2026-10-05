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

package org.opengroup.osdu.indexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexState;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.indexer.cache.partitionsafe.IndexCache;
import org.opengroup.osdu.indexer.util.CustomIndexAnalyzerSetting;
import org.opengroup.osdu.indexer.util.ElasticAliasUtil;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for retry/idempotency and race condition scenarios
 * in the IndicesServiceImpl alias functionality.
 */
@RunWith(MockitoJUnitRunner.class)
public class IndicesServiceRetryTest {

    @Mock
    private RequestScopedElasticsearchClient requestScopedClient;
    @Mock
    private ElasticIndexNameResolver elasticIndexNameResolver;
    @Mock
    private IndexCache indexCache;
    @Mock
    private IndexAliasService indexAliasService;
    @Mock
    private JaxRsDpsLog log;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private CustomIndexAnalyzerSetting customIndexAnalyzerSetting;
    @Mock
    private ElasticAliasUtil aliasUtil;

    @InjectMocks
    private IndicesServiceImpl indicesService;

    @Mock
    private ElasticsearchClient client;
    @Mock
    private ElasticsearchIndicesClient indicesClient;

    private static final String INDEX_NAME = "test-index-1.0.0";
    private static final String PHYSICAL_INDEX_NAME = "test-index-1.0.0-r1";
    private static final String KIND = "test:data:TestType:1.0.0";

    @Before
    public void setup() {
        lenient().when(requestScopedClient.getClient()).thenReturn(client);
        lenient().when(client.indices()).thenReturn(indicesClient);
        lenient().when(aliasUtil.getPhysicalIndexNameForCreation(INDEX_NAME)).thenReturn(PHYSICAL_INDEX_NAME);
        lenient().when(elasticIndexNameResolver.getKindFromIndexName(INDEX_NAME)).thenReturn(KIND);
        lenient().when(customIndexAnalyzerSetting.isEnabled()).thenReturn(false);

        try {
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (Exception e) {
            // Mock setup exception
        }
    }

    @Test
    public void testRetryIdempotency_PhysicalIndexExistsFromPreviousAttempt_SucceedsOnRetry() throws Exception {
        BooleanResponse indexNotExists = mock(BooleanResponse.class);
        when(indexNotExists.value()).thenReturn(false);
        BooleanResponse physicalIndexExists = mock(BooleanResponse.class);
        when(physicalIndexExists.value()).thenReturn(true);

        // First call: index doesn't exist; physical index already exists from previous attempt
        when(indicesClient.exists(any(ExistsRequest.class)))
            .thenReturn(indexNotExists)        // isIndexExist(index)
            .thenReturn(physicalIndexExists);  // isIndexExist(physicalIndex)

        when(aliasUtil.createAlias(client, INDEX_NAME, PHYSICAL_INDEX_NAME)).thenReturn(true);

        boolean result = indicesService.createIndex(client, INDEX_NAME, null, new HashMap<>());

        assertTrue("Should succeed by reusing existing physical index", result);
        verify(indicesClient, never()).create(any(CreateIndexRequest.class));
        verify(aliasUtil, times(1)).createAlias(client, INDEX_NAME, PHYSICAL_INDEX_NAME);
        verify(indexCache, times(1)).put(INDEX_NAME, true);
    }

    @Test
    public void testRaceCondition_ResourceAlreadyExists_ProceedsToAliasCreation() throws Exception {
        BooleanResponse indexNotExists = mock(BooleanResponse.class);
        when(indexNotExists.value()).thenReturn(false);
        BooleanResponse physicalIndexNotExists = mock(BooleanResponse.class);
        when(physicalIndexNotExists.value()).thenReturn(false);

        when(indicesClient.exists(any(ExistsRequest.class)))
            .thenReturn(indexNotExists)
            .thenReturn(physicalIndexNotExists);

        ElasticsearchException alreadyExistsException = new ElasticsearchException("test",
            ErrorResponse.of(er -> er.status(HttpStatus.SC_BAD_REQUEST)
                .error(ErrorCause.of(ec -> ec.type("resource_already_exists_exception")
                    .reason("Index already exists")))));

        when(indicesClient.create(any(CreateIndexRequest.class))).thenThrow(alreadyExistsException);
        when(aliasUtil.createAlias(client, INDEX_NAME, PHYSICAL_INDEX_NAME)).thenReturn(true);

        boolean result = indicesService.createIndex(client, INDEX_NAME, null, new HashMap<>());

        assertTrue("Should handle resource_already_exists_exception gracefully", result);
        verify(aliasUtil, times(1)).createAlias(client, INDEX_NAME, PHYSICAL_INDEX_NAME);
        verify(indexCache, times(1)).put(INDEX_NAME, true);
        verify(log).info(contains("created by concurrent request"));
    }

    @Test
    public void testRaceCondition_AliasCreationFails_ReturnsFalse() throws Exception {
        BooleanResponse indexNotExists = mock(BooleanResponse.class);
        when(indexNotExists.value()).thenReturn(false);
        BooleanResponse physicalIndexNotExists = mock(BooleanResponse.class);
        when(physicalIndexNotExists.value()).thenReturn(false);

        when(indicesClient.exists(any(ExistsRequest.class)))
            .thenReturn(indexNotExists)
            .thenReturn(physicalIndexNotExists);

        ElasticsearchException alreadyExistsException = new ElasticsearchException("test",
            ErrorResponse.of(er -> er.status(HttpStatus.SC_BAD_REQUEST)
                .error(ErrorCause.of(ec -> ec.type("resource_already_exists_exception")
                    .reason("Index already exists")))));

        when(indicesClient.create(any(CreateIndexRequest.class))).thenThrow(alreadyExistsException);
        when(aliasUtil.createAlias(any(), eq(INDEX_NAME), eq(PHYSICAL_INDEX_NAME))).thenReturn(false);

        boolean result = indicesService.createIndex(client, INDEX_NAME, null, new HashMap<>());

        assertFalse("Should return false when alias creation fails", result);
        verify(aliasUtil, times(1)).createAlias(any(), eq(INDEX_NAME), eq(PHYSICAL_INDEX_NAME));
        verify(indexCache, times(0)).put(any(), any());
    }

    @Test
    public void testAliasCollisionDetection_ExistingIndexSkipsCreation() throws Exception {
        BooleanResponse aliasNameExistsAsPhysicalIndex = mock(BooleanResponse.class);
        when(aliasNameExistsAsPhysicalIndex.value()).thenReturn(true);

        when(indicesClient.exists(any(ExistsRequest.class)))
            .thenReturn(aliasNameExistsAsPhysicalIndex);

        boolean result = indicesService.createIndex(client, "test-conflict-1.0.0", null, new HashMap<>());

        assertTrue("Should return true when index already exists (backward compatibility)", result);
        verify(indicesClient, never()).create(any(CreateIndexRequest.class));
        verify(aliasUtil, never()).createAlias(any(), any(), any());
    }

    @Test
    public void testRecoveredPhysicalIndexCanBeRecreatedAfterAliasDeletion() throws Exception {
        Map<String, Boolean> cache = new HashMap<>();
        Set<String> existingIndexes = new HashSet<>(Set.of(PHYSICAL_INDEX_NAME));
        when(indexCache.get(anyString())).thenAnswer(invocation -> cache.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            cache.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(indexCache).put(anyString(), anyBoolean());
        doAnswer(invocation -> {
            cache.remove(invocation.getArgument(0));
            return null;
        }).when(indexCache).delete(anyString());
        when(indicesClient.exists(any(ExistsRequest.class))).thenAnswer(invocation ->
            new BooleanResponse(existingIndexes.contains(((ExistsRequest) invocation.getArgument(0)).index().get(0))));
        when(aliasUtil.createAlias(client, INDEX_NAME, PHYSICAL_INDEX_NAME)).thenAnswer(invocation -> {
            if (!existingIndexes.contains(PHYSICAL_INDEX_NAME)) {
                return false;
            }
            existingIndexes.add(INDEX_NAME);
            return true;
        });

        assertTrue(indicesService.createIndex(client, INDEX_NAME, null, new HashMap<>()));
        assertTrue(cache.containsKey(PHYSICAL_INDEX_NAME));

        when(indicesClient.get(any(GetIndexRequest.class))).thenReturn(GetIndexResponse.of(builder ->
            builder.result(PHYSICAL_INDEX_NAME, IndexState.of(state -> state.aliases(INDEX_NAME, alias -> alias)))));
        when(indicesClient.delete(any(DeleteIndexRequest.class))).thenAnswer(invocation -> {
            existingIndexes.clear();
            return DeleteIndexResponse.of(builder -> builder.acknowledged(true));
        });
        assertTrue(indicesService.deleteIndex(client, INDEX_NAME));
        assertFalse(cache.containsKey(PHYSICAL_INDEX_NAME));
        assertFalse(cache.containsKey(INDEX_NAME));

        when(indicesClient.create(any(CreateIndexRequest.class))).thenAnswer(invocation -> {
            existingIndexes.add(PHYSICAL_INDEX_NAME);
            return CreateIndexResponse.of(builder -> builder.index(PHYSICAL_INDEX_NAME)
                .acknowledged(true).shardsAcknowledged(true));
        });
        assertTrue(indicesService.createIndex(client, INDEX_NAME, null, new HashMap<>()));
        verify(indicesClient).create(any(CreateIndexRequest.class));
    }
}
