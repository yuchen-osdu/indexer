// Copyright © Schlumberger
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.BAG_OF_WORDS_FEATURE_NAME;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.KEYWORD_LOWER_FEATURE_NAME;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.invocation.Invocation;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.indexer.IndexSchema;
import org.opengroup.osdu.core.common.model.search.RecordMetaAttribute;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.indexer.cache.partitionsafe.IndexCache;
import org.opengroup.osdu.indexer.model.XcollaborationHolder;
import org.opengroup.osdu.indexer.service.exception.ElasticsearchMappingException;
import org.opengroup.osdu.indexer.util.CustomIndexAnalyzerSetting;
import org.opengroup.osdu.indexer.util.ElasticClientHandler;
import org.opengroup.osdu.indexer.util.TypeMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {IFeatureFlag.class})
public class IndexerMappingServiceTest {

    private final String kind = "tenant:test:test:1.0.0";
    private final String index = "tenant-test-test-1.0.0";
    private final String type = "test";
    private final String validMapping = "{\"dynamic\":false,\"properties\":{\"data\":{\"properties\":{\"Msg\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"}}},\"Intervals\":{\"properties\":{\"StopMarkerID\":{\"type\":\"keyword\"},\"GeologicUnitInterpretationIDs\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"}}},\"StopMeasuredDepth\":{\"type\":\"double\"}}},\"Location\":{\"type\":\"geo_point\"}}},\"bagOfWords\":{\"store\":true,\"type\":\"text\",\"fields\":{\"autocomplete\":{\"type\":\"completion\",\"analyzer\":\"standard\"}}},\"authority\":{\"type\":\"constant_keyword\",\"value\":\"tenant\"},\"id\":{\"type\":\"keyword\"},\"acl\":{\"properties\":{\"viewers\":{\"type\":\"keyword\"},\"owners\":{\"type\":\"keyword\"}}}}}";
    private final String validKeywordLowerMapping = "{\"dynamic\":false,\"properties\":{\"data\":{\"properties\":{\"Msg\":{\"type\":\"text\",\"fields\":{\"keywordLower\":{\"normalizer\":\"lowercase\",\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"},\"keyword\":{\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"}}},\"Intervals\":{\"properties\":{\"StopMarkerID\":{\"type\":\"keyword\"},\"GeologicUnitInterpretationIDs\":{\"type\":\"text\",\"fields\":{\"keywordLower\":{\"normalizer\":\"lowercase\",\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"},\"keyword\":{\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"}}},\"StopMeasuredDepth\":{\"type\":\"double\"}}},\"Location\":{\"type\":\"geo_point\"}}},\"bagOfWords\":{\"store\":true,\"type\":\"text\",\"fields\":{\"autocomplete\":{\"type\":\"completion\",\"analyzer\":\"standard\"}}},\"authority\":{\"type\":\"constant_keyword\",\"value\":\"tenant\"},\"id\":{\"type\":\"keyword\"},\"acl\":{\"properties\":{\"viewers\":{\"type\":\"keyword\"},\"owners\":{\"type\":\"keyword\"}}}}}";
    private final String validMappingWithoutBagOfWords = "{\"dynamic\":false,\"properties\":{\"data\":{\"properties\":{\"Msg\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"}}},\"Intervals\":{\"properties\":{\"StopMarkerID\":{\"type\":\"keyword\"},\"GeologicUnitInterpretationIDs\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"}}},\"StopMeasuredDepth\":{\"type\":\"double\"}}},\"Location\":{\"type\":\"geo_point\"}}},\"authority\":{\"type\":\"constant_keyword\",\"value\":\"tenant\"},\"id\":{\"type\":\"keyword\"},\"acl\":{\"properties\":{\"viewers\":{\"type\":\"keyword\"},\"owners\":{\"type\":\"keyword\"}}}}}";
    private final String validKeywordLowerMappingWithoutBagOfWords = "{\"dynamic\":false,\"properties\":{\"data\":{\"properties\":{\"Msg\":{\"type\":\"text\",\"fields\":{\"keywordLower\":{\"normalizer\":\"lowercase\",\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"},\"keyword\":{\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"}}},\"Intervals\":{\"properties\":{\"StopMarkerID\":{\"type\":\"keyword\"},\"GeologicUnitInterpretationIDs\":{\"type\":\"text\",\"fields\":{\"keywordLower\":{\"normalizer\":\"lowercase\",\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"},\"keyword\":{\"null_value\":\"null\",\"ignore_above\":256,\"type\":\"keyword\"}}},\"StopMeasuredDepth\":{\"type\":\"double\"}}},\"Location\":{\"type\":\"geo_point\"}}},\"authority\":{\"type\":\"constant_keyword\",\"value\":\"tenant\"},\"id\":{\"type\":\"keyword\"},\"acl\":{\"properties\":{\"viewers\":{\"type\":\"keyword\"},\"owners\":{\"type\":\"keyword\"}}}}}";
    private final String emptyDataValidMapping = "{\"dynamic\":false,\"properties\":{\"id\":{\"type\":\"keyword\"},\"acl\":{\"properties\":{\"viewers\":{\"type\":\"keyword\"},\"owners\":{\"type\":\"keyword\"}}},\"bagOfWords\":{\"store\":true,\"type\":\"text\",\"fields\":{\"autocomplete\":{\"type\":\"completion\",\"analyzer\":\"standard\"}}},\"authority\":{\"type\":\"constant_keyword\",\"value\":\"tenant\"}}}";
    private final String mapping = "{\"dynamic\":false,\"properties\":{\"ancestry\":{\"type\":\"object\",\"properties\":{\"parents\":{\"type\":\"keyword\"}}},\"data\":{\"type\":\"object\",\"properties\":{\"Address\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256,\"null_value\":\"null\"}}},\"Phone\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256,\"null_value\":\"null\"}}},\"Full Name\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256,\"null_value\":\"null\"}}}}},\"x-acl\":{\"type\":\"keyword\"},\"kind\":{\"type\":\"keyword\"},\"legal\":{\"type\":\"object\",\"properties\":{\"legaltags\":{\"type\":\"keyword\"},\"otherRelevantDataCountries\":{\"type\":\"keyword\"},\"status\":{\"type\":\"keyword\"}}},\"namespace\":{\"type\":\"keyword\"},\"index\":{\"type\":\"object\",\"properties\":{\"trace\":{\"type\":\"text\"},\"lastUpdateTime\":{\"type\":\"date\"},\"statusCode\":{\"type\":\"integer\"}}},\"acl\":{\"type\":\"object\",\"properties\":{\"viewers\":{\"type\":\"keyword\"},\"owners\":{\"type\":\"keyword\"}}},\"id\":{\"type\":\"keyword\"},\"type\":{\"type\":\"keyword\"},\"version\":{\"type\":\"long\"},\"tags\":{\"type\":\"flattened\"}}}";
    @Mock
    private RestClient restClient;
    @Mock
    private Response response;
    @Mock
    private StatusLine statusLine;
    @Mock
    private JaxRsDpsLog log;
    @Mock
    private ElasticClientHandler elasticClientHandler;
    @Mock
    private IndexCache indexCache;
    @Mock
    private IndicesService indicesService;
    @Mock
    private ElasticIndexNameResolver elasticIndexNameResolver;
    @Mock
    private XcollaborationHolder xcollaborationHolder;
    @MockBean
    private IFeatureFlag featureFlag;
    @Mock
    private CustomIndexAnalyzerSetting customIndexAnalyzerSetting;

    @Spy
    @InjectMocks
    private IndexerMappingServiceImpl sut = new IndexerMappingServiceImpl();

    private IndexSchema indexSchema;
    private IndexSchema noDataIndexSchema;

    private ElasticsearchIndicesClient indicesClient;
    private ElasticsearchClient elasticsearchClient;

    @Before
    public void setup() throws IOException {
        initMocks(this);

        this.indexSchema = IndexSchema.builder().kind(kind).type(type).dataSchema(getDataAttributeMapping()).metaSchema(getMetaAttributeMapping()).build();
        this.noDataIndexSchema = IndexSchema.builder().kind(kind).type(type).dataSchema(null).metaSchema(getMetaAttributeMapping()).build();

        this.indicesClient = mock(ElasticsearchIndicesClient.class);
        this.elasticsearchClient = mock(ElasticsearchClient.class);

        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(index);
//        when(this.restHighLevelClient.getLowLevelClient()).thenReturn(restClient);
        when(this.restClient.performRequest(any())).thenReturn(response);
        when(this.response.getStatusLine()).thenReturn(statusLine);
        when(this.statusLine.getStatusCode()).thenReturn(200);
        when(xcollaborationHolder.isFeatureEnabledAndHeaderExists()).thenReturn(false);

        when(this.elasticsearchClient.indices()).thenReturn(this.indicesClient);
        GetMappingResponse mappingResponse = mock(GetMappingResponse.class);
        when(this.indicesClient.getMapping(any(GetMappingRequest.class))).thenReturn(mappingResponse);
        when(this.customIndexAnalyzerSetting.isEnabled()).thenReturn(false);
    }

    private Map<String, Object> getMetaAttributeMapping() {
        Map<String, Object> metaMapping = new HashMap<>();
        metaMapping.put(RecordMetaAttribute.ID.getValue(), "keyword");
        metaMapping.put(RecordMetaAttribute.ACL.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.ACL));
        metaMapping.put(RecordMetaAttribute.BAG_OF_WORDS.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.BAG_OF_WORDS));
        metaMapping.put(RecordMetaAttribute.AUTHORITY.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.AUTHORITY));
        return metaMapping;
    }

    private Map<String, Object> getDataAttributeMapping() {
        Map<String, Object> dataMapping = new HashMap<>();
        dataMapping.put("Location", "geo_point");
        dataMapping.put("Msg", "text");
        Map<String, Object> intervalNestedAttribute = new HashMap<>();
        Map<String, Object> intervalProperties = new HashMap<>();
        intervalProperties.put("StopMarkerID", "keyword");
        intervalProperties.put("GeologicUnitInterpretationIDs", "text");
        intervalProperties.put("StopMeasuredDepth", "double");
        intervalNestedAttribute.put("properties", intervalProperties);
        dataMapping.put("Intervals", intervalNestedAttribute);
        return dataMapping;
    }

    @Test
    public void should_returnValidMapping_givenFalseMerge_keywordLowerDisabled_createMappingTest() throws IOException {
        when(this.featureFlag.isFeatureEnabled(KEYWORD_LOWER_FEATURE_NAME)).thenReturn(false);
        PutMappingResponse putMappingResponse = mock(PutMappingResponse.class);
        when(putMappingResponse.acknowledged()).thenReturn(true);
        when(indicesClient.putMapping(any(PutMappingRequest.class))).thenReturn(putMappingResponse);

        try {
            String mapping = this.sut.createMapping(elasticsearchClient, indexSchema, index, false);
            assertEquals(validMapping, mapping);
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    @Test
    public void should_returnValidMapping_givenFalseMerge_keywordLowerEnabled_createMappingTest() throws IOException {
        when(this.featureFlag.isFeatureEnabled(KEYWORD_LOWER_FEATURE_NAME)).thenReturn(true);

        PutMappingResponse putMappingResponse = mock(PutMappingResponse.class);
        when(putMappingResponse.acknowledged()).thenReturn(true);
        when(indicesClient.putMapping(any(PutMappingRequest.class))).thenReturn(putMappingResponse);

        try {
            String mapping = this.sut.createMapping(elasticsearchClient, indexSchema, index, false);
            assertEquals(validKeywordLowerMapping, mapping);
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }


    @Test
    public void should_returnValidMapping_givenTrueMerge_createMappingTest() {
        try {
            PutMappingResponse mappingResponse = mock(PutMappingResponse.class);
            doReturn(true).when(mappingResponse).acknowledged();
            doReturn(this.indicesClient).when(this.elasticsearchClient).indices();
            doReturn(mappingResponse).when(this.indicesClient).putMapping(any(PutMappingRequest.class));

            String mapping = this.sut.createMapping(this.elasticsearchClient, this.indexSchema, this.index, true);
            assertEquals(this.validMappingWithoutBagOfWords, mapping);
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    @Test
    public void should_returnValidMapping_givenTrueMerge_keywordLowerEnabled_createMappingTest() {
        when(this.featureFlag.isFeatureEnabled(KEYWORD_LOWER_FEATURE_NAME)).thenReturn(true);
        try {
            PutMappingResponse mappingResponse = mock(PutMappingResponse.class);
            doReturn(true).when(mappingResponse).acknowledged();
            doReturn(this.indicesClient).when(this.elasticsearchClient).indices();
            doReturn(mappingResponse).when(this.indicesClient).putMapping(any(PutMappingRequest.class));

            String mapping = this.sut.createMapping(this.elasticsearchClient, this.indexSchema, this.index, true);
            assertEquals(validKeywordLowerMappingWithoutBagOfWords, mapping);
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    @Test
    public void should_returnValidMapping_givenExistType_createMappingTest() {
        try {
            PutMappingResponse mappingResponse = mock(PutMappingResponse.class);
            doReturn(true).when(mappingResponse).acknowledged();
            doReturn(this.indicesClient).when(this.elasticsearchClient).indices();
            doReturn(mappingResponse).when(this.indicesClient).putMapping(any(PutMappingRequest.class));

            IndexerMappingServiceImpl indexerMappingServiceLocal = spy(new IndexerMappingServiceImpl());
            String mapping = this.sut.createMapping(this.elasticsearchClient, this.indexSchema, this.index, true);
            assertEquals(validMappingWithoutBagOfWords, mapping);
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    @Test
    public void should_returnDocumentMapping_givenValidIndexSchema() {
        try {
            Map<String, Object> documentMapping = this.sut.getIndexMappingFromRecordSchema(this.indexSchema);
            String documentMappingJson = new Gson().toJson(documentMapping);
            assertEquals(validMapping, documentMappingJson);
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    @Test
    public void should_returnDocumentMapping_givenValidEmptyDataIndexSchema() {
        try {
            IndexSchema emptyDataIndexSchema = IndexSchema.builder().kind(kind).type(type).metaSchema(getMetaAttributeMapping()).build();
            Map<String, Object> documentMapping = this.sut.getIndexMappingFromRecordSchema(emptyDataIndexSchema);
            String documentMappingJson = new Gson().toJson(documentMapping);
            assertEquals(emptyDataValidMapping, documentMappingJson);
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    @Test
    public void should_returnCachedStatus_givenUpdatedIndex() throws Exception {
        final String cacheKey = String.format("metaAttributeMappingSynced-%s", index);
        when(this.indexCache.get(cacheKey)).thenReturn(true);

        this.sut.syncMetaAttributeIndexMappingIfRequired(elasticsearchClient, indexSchema);
        Collection<Invocation> invocations = mockingDetails(this.sut).getInvocations();
        assertEquals(1, invocations.size());
    }

    @Test
    public void should_applyNoUpdate_givenUpdateIndex() throws Exception {
        final String cacheKey = String.format("metaAttributeMappingSynced-%s", index);
        final String mapping = "{\"dynamic\":\"false\",\"properties\":{\"acl\":{\"properties\":{\"owners\":{\"type\":\"keyword\"},\"viewers\":{\"type\":\"keyword\"}}},\"ancestry\":{\"properties\":{\"parents\":{\"type\":\"keyword\"}}},\"authority\":{\"type\":\"constant_keyword\",\"value\":\"opendes\"},\"createTime\":{\"type\":\"date\"},\"createUser\":{\"type\":\"keyword\"},\"data\":{\"properties\":{\"message\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"null_value\":\"null\",\"ignore_above\":256}}}}},\"id\":{\"type\":\"keyword\"},\"index\":{\"properties\":{\"lastUpdateTime\":{\"type\":\"date\"},\"statusCode\":{\"type\":\"integer\"},\"trace\":{\"type\":\"text\"}}},\"kind\":{\"type\":\"keyword\"},\"legal\":{\"properties\":{\"legaltags\":{\"type\":\"keyword\"},\"otherRelevantDataCountries\":{\"type\":\"keyword\"},\"status\":{\"type\":\"keyword\"}}},\"modifyTime\":{\"type\":\"date\"},\"modifyUser\":{\"type\":\"keyword\"},\"namespace\":{\"type\":\"keyword\"},\"source\":{\"type\":\"constant_keyword\",\"value\":\"test\"},\"tags\":{\"type\":\"flattened\"},\"type\":{\"type\":\"keyword\"},\"version\":{\"type\":\"long\"},\"x-acl\":{\"type\":\"keyword\"},\"bagOfWords\":{\"search_analyzer\":\"whitespace\",\"analyzer\":\"detailExtractor\",\"store\":true,\"type\":\"text\",\"fields\":{\"autocomplete\":{\"type\":\"completion\",\"analyzer\":\"detailExtractor\",\"search_analyzer\":\"whitespace\",\"max_input_length\":256}}}}}";
        doReturn(mapping).when(this.sut).getIndexMapping(elasticsearchClient, index);

        PutMappingResponse putMappingResponse = mock(PutMappingResponse.class);
        when(putMappingResponse.acknowledged()).thenReturn(true);
        when(indicesClient.putMapping(any(PutMappingRequest.class))).thenReturn(putMappingResponse);

        this.sut.syncMetaAttributeIndexMappingIfRequired(elasticsearchClient, noDataIndexSchema);

        verify(this.indexCache, times(1)).get(cacheKey);
        verify(this.indexCache, times(1)).put(cacheKey, true);
    }

    @Test
    public void should_applyUpdate_givenExistingIndex() throws Exception {
        final String cacheKey = String.format("metaAttributeMappingSynced-%s", index);
        final String mapping = "{\"dynamic\":\"false\",\"properties\":{\"acl\":{\"properties\":{\"owners\":{\"type\":\"keyword\"},\"viewers\":{\"type\":\"keyword\"}}},\"ancestry\":{\"properties\":{\"parents\":{\"type\":\"keyword\"}}},\"data\":{\"properties\":{\"message\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"null_value\":\"null\",\"ignore_above\":256}}}}},\"id\":{\"type\":\"keyword\"},\"index\":{\"properties\":{\"lastUpdateTime\":{\"type\":\"date\"},\"statusCode\":{\"type\":\"integer\"},\"trace\":{\"type\":\"text\"}}},\"kind\":{\"type\":\"keyword\"},\"legal\":{\"properties\":{\"legaltags\":{\"type\":\"keyword\"},\"otherRelevantDataCountries\":{\"type\":\"keyword\"},\"status\":{\"type\":\"keyword\"}}},\"namespace\":{\"type\":\"keyword\"},\"tags\":{\"type\":\"flattened\"},\"type\":{\"type\":\"keyword\"},\"version\":{\"type\":\"long\"},\"x-acl\":{\"type\":\"keyword\"}}}";
        doReturn(mapping).when(this.sut).getIndexMapping(elasticsearchClient, index);

        PutMappingResponse mappingResponse = mock(PutMappingResponse.class);
        doReturn(true).when(mappingResponse).acknowledged();
        doReturn(this.indicesClient).when(this.elasticsearchClient).indices();
        doReturn(mappingResponse).when(this.indicesClient).putMapping(any(PutMappingRequest.class));

        this.sut.syncMetaAttributeIndexMappingIfRequired(elasticsearchClient, indexSchema);

        verify(this.indexCache, times(1)).get(cacheKey);
        verify(this.indexCache, times(1)).put(cacheKey, true);
        verify(this.indicesClient, times(1)).putMapping(any(PutMappingRequest.class));
    }

    @Test
    public void should_applyNoUpdate_onDataChange_givenMetaUpdate_onIndex() throws Exception {
        final String cacheKey = String.format("metaAttributeMappingSynced-%s", index);
        final String mapping = "{\"dynamic\":\"false\",\"properties\":{\"acl\":{\"properties\":{\"owners\":{\"type\":\"keyword\"},\"viewers\":{\"type\":\"keyword\"}}},\"ancestry\":{\"properties\":{\"parents\":{\"type\":\"keyword\"}}},\"authority\":{\"type\":\"constant_keyword\",\"value\":\"opendes\"},\"createTime\":{\"type\":\"date\"},\"createUser\":{\"type\":\"keyword\"},\"data\":{\"properties\":{\"message\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"null_value\":\"null\",\"ignore_above\":256}}}}},\"id\":{\"type\":\"keyword\"},\"index\":{\"properties\":{\"lastUpdateTime\":{\"type\":\"date\"},\"statusCode\":{\"type\":\"integer\"},\"trace\":{\"type\":\"text\"}}},\"kind\":{\"type\":\"keyword\"},\"legal\":{\"properties\":{\"legaltags\":{\"type\":\"keyword\"},\"otherRelevantDataCountries\":{\"type\":\"keyword\"},\"status\":{\"type\":\"keyword\"}}},\"modifyTime\":{\"type\":\"date\"},\"modifyUser\":{\"type\":\"keyword\"},\"namespace\":{\"type\":\"keyword\"},\"source\":{\"type\":\"constant_keyword\",\"value\":\"test\"},\"tags\":{\"type\":\"flattened\"},\"type\":{\"type\":\"keyword\"},\"version\":{\"type\":\"long\"},\"x-acl\":{\"type\":\"keyword\"},\"bagOfWords\":{\"search_analyzer\":\"whitespace\",\"analyzer\":\"detailExtractor\",\"store\":true,\"type\":\"text\",\"fields\":{\"autocomplete\":{\"type\":\"completion\",\"analyzer\":\"detailExtractor\",\"search_analyzer\":\"whitespace\",\"max_input_length\":256}}}}}";
        doReturn(mapping).when(this.sut).getIndexMapping(elasticsearchClient, index);
        when(this.featureFlag.isFeatureEnabled(BAG_OF_WORDS_FEATURE_NAME)).thenReturn(false);

        when(this.elasticsearchClient.indices()).thenReturn(this.indicesClient);
        GetMappingResponse mappingResponse = mock(GetMappingResponse.class);
        when(this.indicesClient.getMapping(any(GetMappingRequest.class))).thenReturn(mappingResponse);

        PutMappingResponse putMappingResponse = mock(PutMappingResponse.class);
        when(putMappingResponse.acknowledged()).thenReturn(true);
        when(indicesClient.putMapping(any(PutMappingRequest.class))).thenReturn(putMappingResponse);

        this.sut.syncMetaAttributeIndexMappingIfRequired(elasticsearchClient, noDataIndexSchema);

        verify(this.indexCache, times(1)).get(cacheKey);
        verify(this.indexCache, times(1)).put(cacheKey, true);
        verify(this.featureFlag, times(1)).isFeatureEnabled(BAG_OF_WORDS_FEATURE_NAME);
        try (MockedStatic<TypeMapper> theMock = Mockito.mockStatic(TypeMapper.class)) {
            theMock.verifyNoInteractions();
        }
    }

    @Test
    public void should_throwMappingException_givenElasticsearchException() throws Exception {
        String mapping = "{\"dynamic\":\"false\",\"properties\":{\"acl\":{\"properties\":{\"owners\":{\"type\":\"keyword\"},\"viewers\":{\"type\":\"keyword\"}}},\"ancestry\":{\"properties\":{\"parents\":{\"type\":\"keyword\"}}},\"data\":{\"properties\":{\"message\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"null_value\":\"null\",\"ignore_above\":256}}}}},\"id\":{\"type\":\"keyword\"},\"index\":{\"properties\":{\"lastUpdateTime\":{\"type\":\"date\"},\"statusCode\":{\"type\":\"integer\"},\"trace\":{\"type\":\"text\"}}},\"kind\":{\"type\":\"keyword\"},\"legal\":{\"properties\":{\"legaltags\":{\"type\":\"keyword\"},\"otherRelevantDataCountries\":{\"type\":\"keyword\"},\"status\":{\"type\":\"keyword\"}}},\"namespace\":{\"type\":\"keyword\"},\"tags\":{\"type\":\"flattened\"},\"type\":{\"type\":\"keyword\"},\"version\":{\"type\":\"long\"},\"x-acl\":{\"type\":\"keyword\"}}}";
        String message = "testExceptionMessage";

        ErrorResponse errorResponse = ErrorResponse.of(errorRespBuilder -> errorRespBuilder
            .error(ErrorCause.of(errorCauseBuilder -> errorCauseBuilder.reason(message)))
            .status(HttpStatus.SC_BAD_REQUEST)
        );

        doReturn(mapping).when(this.sut).getIndexMapping(elasticsearchClient, index);
        doReturn(this.indicesClient).when(this.elasticsearchClient).indices();
        doThrow(new ElasticsearchException(message, errorResponse)).when(this.indicesClient).putMapping(any(PutMappingRequest.class));

        GetMappingResponse mappingResponse = mock(GetMappingResponse.class);
        when(this.indicesClient.getMapping(any(GetMappingRequest.class))).thenReturn(mappingResponse);

        ElasticsearchMappingException exception = assertThrows(
                ElasticsearchMappingException.class,
                () -> this.sut.syncMetaAttributeIndexMappingIfRequired(elasticsearchClient, indexSchema));

        String expectedMessage = "Failed to create mapping: [%s] failed: [null] %s".formatted(message, message);
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    public void testGetIndexMapping() throws Exception {
        when(indicesService.isIndexExist(any(), any())).thenReturn(true);
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        TypeMapping typeMapping = TypeMapping.of(builder -> builder.withJson(new StringReader(mapping)));

        GetMappingResponse getMappingResponse = GetMappingResponse.of(
            responseBuilder -> responseBuilder.result(
                "index", IndexMappingRecord.of(mappingRecordBuilder -> mappingRecordBuilder.mappings(typeMapping))
            )
        );
        when(indicesClient.getMapping(any(GetMappingRequest.class))).thenReturn(getMappingResponse);
        String actualMapping = this.sut.getIndexMapping(elasticsearchClient, "index");
        assertEquals(mapping, actualMapping);
    }
}
