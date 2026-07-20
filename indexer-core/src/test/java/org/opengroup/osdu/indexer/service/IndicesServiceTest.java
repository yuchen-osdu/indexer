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
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.CharFilter;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.search.IndexInfo;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.indexer.cache.partitionsafe.IndexCache;
import org.opengroup.osdu.indexer.util.CustomIndexAnalyzerSetting;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opengroup.osdu.indexer.testutils.ReflectionTestUtil.setFieldValueForClass;

@RunWith(SpringRunner.class)
public class IndicesServiceTest {
    @Mock
    private RequestScopedElasticsearchClient requestScopedClient;
    @Mock
    private ElasticIndexNameResolver elasticIndexNameResolver;
    @Mock
    private IndexCache indicesExistCache;
    @Mock
    @Lazy
    private JaxRsDpsLog log;
    @Mock
    private RestClient restClient;
    @Mock
    private Response response;
    @Mock
    private HttpEntity httpEntity;
    @Mock
    private IndexAliasService indexAliasService;
    @Mock
    private CustomIndexAnalyzerSetting customIndexAnalyzerSetting;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private IndicesServiceImpl sut;

    private ElasticsearchClient restHighLevelClient;
    private ElasticsearchIndicesClient indicesClient;
    private RestClientTransport restClientTransport;

    @Before
    public void setup() {
        initMocks(this);
        indicesClient = mock(ElasticsearchIndicesClient.class);
        restHighLevelClient = mock(ElasticsearchClient.class);
        restClientTransport = mock(RestClientTransport.class);
        when(requestScopedClient.getClient()).thenReturn(restHighLevelClient);
        setFieldValueForClass(sut, "healthRetryThreshold", 1);
        setFieldValueForClass(sut, "healthRetrySleepPeriodInMilliseconds", 1);
    }

    @Test
    public void create_elasticIndex() throws Exception {
        String index = "common-welldb-wellbore-1.2.0";
        CreateIndexResponse indexResponse = CreateIndexResponse.of(builder -> builder.index(index).acknowledged(true).shardsAcknowledged(true));
        PutAliasResponse putAliasResponse = PutAliasResponse.of(builder -> builder.acknowledged(true));
        ArgumentCaptor<CreateIndexRequest> createIndexRequestArgumentCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);

        when(elasticIndexNameResolver.getIndexNameFromKind(any())).thenReturn(index);
        when(restHighLevelClient.indices()).thenReturn(indicesClient);
        when(indicesClient.create(any(CreateIndexRequest.class))).thenReturn(indexResponse);
        when(indicesClient.putAlias(any(PutAliasRequest.class))).thenReturn(putAliasResponse);
        when(customIndexAnalyzerSetting.isEnabled()).thenReturn(false);

        boolean response = this.sut.createIndex(restHighLevelClient, index, null,  new HashMap<>());
        assertTrue(response);
        when(this.indicesExistCache.get(index)).thenReturn(true);
        verify(this.indexAliasService, times(1)).createIndexAlias(any(), any());
        verify(indicesClient).create(createIndexRequestArgumentCaptor.capture());

        CreateIndexRequest request = createIndexRequestArgumentCaptor.getValue();
        IndexSettingsAnalysis analysis =request.settings().analysis();
        Assert.assertNull(analysis);
    }

    @Test
    public void create_elasticIndex_with_custom_analyzer() throws Exception {
        String index = "common-welldb-wellbore-1.2.0";
        CreateIndexResponse indexResponse = CreateIndexResponse.of(builder -> builder.index(index).acknowledged(true).shardsAcknowledged(true));
        PutAliasResponse putAliasResponse = PutAliasResponse.of(builder -> builder.acknowledged(true));
        ArgumentCaptor<CreateIndexRequest> createIndexRequestArgumentCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);

        when(elasticIndexNameResolver.getIndexNameFromKind(any())).thenReturn(index);
        when(restHighLevelClient.indices()).thenReturn(indicesClient);
        when(indicesClient.create(any(CreateIndexRequest.class))).thenReturn(indexResponse);
        when(indicesClient.putAlias(any(PutAliasRequest.class))).thenReturn(putAliasResponse);
        when(customIndexAnalyzerSetting.isEnabled()).thenReturn(true);

        boolean response = this.sut.createIndex(restHighLevelClient, index, null,  new HashMap<>());
        assertTrue(response);
        when(this.indicesExistCache.get(index)).thenReturn(true);
        verify(this.indexAliasService, times(1)).createIndexAlias(any(), any());
        verify(indicesClient).create(createIndexRequestArgumentCaptor.capture());

        CreateIndexRequest request = createIndexRequestArgumentCaptor.getValue();
        IndexSettingsAnalysis analysis =request.settings().analysis();
        Assert.assertNotNull(analysis);
        /*
        {
            "analyzer": {
                "osdu_custom_analyzer": {
                    "type": "custom",
                    "char_filter": ["osdu_mapping_charFilter_for_underscore", "osdu_patternReplace_charFilter_for_dot"],
                    "filter": ["lowercase"],
                    "tokenizer": "standard"
                }
            },
            "char_filter": {
                "osdu_mapping_charFilter_for_underscore": {
                    "type": "mapping",
                    "mappings": ["_=>\\\\u0020"]
                },
                "osdu_patternReplace_charFilter_for_dot": {
                    "type": "pattern_replace",
                    "pattern": "(\\\\D)\\\\.|\\\\.(?=\\\\D)",
                    "replacement": "$1 "
                }
            }
         }
         */
        Assert.assertTrue(analysis.analyzer().containsKey("osdu_custom_analyzer"));
        Analyzer analyzer = analysis.analyzer().get("osdu_custom_analyzer");
        Assert.assertTrue(analyzer.isCustom());
        Assert.assertEquals("standard", analyzer.custom().tokenizer());
        Assert.assertEquals(List.of("lowercase"), analyzer.custom().filter());
        Assert.assertEquals(List.of("osdu_mapping_charFilter_for_underscore", "osdu_patternReplace_charFilter_for_dot"),
                analyzer.custom().charFilter().stream().sorted().toList());

        Assert.assertTrue(analysis.charFilter().containsKey("osdu_mapping_charFilter_for_underscore"));
        CharFilter mappingCharFilter = analysis.charFilter().get("osdu_mapping_charFilter_for_underscore");
        Assert.assertTrue(mappingCharFilter.definition().isMapping());
        Assert.assertEquals(List.of("_=>\\u0020"), mappingCharFilter.definition().mapping().mappings());

        Assert.assertTrue(analysis.charFilter().containsKey("osdu_patternReplace_charFilter_for_dot"));
        CharFilter patternReplaceCharFilter = analysis.charFilter().get("osdu_patternReplace_charFilter_for_dot");
        Assert.assertTrue(patternReplaceCharFilter.definition().isPatternReplace());
        Assert.assertEquals("(\\D)\\.|\\.(?=\\D)", patternReplaceCharFilter.definition().patternReplace().pattern());
        Assert.assertEquals("$1 ", patternReplaceCharFilter.definition().patternReplace().replacement());
    }

    @Test
    public void custom_analyzer_comprehensive_char_filters() {
        String matchRegex = "(\\D)\\.|\\.(?=\\D)";
        String replacement = "$1 ";
        String input = "dev_tools_console.pdf";
        String output = input.replaceAll("_", " ").replaceAll(matchRegex, replacement);
        Assert.assertEquals("dev tools console pdf", output);

        // Replace dot with whitespace for non-number string
        input = "AAA.BBB";
        output = input.replaceAll("_", " ").replaceAll(matchRegex, replacement);
        Assert.assertEquals("AAA BBB", output);

        input = "AAA.12";
        output = input.replaceAll("_", " ").replaceAll(matchRegex, replacement);
        Assert.assertEquals("AAA 12", output);

        input = "100.BBB";
        output = input.replaceAll("_", " ").replaceAll(matchRegex, replacement);
        Assert.assertEquals("100 BBB", output);

        // No replacement for number string
        input = "100.12";
        output = input.replaceAll("_", " ").replaceAll(matchRegex, replacement);
        Assert.assertEquals(input, output);
    }

    @Test
    public void create_elasticIndex_fail() throws Exception {
        String index = "common-welldb-wellbore-1.2.0";
        CreateIndexResponse indexResponse = CreateIndexResponse.of(builder -> builder.shardsAcknowledged(false).acknowledged(false).index(index));

        when(restHighLevelClient.indices()).thenReturn(indicesClient);
        when(indicesClient.create(any(CreateIndexRequest.class))).thenReturn(indexResponse);
        when(customIndexAnalyzerSetting.isEnabled()).thenReturn(false);
        boolean response = this.sut.createIndex(restHighLevelClient, index, null, new HashMap<>());
        assertFalse(response);
        verify(this.indicesExistCache, times(0)).put(any(), any());
        verify(this.indicesClient, times(0)).putAlias(any(PutAliasRequest.class));
    }

    @Test
    public void create_existingElasticIndex() throws Exception {
        String index = "common-welldb-wellbore-1.2.0";
        ErrorResponse errorResponse = ErrorResponse.of(
            responseBuilder -> responseBuilder.error(
                ErrorCause.of(errorBuilder -> errorBuilder.reason("resource_already_exists_exception")))
                .status(HttpStatus.SC_BAD_REQUEST)
        );

        ElasticsearchException existsException = new ElasticsearchException(null, errorResponse);

        when(restHighLevelClient.indices()).thenReturn(indicesClient);
        when(indicesClient.create(any(CreateIndexRequest.class))).thenThrow(existsException);
        when(customIndexAnalyzerSetting.isEnabled()).thenReturn(false);
        boolean response = this.sut.createIndex(restHighLevelClient, index, null, new HashMap<>());
        assertTrue(response);
        verify(this.indicesExistCache, times(1)).put(any(), any());
        verify(this.indicesClient, times(0)).putAlias(any(PutAliasRequest.class));
    }

    @Test
    public void delete_existingElasticIndex() throws Exception {
        DeleteIndexResponse deleteIndexResponse = DeleteIndexResponse.of(builder -> builder.acknowledged(true));
        GetIndexResponse getIndexResponse = mock(GetIndexResponse.class);
        Map<String, IndexState> indices = Map.of("anyIndex", IndexState.of(builder -> builder));

        doReturn(indicesClient).when(restHighLevelClient).indices();
        doReturn(deleteIndexResponse).when(indicesClient).delete(any(DeleteIndexRequest.class));
        doReturn(getIndexResponse).when(indicesClient).get(any(GetIndexRequest.class));
        doReturn(indices).when(getIndexResponse).result();
        boolean response = this.sut.deleteIndex("anyIndex");
        assertTrue(response);
    }

    @Test
    public void delete_existingElasticIndex_usingSameClient() throws Exception {
        DeleteIndexResponse deleteIndexResponse =  DeleteIndexResponse.of(builder -> builder.acknowledged(true));
        GetIndexResponse getIndexResponse = mock(GetIndexResponse.class);
        Map<String, IndexState> indices = Map.of("anyIndex", IndexState.of(builder -> builder));

        doReturn(indicesClient).when(restHighLevelClient).indices();
        doReturn(deleteIndexResponse).when(indicesClient).delete(any(DeleteIndexRequest.class));
        doReturn(getIndexResponse).when(indicesClient).get(any(GetIndexRequest.class));
        doReturn(indices).when(getIndexResponse).result();
        boolean response = this.sut.deleteIndex(restHighLevelClient, "anyIndex");
        assertTrue(response);
    }

    @Test
    public void should_throw_internalServerException_delete_isNotAcknowledged() throws Exception {
        DeleteIndexResponse deleteIndexResponse =  DeleteIndexResponse.of(builder -> builder.acknowledged(false));
        GetIndexResponse getIndexResponse = mock(GetIndexResponse.class);
        Map<String, IndexState> indices = Map.of("anyIndex", IndexState.of(builder -> builder));

        doReturn(indicesClient).when(restHighLevelClient).indices();
        doReturn(deleteIndexResponse).when(indicesClient).delete(any(DeleteIndexRequest.class));
        doReturn(getIndexResponse).when(indicesClient).get(any(GetIndexRequest.class));
        doReturn(indices).when(getIndexResponse).result();

        try {
            this.sut.deleteIndex("anyIndex");
            fail("Should not succeed!");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getError().getCode());
            assertEquals("Could not delete index anyIndex", e.getError().getMessage());
            assertEquals("Index deletion error", e.getError().getReason());
        } catch (Exception e) {
            fail("Should not throw this exception " + e.getMessage());
        }
    }

    @Test
    public void should_throwAppException_when_delete_existingElasticIndex_and_backupIsRunning() throws Exception {
        ErrorResponse errorResponse = ErrorResponse.of(
            responseBuilder -> responseBuilder.error(
                    ErrorCause.of(errorBuilder -> errorBuilder.reason("Cannot delete indices that are being snapshotted: [[anyIndex/8IXuPeFnTJGEu_LjjXrHwA]]. Try again after snapshot finishes or cancel the currently running snapshot.")))
                .status(HttpStatus.SC_BAD_REQUEST)
        );

        ElasticsearchException exception = new ElasticsearchException(null, errorResponse);

        GetIndexResponse getIndexResponse = mock(GetIndexResponse.class);
        Map<String, IndexState> indices = Map.of("anyIndex", IndexState.of(builder -> builder));

        doReturn(indicesClient).when(restHighLevelClient).indices();
        doThrow(exception).when(indicesClient).delete(any(DeleteIndexRequest.class));
        doReturn(getIndexResponse).when(indicesClient).get(any(GetIndexRequest.class));
        doReturn(indices).when(getIndexResponse).result();

        try {
            this.sut.deleteIndex("anyIndex");
            fail("Should not succeed!");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_CONFLICT, e.getError().getCode());
            assertEquals("Unable to delete the index because it is currently locked. Try again in few minutes.", e.getError().getMessage());
            assertEquals("Index deletion error", e.getError().getReason());
        } catch (Exception e) {
            fail("Should not throw this exception " + e.getMessage());
        }
    }

    @Test
    public void should_throwAppException_when_delete_nonExistent_index() throws Exception {
        ErrorResponse errorResponse = ErrorResponse.of(
            responseBuilder -> responseBuilder.error(
                    ErrorCause.of(errorBuilder -> errorBuilder.reason("no such index")))
                .status(HttpStatus.SC_NOT_FOUND)
        );

        ElasticsearchException exception = new ElasticsearchException(null, errorResponse);
        doReturn(indicesClient).when(restHighLevelClient).indices();
        doThrow(exception).when(indicesClient).get(any(GetIndexRequest.class));

        try {
            this.sut.deleteIndex("anyIndex");
            fail("Should not succeed!");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getError().getCode());
            assertEquals("Index anyIndex not found", e.getError().getMessage());
            assertEquals("Index resolving error", e.getError().getReason());
        } catch (Exception e) {
            fail("Should not throw this exception " + e.getMessage());
        }
    }

    @Test
    public void should_get_valid_indexInfo() throws IOException {
        String responseJson = "[" +
                "  {" +
                "    \"index\": \"tenant1-aapg-file-1.0.0\"," +
                "    \"docs.count\": \"92\"," +
                "    \"creation.date\": \"1545912860994\"" +
                "  }" +
                "]";
        Request request = new Request("GET", "/_cat/indices/*,-.*?h=index,health,docs.count,creation.date&s=docs.count:asc&format=json");
        StringEntity entity = new StringEntity(responseJson, ContentType.APPLICATION_JSON);
        when(this.restHighLevelClient._transport()).thenReturn(this.restClientTransport);
        when(this.restClientTransport.restClient()).thenReturn(this.restClient);
        when(this.restClient.performRequest(request)).thenReturn(response);
        when(this.response.getEntity()).thenReturn(entity);

        List<IndexInfo> infos = this.sut.getIndexInfo(this.restHighLevelClient, "");
        assertNotNull(infos);
        assertEquals(1, infos.size());
    }

    @Test
    public void should_get_valid_indexInfoByPattern() throws IOException {
        String responseJson = "[" +
                "  {" +
                "    \"index\": \"tenant1-aapg-file-1.0.0\"," +
                "    \"docs.count\": \"92\"," +
                "    \"creation.date\": \"1545912860994\"" +
                "  }," +
                "  {" +
                "    \"index\": \"tenant1-aapg-document-1.0.0\"," +
                "    \"docs.count\": \"0\"," +
                "    \"creation.date\": \"1545912868416\"" +
                "  }" +
                "]";
        Request request = new Request("GET", "/_cat/indices/tenant1-aapg-*?h=index,health,docs.count,creation.date&format=json");
        StringEntity entity = new StringEntity(responseJson, ContentType.APPLICATION_JSON);
        when(this.restHighLevelClient._transport()).thenReturn(this.restClientTransport);
        when(this.restClientTransport.restClient()).thenReturn(this.restClient);
        when(this.restClient.performRequest(request)).thenReturn(response);
        when(this.response.getEntity()).thenReturn(entity);

        List<IndexInfo> infos = this.sut.getIndexInfo(this.restHighLevelClient, "tenant1-aapg-*");
        assertNotNull(infos);
        assertEquals(2, infos.size());
    }

    @Test
    public void should_properly_deserialize_indices_get_response() {
        String jsonResponse = "[{\"index\":\"tenant1-test-hello-1.0.1\",\"docs.count\":\"1\",\"creation.date\":\"1551996907769\"}]";

        final Type typeOf = new TypeToken<List<IndexInfo>>() {}.getType();
        List<IndexInfo> indicesList = new Gson().fromJson(jsonResponse, typeOf);

        assertEquals(1, indicesList.size());
        assertEquals("tenant1-test-hello-1.0.1", indicesList.get(0).getName());
        assertEquals("1", indicesList.get(0).getDocumentCount());
        assertEquals("1551996907769", indicesList.get(0).getCreationDate());
    }

    @Test
    public void should_returnTrue_indexExistInCache() throws IOException {
        when(this.indicesExistCache.get("anyIndex")).thenReturn(true);

        boolean result = this.sut.isIndexExist(any(ElasticsearchClient.class), "anyIndex");

        assertTrue(result);
    }

    @Test
    public void should_getIndexExist_whenIndexNotInCache() throws IOException {
        when(this.indicesExistCache.get("anyIndex")).thenReturn(false);

        doReturn(indicesClient).when(restHighLevelClient).indices();
        BooleanResponse booleanResponse = new BooleanResponse(true);
        doReturn(booleanResponse).when(indicesClient).exists(any(ExistsRequest.class));

        boolean result = this.sut.isIndexExist(restHighLevelClient, "anyIndex");

        assertTrue(result);
        verify(this.indicesExistCache, times(1)).get("anyIndex");
        verify(this.indicesExistCache, times(1)).put("anyIndex", true);
    }

    @Test
    public void should_getIndexReadyStatus_whenIndexInCache() throws IOException {
        when(this.indicesExistCache.get("anyIndex")).thenReturn(true);

        boolean result = this.sut.isIndexReady(mock(ElasticsearchClient.class), "anyIndex");

        assertTrue(result);
    }

    @Test
    public void should_getIndexReadyStatus_whenIndexNotInCache() throws IOException {
        when(this.indicesExistCache.get("anyIndex")).thenReturn(false);
        doReturn(indicesClient).when(restHighLevelClient).indices();

        BooleanResponse booleanResponse = new BooleanResponse(true);
        doReturn(booleanResponse).when(indicesClient).exists(any(ExistsRequest.class));

        String responseJson = "[" +
                "  {" +
                "    \"index\": \"anyIndex\"," +
                "    \"health\": \"yellow\"," +
                "    \"docs.count\": \"92\"," +
                "    \"creation.date\": \"1545912860994\"" +
                "  }" +
                "]";
        Request request = new Request("GET", "/_cat/indices/anyIndex?h=index,health,docs.count,creation.date&format=json");
        StringEntity entity = new StringEntity(responseJson, ContentType.APPLICATION_JSON);
        when(this.restHighLevelClient._transport()).thenReturn(this.restClientTransport);
        when(this.restClientTransport.restClient()).thenReturn(this.restClient);
        when(this.restClient.performRequest(request)).thenReturn(response);
        when(this.response.getEntity()).thenReturn(entity);

        boolean result = this.sut.isIndexReady(restHighLevelClient, "anyIndex");

        assertTrue(result);
        verify(this.indicesExistCache, times(1)).get("anyIndex");
        verify(this.indicesExistCache, times(1)).put("anyIndex", true);
    }

    @Test(expected = AppException.class)
    public void should_throwIndexUnavailableException_whenIndexNotReady() throws IOException {
        when(this.indicesExistCache.get("anyIndex")).thenReturn(false);
        doReturn(indicesClient).when(restHighLevelClient).indices();

        BooleanResponse booleanResponse = new BooleanResponse(true);
        doReturn(booleanResponse).when(indicesClient).exists(any(ExistsRequest.class));

        String responseJson = "[" +
                "  {" +
                "    \"index\": \"anyIndex\"," +
                "    \"health\": \"red\"," +
                "    \"docs.count\": \"92\"," +
                "    \"creation.date\": \"1545912860994\"" +
                "  }" +
                "]";
        Request request = new Request("GET", "/_cat/indices/anyIndex?h=index,health,docs.count,creation.date&format=json");
        StringEntity entity = new StringEntity(responseJson, ContentType.APPLICATION_JSON);
        when(this.restHighLevelClient._transport()).thenReturn(this.restClientTransport);
        when(this.restClientTransport.restClient()).thenReturn(this.restClient);
        when(this.restClient.performRequest(request)).thenReturn(response);
        when(this.response.getEntity()).thenReturn(entity);

        this.sut.isIndexReady(restHighLevelClient, "anyIndex");
    }

}
