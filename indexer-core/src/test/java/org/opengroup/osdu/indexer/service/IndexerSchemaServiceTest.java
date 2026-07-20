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

import static java.util.Map.entry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.util.Strings;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.RequestStatus;
import org.opengroup.osdu.core.common.model.indexer.IndexSchema;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.indexer.cache.partitionsafe.FlattenedSchemaCache;
import org.opengroup.osdu.indexer.cache.partitionsafe.SchemaCache;
import org.opengroup.osdu.indexer.cache.partitionsafe.VirtualPropertiesSchemaCache;
import org.opengroup.osdu.indexer.model.indexproperty.AugmenterConfiguration;
import org.opengroup.osdu.indexer.schema.converter.exeption.SchemaProcessingException;
import org.opengroup.osdu.indexer.service.exception.ElasticsearchMappingException;
import org.opengroup.osdu.indexer.util.AugmenterSetting;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;
import org.opengroup.osdu.indexer.util.function.AugmenterFunctionFactory;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class IndexerSchemaServiceTest {

    private final String kind = "tenant:test:test:1.0.0";
    private final String emptySchema = null;
    private final String someSchema = "{\"kind\":\"tenant:test:test:1.0.0\", \"schema\":[{\"path\":\"test-path\", \"kind\":\"tenant:test:test:1.0.0\"}]}";

    @Mock
    private JaxRsDpsLog log;
    @Mock
    private ElasticIndexNameResolver elasticIndexNameResolver;
    @Mock
    private IMappingService mappingService;
    @Mock
    private IndicesService indicesService;
    @Mock
    private SchemaService schemaService;
    @Mock
    private SchemaCache schemaCache;
    @Mock
    private FlattenedSchemaCache flattenedSchemaCache;
    @Mock
    private VirtualPropertiesSchemaCache virtualPropertiesSchemaCache;
    @Mock
    private AugmenterConfigurationService augmenterConfigurationService;
    @Mock
    private AugmenterSetting augmenterSetting;
    @Mock
    private RequestScopedElasticsearchClient requestScopedClient;
    @Mock
    private AugmenterFunctionFactory augmenterFunctionFactory;
    @InjectMocks
    private IndexSchemaServiceImpl sut;

    @Before
    public void setup() {
        initMocks(this);
        ElasticsearchClient restHighLevelClient = mock(ElasticsearchClient.class);
        when(requestScopedClient.getClient()).thenReturn(restHighLevelClient);
        when(augmenterSetting.isEnabled()).thenReturn(true);
        when(augmenterFunctionFactory.isAugmenterFunction(any())).thenReturn(false);
    }

    @Test
    public void should_returnNull_givenEmptySchema_getIndexerInputSchemaSchemaTest() throws Exception {
        when(schemaService.getSchema(any())).thenReturn(emptySchema);

        IndexSchema indexSchema = this.sut.getIndexerInputSchema(kind, false);

        assertNotNull(indexSchema);
    }

    @Test
    public void should_returnValidResponse_givenValidSchema_getIndexerInputSchemaTest() throws Exception {
        when(schemaService.getSchema(any())).thenReturn(someSchema);

        IndexSchema indexSchema = this.sut.getIndexerInputSchema(kind, false);

        assertEquals(kind, indexSchema.getKind());
    }

    @Test
    public void should_returnValidResponse_givenValidSchemaWithCacheHit_getIndexerInputSchemaTest() throws Exception {
        when(schemaService.getSchema(any())).thenReturn(someSchema);
        when(this.schemaCache.get(kind + "_flattened")).thenReturn(someSchema);

        IndexSchema indexSchema = this.sut.getIndexerInputSchema(kind, false);

        assertEquals(kind, indexSchema.getKind());
    }

    @Test
    public void should_retry_givenSchemaWithCacheHitAndFlattenedWithNoCacheHit_getIndexerInputSchemaTest() throws Exception {
        when(schemaService.getSchema(any())).thenReturn(someSchema);
        when(this.schemaCache.get(kind)).thenReturn(someSchema);
        when(this.schemaCache.get(kind + "_flattened")).thenReturn(null);

        IndexSchema indexSchema = this.sut.getIndexerInputSchema(kind, false);

        assertNotNull(indexSchema);
        assertEquals(kind, indexSchema.getKind());
        verify(this.schemaCache, times(2)).put(any(String.class), any(String.class));
        verify(this.flattenedSchemaCache, times(2)).put(any(String.class), any(String.class));
    }

    @Test
    public void should_throw500_givenInvalidSchemaCacheHit_getIndexerInputSchemaTest() {
        try {
            String invalidSchema = "{}}";
            when(schemaService.getSchema(any())).thenReturn(invalidSchema);

            this.sut.getIndexerInputSchema(kind, false);
            fail("Should throw exception");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getError().getCode());
            assertEquals("An error has occurred while normalizing the schema.", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not throw exception" + e.getMessage());
        }
    }

    @Test
    public void should_return_basic_schema_when_storage_returns_no_schema() throws UnsupportedEncodingException, URISyntaxException {
        IndexSchema returnedSchema = this.sut.getIndexerInputSchema(kind, false);

        assertNotNull(returnedSchema.getDataSchema());
        assertNotNull(returnedSchema);
        assertEquals(kind, returnedSchema.getKind());
    }

    @Test
    public void should_create_schema_when_storage_returns_valid_schema() throws IOException, URISyntaxException {
        String kind = "tenant1:avocet:completion:1.0.0";
        String storageSchema = "{" +
                "  \"kind\": \"tenant1:avocet:completion:1.0.0\"," +
                "  \"schema\": [" +
                "    {" +
                "      \"path\": \"status\"," +
                "      \"kind\": \"string\"" +
                "    }," +
                "    {" +
                "      \"path\": \"startDate\"," +
                "      \"kind\": \"string\"" +
                "    }," +
                "    {" +
                "      \"path\": \"endDate\"," +
                "      \"kind\": \"string\"" +
                "    }," +
                "    {" +
                "      \"path\": \"type \"," +
                "      \"kind\": \"string\"" +
                "    }," +
                "    {" +
                "      \"path\": \"itemguid\"," +
                "      \"kind\": \"string\"" +
                "    }" +
                "  ]" +
                "}";
        Map<String, OperationType> schemaMessages = new HashMap<>();
        schemaMessages.put(kind, OperationType.create_schema);

        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.schemaCache.get(kind)).thenReturn(null);
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(false);
        when(this.schemaService.getSchema(kind)).thenReturn(storageSchema);

        this.sut.processSchemaMessages(schemaMessages);

        verify(this.mappingService, times(1)).getIndexMappingFromRecordSchema(any());
        verify(this.indicesService, times(1)).createIndex(any(), any(), any(), any());
        verifyNoMoreInteractions(this.mappingService);
    }

    @Test
    public void should_merge_mapping_when_storage_returns_valid_schema() throws IOException, URISyntaxException {
        String kind = "tenant1:avocet:completion:1.0.0";
        String storageSchema = "{" +
                "  \"kind\": \"tenant1:avocet:completion:1.0.0\"," +
                "  \"schema\": [" +
                "    {" +
                "      \"path\": \"status\"," +
                "      \"kind\": \"string\"" +
                "    }," +
                "    {" +
                "      \"path\": \"startDate\"," +
                "      \"kind\": \"string\"" +
                "    }" +
                "  ]" +
                "}";
        Map<String, OperationType> schemaMessages = new HashMap<>();
        schemaMessages.put(kind, OperationType.create_schema);

        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.schemaCache.get(kind)).thenReturn(null);
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(true);
        when(this.schemaService.getSchema(kind)).thenReturn(storageSchema);

        this.sut.processSchemaMessages(schemaMessages);

        verify(this.indicesService, times(0)).createIndex(any(), any(), any(), any());
        verify(this.mappingService, times(1)).createMapping(any(), any(), any(), anyBoolean());
        verifyNoMoreInteractions(this.mappingService);
    }

    @Test
    public void should_merge_schema_without_invalidateCache_when_kind_has_property_configuration() throws IOException, URISyntaxException {
        String kind = "tenant1:avocet:completion:1.0.0";
        String storageSchema = "{" +
                "  \"kind\": \"tenant1:avocet:completion:1.0.0\"," +
                "  \"schema\": [" +
                "    {" +
                "      \"path\": \"status\"," +
                "      \"kind\": \"string\"" +
                "    }," +
                "    {" +
                "      \"path\": \"startDate\"," +
                "      \"kind\": \"string\"" +
                "    }" +
                "  ]" +
                "}";
        String extendedProperties = "[{\n" +
                "        \"path\": \"ext_p1\",\n" +
                "        \"kind\": \"string\"\n" +
                "    }, {\n" +
                "        \"path\": \"ext_p2\",\n" +
                "        \"kind\": \"string\"\n" +
                "    }\n" +
                "]";
        Gson gson = new Gson();
        Type listOfSchemaItems = new TypeToken<ArrayList<SchemaItem>>() {}.getType();
        List<SchemaItem> extendedSchemaItems = gson.fromJson(extendedProperties, listOfSchemaItems);

        when(this.schemaCache.get(kind)).thenReturn(null);
        when(this.schemaService.getSchema(kind)).thenReturn(storageSchema);
        when(this.augmenterConfigurationService.getConfiguration(kind)).thenReturn(new AugmenterConfiguration());
        when(this.augmenterConfigurationService.getExtendedSchemaItems(any(), any(), any())).thenReturn(extendedSchemaItems);

        IndexSchema indexSchema = this.sut.getIndexerInputSchema(kind, false);
        assertNotNull(indexSchema);
        assertEquals(4, indexSchema.getDataSchema().size());
        verify(this.schemaCache, times(0)).delete(any());
        verify(this.flattenedSchemaCache, times(0)).delete(any());
        verify(this.virtualPropertiesSchemaCache, times(0)).delete(any());
    }

    @Test
    public void should_merge_schema_with_invalidateCache_when_kind_has_property_configuration() throws IOException, URISyntaxException {
        String kind = "tenant1:avocet:completion:1.0.0";
        String storageSchema = "{" +
                "  \"kind\": \"tenant1:avocet:completion:1.0.0\"," +
                "  \"schema\": [" +
                "    {" +
                "      \"path\": \"status\"," +
                "      \"kind\": \"string\"" +
                "    }," +
                "    {" +
                "      \"path\": \"startDate\"," +
                "      \"kind\": \"string\"" +
                "    }" +
                "  ]" +
                "}";
        String extendedProperties = "[{\n" +
                "        \"path\": \"ext_p1\",\n" +
                "        \"kind\": \"string\"\n" +
                "    }, {\n" +
                "        \"path\": \"ext_p2\",\n" +
                "        \"kind\": \"string\"\n" +
                "    }\n" +
                "]";
        Gson gson = new Gson();
        Type listOfSchemaItems = new TypeToken<ArrayList<SchemaItem>>() {}.getType();
        List<SchemaItem> extendedSchemaItems = gson.fromJson(extendedProperties, listOfSchemaItems);

        when(this.schemaCache.get(kind)).thenReturn(null);
        when(this.schemaService.getSchema(kind)).thenReturn(storageSchema);
        when(this.augmenterConfigurationService.getConfiguration(kind)).thenReturn(new AugmenterConfiguration());
        when(this.augmenterConfigurationService.getExtendedSchemaItems(any(), any(), any())).thenReturn(extendedSchemaItems);

        IndexSchema indexSchema = this.sut.getIndexerInputSchema(kind, true);
        assertNotNull(indexSchema);
        assertEquals(4, indexSchema.getDataSchema().size());
        verify(this.schemaCache, times(1)).delete(any());
        verify(this.flattenedSchemaCache, times(1)).delete(any());
        verify(this.virtualPropertiesSchemaCache, times(1)).delete(any());
    }

    @Test
    public void should_get_schema_of_related_object_kinds_when__kind_has_property_configuration() throws IOException, URISyntaxException {
        String kind = "osdu:wks:work-product-component--WellLog:1.0.0";
        String storageSchema = "{" +
                "  \"kind\": \"osdu:wks:work-product-component--WellLog:1.0.0\"," +
                "  \"schema\": [" +
                "    {" +
                "      \"path\": \"status\"," +
                "      \"kind\": \"string\"" +
                "    }," +
                "    {" +
                "      \"path\": \"startDate\"," +
                "      \"kind\": \"string\"" +
                "    }" +
                "  ]" +
                "}";
        String propertyConfigurations = "{\n" +
                "    \"Name\": \"WellLogIndex-PropertyPathConfiguration\",\n" +
                "    \"Code\": \"osdu:wks:work-product-component--WellLog:1.\",\n" +
                "    \"AttributionAuthority\": \"OSDU\",\n" +
                "    \"Configurations\": [{\n" +
                "            \"Name\": \"WellboreName\",\n" +
                "            \"Policy\": \"ExtractFirstMatch\",\n" +
                "            \"Paths\": [{\n" +
                "                    \"RelatedObjectsSpec.RelationshipDirection\": \"ChildToParent\",\n" +
                "                    \"RelatedObjectsSpec.RelatedObjectKind\": \"osdu:wks:master-data--Wellbore:1.\",\n" +
                "                    \"RelatedObjectsSpec.RelatedObjectID\": \"data.WellboreID\",\n" +
                "                    \"ValueExtraction.ValuePath\": \"data.FacilityName\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }, {\n" +
                "            \"Name\": \"TechnicalAssuranceReviewerOrganisationNames\",\n" +
                "            \"Policy\": \"ExtractAllMatches\",\n" +
                "            \"Paths\": [{\n" +
                "                    \"RelatedObjectsSpec.RelationshipDirection\": \"ChildToParent\",\n" +
                "                    \"RelatedObjectsSpec.RelatedObjectKind\": \"osdu:wks:master-data--Organisation:1.\",\n" +
                "                    \"RelatedObjectsSpec.RelatedObjectID\": \"data.TechnicalAssurances[].Reviewers[].OrganisationID\",\n" +
                "                    \"ValueExtraction.ValuePath\": \"data.OrganisationName\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        ObjectMapper objectMapper = new ObjectMapper();
        AugmenterConfiguration augmenterConfiguration = objectMapper.readValue(propertyConfigurations, AugmenterConfiguration.class);

        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.schemaCache.get(kind)).thenReturn(null);
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(true);
        when(this.schemaService.getSchema(kind)).thenReturn(storageSchema);
        when(this.augmenterConfigurationService.getConfiguration(kind)).thenReturn(augmenterConfiguration);
        when(this.augmenterConfigurationService.resolveConcreteKind(anyString())).thenAnswer(invocation -> {
            String relatedObjectKind = invocation.getArgument(0);
            return relatedObjectKind + "0.0";
        });

        IndexSchema indexSchema = this.sut.getIndexerInputSchema(kind, false);
        assertEquals(2, indexSchema.getDataSchema().size());
        verify(this.augmenterConfigurationService, times(2)).resolveConcreteKind(any());
        verify(this.schemaCache, times(5)).get(any());
        verify(this.schemaService, times(3)).getSchema(any());
    }

    @Test
    public void should_throw_mapping_conflict_when_elastic_backend_cannot_process_schema_changes() throws IOException, URISyntaxException {
        String kind = "tenant1:avocet:completion:1.0.0";
        String reason = String.format("Could not create type mapping %s/completion.", kind.replace(":", "-"));
        String storageSchema = "{" +
                "  \"kind\": \"tenant1:avocet:completion:1.0.0\"," +
                "  \"schema\": [" +
                "    {" +
                "      \"path\": \"status\"," +
                "      \"kind\": \"string\"" +
                "    }" +
                "  ]" +
                "}";
        Map<String, OperationType> schemaMessages = new HashMap<>();
        schemaMessages.put(kind, OperationType.create_schema);

        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.schemaCache.get(kind)).thenReturn(null);
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(true);
        when(this.schemaService.getSchema(kind)).thenReturn(storageSchema);
        when(this.mappingService.createMapping(any(), any(), any(), anyBoolean())).thenThrow(new ElasticsearchMappingException(reason, HttpStatus.SC_BAD_REQUEST));

        try {
            this.sut.processSchemaMessages(schemaMessages);
        } catch (AppException e) {
            assertEquals(RequestStatus.SCHEMA_CONFLICT, e.getError().getCode());
            assertEquals("error creating or merging index mapping", e.getError().getMessage());
            assertEquals(reason, e.getError().getReason());
        } catch (Exception e) {
            fail("Should not throw this exception " + e.getMessage());
        }
    }

    @Test
    public void should_throw_genericAppException_when_elastic_backend_cannot_process_schema_changes() throws IOException, URISyntaxException {
        String kind = "tenant1:avocet:completion:1.0.0";
        String reason = String.format("Could not create type mapping %s/completion.", kind.replace(":", "-"));
        String storageSchema = "{" +
                "  \"kind\": \"tenant1:avocet:completion:1.0.0\"," +
                "  \"schema\": [" +
                "    {" +
                "      \"path\": \"status\"," +
                "      \"kind\": \"string\"" +
                "    }" +
                "  ]" +
                "}";
        Map<String, OperationType> schemaMessages = new HashMap<>();
        schemaMessages.put(kind, OperationType.create_schema);

        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.schemaCache.get(kind)).thenReturn(null);
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(true);
        when(this.schemaService.getSchema(kind)).thenReturn(storageSchema);
        when(this.mappingService.createMapping(any(), any(), any(), anyBoolean())).thenThrow(new AppException(HttpStatus.SC_FORBIDDEN, reason, "blah"));

        try {
            this.sut.processSchemaMessages(schemaMessages);
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_FORBIDDEN, e.getError().getCode());
            assertEquals("blah", e.getError().getMessage());
            assertEquals(reason, e.getError().getReason());
        } catch (Exception e) {
            fail("Should not throw this exception " + e.getMessage());
        }
    }


    @Test
    public void should_log_and_do_nothing_when_storage_returns_invalid_schema() throws IOException, URISyntaxException {
        String kind = "tenant1:avocet:completion:1.0.0";
        String storageSchema = "{" +
                "  \"kind\": \"tenant1:avocet:completion:1.0.0\"" +
                "}";
        Map<String, OperationType> schemaMessages = new HashMap<>();
        schemaMessages.put(kind, OperationType.create_schema);

        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.schemaCache.get(kind)).thenReturn(null);
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(true);
        when(this.schemaService.getSchema(kind)).thenReturn(storageSchema);

        this.sut.processSchemaMessages(schemaMessages);

        verify(this.log).warning(eq("schema not found for kind: tenant1:avocet:completion:1.0.0"));
    }

    @Test
    public void should_invalidateCache_when_purge_schema_and_schema_found_in_cache() throws IOException {
        String kind = "tenant1:avocet:completion:1.0.0";
        Map<String, OperationType> schemaMessages = new HashMap<>();
        schemaMessages.put(kind, OperationType.purge_schema);

        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(true);

        this.sut.processSchemaMessages(schemaMessages);

        verify(this.schemaCache, times(1)).delete(anyString());
        verify(this.flattenedSchemaCache, times(1)).delete(anyString());
        verify(this.virtualPropertiesSchemaCache, times(1)).delete(anyString());
    }

    @Test
    public void should_log_warning_when_purge_schema_and_schema_not_found_in_cache() throws IOException {
        String kind = "tenant1:avocet:completion:1.0.0";
        Map<String, OperationType> schemaMessages = new HashMap<>();
        schemaMessages.put(kind, OperationType.purge_schema);

        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(false);

        this.sut.processSchemaMessages(schemaMessages);

        verify(this.log).warning(eq(String.format("Kind: %s not found", kind)));
    }

    @Test
    public void should_sync_schema_with_storage() throws Exception {
        String kind = "tenant1:avocet:completion:1.0.0";
        String storageSchema = "{" +
                "  \"kind\": \"tenant1:avocet:completion:1.0.0\"," +
                "  \"schema\": [" +
                "    {" +
                "      \"path\": \"status\"," +
                "      \"kind\": \"string\"" +
                "    }" +
                "  ]" +
                "}";
        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.schemaCache.get(kind)).thenReturn(null);
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(true);
        when(this.indicesService.deleteIndex(any(), any())).thenReturn(true);
        when(this.schemaService.getSchema(kind)).thenReturn(storageSchema);

        this.sut.syncIndexMappingWithStorageSchema(kind);

        verify(this.mappingService, times(1)).getIndexMappingFromRecordSchema(any());
        verify(this.indicesService, times(1)).isIndexExist(any(), any());
        verify(this.indicesService, times(1)).deleteIndex(any(), any());
        verify(this.indicesService, times(1)).createIndex(any(), any(), any(), any());
        verifyNoMoreInteractions(this.mappingService);
    }

    @Test
    public void should_get_augmented_schema_with_ParentToChildren_and_ChildToParent_relationships() throws Exception {
        // Prepare
        Map<String, String> schemaCacheMock = new HashMap<>();
        when(this.schemaCache.get(anyString())).thenAnswer(invocation ->
                schemaCacheMock.getOrDefault(invocation.getArgument(0), null));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                schemaCacheMock.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }
        }).when(schemaCache).put(anyString(), anyString());

        Map<String, String> flattenedSchemaCacheMock = new HashMap<>();
        when(this.flattenedSchemaCache.get(anyString())).thenAnswer(invocation ->
                flattenedSchemaCacheMock.getOrDefault(invocation.getArgument(0), null));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                flattenedSchemaCacheMock.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }
        }).when(flattenedSchemaCache).put(anyString(), anyString());

        Map<String, String> kindsMap = getKindsMap();
        when(this.augmenterConfigurationService.resolveConcreteKind(anyString())).thenAnswer(invocation ->
                kindsMap.getOrDefault(invocation.getArgument(0), null));

        Map<String, String> schemaFilesMap = getSchemaFilesMap();
        when(this.schemaService.getSchema(any())).thenAnswer(invocation -> {
            String kind = (String)invocation.getArgument(0);
            String jsonFile = schemaFilesMap.get(kind);
            return getJsonFromFile(jsonFile);
        });

        Map<String, String> configurationFilesMap = getConfigurationFilesMap();
        ObjectMapper mapper = new ObjectMapper();
        when(this.augmenterConfigurationService.getConfiguration(any())).thenAnswer(invocation -> {
            String kind = (String)invocation.getArgument(0);
            String jsonFile = configurationFilesMap.getOrDefault(kind, null);
            if(Strings.isBlank(jsonFile)) {
                return null;
            }
            else {
                String jsonText = getJsonFromFile(jsonFile);
                return mapper.readValue(jsonText, AugmenterConfiguration.class);
            }
        });

        AugmenterConfigurationService augmenterConfigurationServiceMock = getAugmenterConfigurationServiceImpl();
        List<String> schemaResolvedOrders = new ArrayList<>();
        when(this.augmenterConfigurationService.getExtendedSchemaItems(any(), any(), any())).thenAnswer(invocation -> {
            Schema originalSchema = invocation.getArgument(0);
            String kind = originalSchema.getKind();
            schemaResolvedOrders.add(kind);
            Map<String, Schema> relatedObjectKindSchemas = invocation.getArgument(1);
            AugmenterConfiguration augmenterConfiguration = invocation.getArgument(2);
            return augmenterConfigurationServiceMock.getExtendedSchemaItems(originalSchema, relatedObjectKindSchemas, augmenterConfiguration);
        });

        // Test method
        IndexSchema schema = this.sut.getIndexerInputSchema("osdu:wks:master-data--Wellbore:1.4.0", false);

        // Verify
        // The order is reverse
        int order = 0;
        Assert.assertEquals("osdu:wks:master-data--Well:1.3.0", schemaResolvedOrders.get(order++));
        Assert.assertEquals("osdu:wks:work-product-component--WellLog:1.4.0", schemaResolvedOrders.get(order++));
        Assert.assertEquals("osdu:wks:master-data--Wellbore:1.4.0", schemaResolvedOrders.get(order++));

        // Only the schema of the kinds without configuration and the kind requested should be cached
        Assert.assertTrue(schemaCacheMock.size() > 1);
        Assert.assertFalse(schemaCacheMock.containsKey("osdu:wks:master-data--Well:1.3.0"));
        Assert.assertFalse(schemaCacheMock.containsKey("osdu:wks:work-product-component--WellLog:1.4.0"));
        Assert.assertTrue(schemaCacheMock.containsKey("osdu:wks:master-data--Wellbore:1.4.0"));

        Map<String, String> propertiesAndTypes = getWellboreVerifiedSchema();
        for(Map.Entry<String, String> entry : propertiesAndTypes.entrySet()) {
            String property = entry.getKey();
            Assert.assertTrue(String.format("Property '%s' not found", property), schema.getDataSchema().containsKey(property));
            Assert.assertEquals(entry.getValue(), schema.getDataSchema().get(property));
        }
    }

    @Test
    public void should_get_augmented_schema_with_ChildToParent_relationships() throws Exception {
        // Prepare
        Map<String, String> schemaCacheMock = new HashMap<>();
        when(this.schemaCache.get(anyString())).thenAnswer(invocation ->
                schemaCacheMock.getOrDefault(invocation.getArgument(0), null));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                schemaCacheMock.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }
        }).when(schemaCache).put(anyString(), anyString());

        Map<String, String> flattenedSchemaCacheMock = new HashMap<>();
        when(this.flattenedSchemaCache.get(anyString())).thenAnswer(invocation ->
                flattenedSchemaCacheMock.getOrDefault(invocation.getArgument(0), null));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                flattenedSchemaCacheMock.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }
        }).when(flattenedSchemaCache).put(anyString(), anyString());

        Map<String, String> kindsMap = getKindsMap();
        when(this.augmenterConfigurationService.resolveConcreteKind(anyString())).thenAnswer(invocation ->
                kindsMap.getOrDefault(invocation.getArgument(0), null));

        Map<String, String> schemaFilesMap = getSchemaFilesMap();
        when(this.schemaService.getSchema(any())).thenAnswer(invocation -> {
            String kind = (String)invocation.getArgument(0);
            String jsonFile = schemaFilesMap.get(kind);
            return getJsonFromFile(jsonFile);
        });

        Map<String, String> configurationFilesMap = getConfigurationFilesMap();
        ObjectMapper mapper = new ObjectMapper();
        when(this.augmenterConfigurationService.getConfiguration(any())).thenAnswer(invocation -> {
            String kind = (String)invocation.getArgument(0);
            String jsonFile = configurationFilesMap.getOrDefault(kind, null);
            if(Strings.isBlank(jsonFile)) {
                return null;
            }
            else {
                String jsonText = getJsonFromFile(jsonFile);
                return mapper.readValue(jsonText, AugmenterConfiguration.class);
            }
        });

        AugmenterConfigurationService augmenterConfigurationServiceMock = getAugmenterConfigurationServiceImpl();
        List<String> schemaResolvedOrders = new ArrayList<>();
        when(this.augmenterConfigurationService.getExtendedSchemaItems(any(), any(), any())).thenAnswer(invocation -> {
            Schema originalSchema = invocation.getArgument(0);
            String kind = originalSchema.getKind();
            schemaResolvedOrders.add(kind);
            Map<String, Schema> relatedObjectKindSchemas = invocation.getArgument(1);
            AugmenterConfiguration augmenterConfiguration = invocation.getArgument(2);
            return augmenterConfigurationServiceMock.getExtendedSchemaItems(originalSchema, relatedObjectKindSchemas, augmenterConfiguration);
        });

        // Test method
        IndexSchema schema = this.sut.getIndexerInputSchema("osdu:wks:work-product-component--WellLog:1.4.0", false);

        // Verify
        // The order is reverse
        int order = 0;
        Assert.assertEquals("osdu:wks:master-data--Well:1.3.0", schemaResolvedOrders.get(order++));
        Assert.assertEquals("osdu:wks:master-data--Wellbore:1.4.0", schemaResolvedOrders.get(order++));
        Assert.assertEquals("osdu:wks:work-product-component--WellLog:1.4.0", schemaResolvedOrders.get(order++));

        // Only the schema of the kinds without configuration and the kind requested should be cached
        Assert.assertTrue(schemaCacheMock.size() > 1);
        Assert.assertFalse(schemaCacheMock.containsKey("osdu:wks:master-data--Well:1.3.0"));
        Assert.assertFalse(schemaCacheMock.containsKey("osdu:wks:master-data--Wellbore:1.4.0"));
        Assert.assertTrue(schemaCacheMock.containsKey("osdu:wks:work-product-component--WellLog:1.4.0"));

        Map<String, String> propertiesAndTypes = getWellLogVerifiedSchema();
        for(Map.Entry<String, String> entry : propertiesAndTypes.entrySet()) {
            String property = entry.getKey();
            Assert.assertTrue(String.format("Property '%s' not found", property), schema.getDataSchema().containsKey(property));
            Assert.assertEquals(entry.getValue(), schema.getDataSchema().get(property));
        }
    }

    @Test
    public void should_throw_exception_while_snapshot_running_sync_schema_with_storage() throws Exception {
        String kind = "tenant1:avocet:completion:1.0.0";
        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.schemaCache.get(kind)).thenReturn(null);
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(true);
        when(this.indicesService.deleteIndex(any(), any())).thenThrow(new AppException(HttpStatus.SC_CONFLICT, "Index deletion error", "blah"));

        try {
            this.sut.syncIndexMappingWithStorageSchema(kind);
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_CONFLICT, e.getError().getCode());
            assertEquals("blah", e.getError().getMessage());
            assertEquals("Index deletion error", e.getError().getReason());
        } catch (Exception e) {
            fail("Should not throw this exception " + e.getMessage());
        }

        verify(this.indicesService, times(1)).isIndexExist(any(), any());
        verify(this.indicesService, times(1)).deleteIndex(any(), any());
        verify(this.mappingService, never()).getIndexMappingFromRecordSchema(any());
        verify(this.indicesService, never()).createIndex(any(), any(), any(), any());
    }

    @Test
    public void should_return_true_while_if_forceClean_requested() throws IOException {
        String kind = "tenant1:avocet:completion:1.0.0";
        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(true);

        assertTrue(this.sut.isStorageSchemaSyncRequired(kind, true));
    }

    @Test
    public void should_return_true_while_if_forceClean_notRequested_and_indexNotFound() throws IOException {
        String kind = "tenant1:avocet:completion:1.0.0";
        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(false);

        assertTrue(this.sut.isStorageSchemaSyncRequired(kind, false));
    }

    @Test
    public void should_return_false_while_if_forceClean_notRequested_and_indexExist() throws IOException {
        String kind = "tenant1:avocet:completion:1.0.0";
        when(this.elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(kind.replace(":", "-"));
        when(this.indicesService.isIndexExist(any(), any())).thenReturn(true);

        assertFalse(this.sut.isStorageSchemaSyncRequired(kind, false));
    }

    @Test
    public void should_returnErrors_givenSchemaProcessingException_getIndexerInputSchemaSchemaTest() throws UnsupportedEncodingException, URISyntaxException {
        SchemaProcessingException processingException = new SchemaProcessingException("error processing schema");
        when(schemaService.getSchema(any())).thenThrow(processingException);

        List<String> errors = new ArrayList<>();
        IndexSchema indexSchema = this.sut.getIndexerInputSchema(kind, errors);

        assertNotNull(indexSchema);
        assertTrue(errors.get(0).contains("error processing schema"));
    }

    @Test
    public void should_returnErrors_givenRuntimeException_getIndexerInputSchemaSchemaTest() throws UnsupportedEncodingException, URISyntaxException {
        RuntimeException exception = new RuntimeException("error processing schema, RuntimeException exception thrown");
        when(schemaService.getSchema(any())).thenThrow(exception);

        List<String> errors = new ArrayList<>();
        IndexSchema indexSchema = this.sut.getIndexerInputSchema(kind, errors);

        assertNotNull(indexSchema);
        assertTrue(errors.get(0).contains("error processing schema, RuntimeException exception thrown"));
    }

    @SneakyThrows
    private String getJsonFromFile(String file) {
        InputStream inStream = this.getClass().getResourceAsStream("/indexproperty/" + file);
        BufferedReader br = new BufferedReader(new InputStreamReader(inStream));
        StringBuilder stringBuilder = new StringBuilder();
        String sCurrentLine;
        while ((sCurrentLine = br.readLine()) != null)
        {
            stringBuilder.append(sCurrentLine).append("\n");
        }
        return stringBuilder.toString();
    }

    private Map<String, String> getKindsMap() {
        Map<String, String> map = Map.ofEntries(
                entry("osdu:wks:work-product-component--WellLog:1.", "osdu:wks:work-product-component--WellLog:1.4.0"),
                entry("osdu:wks:master-data--Wellbore:1.", "osdu:wks:master-data--Wellbore:1.4.0"),
                entry("osdu:wks:reference-data--OperatingEnvironment:1.", "osdu:wks:reference-data--OperatingEnvironment:1.0.0"),
                entry("osdu:wks:reference-data--WellBusinessIntention:1.", "osdu:wks:reference-data--WellBusinessIntention:1.0.0"),
                entry("osdu:wks:reference-data--WellStatusSummary:1.", "osdu:wks:reference-data--WellStatusSummary:1.0.0"),
                entry("osdu:wks:reference-data--WellProductType:1.", "osdu:wks:reference-data--WellProductType:1.0.0"),
                entry("osdu:wks:master-data--Organisation:1.", "osdu:wks:master-data--Organisation:1.2.0"),
                entry("osdu:wks:reference-data--FacilityStateType:1.", "osdu:wks:reference-data--FacilityStateType:1.0.0"),
                entry("osdu:wks:reference-data--WellboreTrajectoryType:1.", "osdu:wks:reference-data--WellboreTrajectoryType:1.0.0"),
                entry("osdu:wks:master-data--Well:1.", "osdu:wks:master-data--Well:1.3.0"),
                entry("osdu:wks:master-data--GeoPoliticalEntity:1.", "osdu:wks:master-data--GeoPoliticalEntity:1.1.0"),
                entry("osdu:wks:master-data--Field:1.", "osdu:wks:master-data--Field:1.1.0")
        );
        return map;
    }

    private Map<String, String> getSchemaFilesMap() {
        Map<String, String> map = Map.ofEntries(
                entry("osdu:wks:work-product-component--WellLog:1.4.0", "augmented_schema/Schema_WellLog.json"),
                entry("osdu:wks:master-data--Wellbore:1.4.0", "augmented_schema/Schema_Wellbore.json"),
                entry("osdu:wks:reference-data--OperatingEnvironment:1.0.0", "augmented_schema/Schema_OperatingEnvironment.json"),
                entry("osdu:wks:reference-data--WellBusinessIntention:1.0.0", "augmented_schema/Schema_WellBusinessIntention.json"),
                entry("osdu:wks:reference-data--WellStatusSummary:1.0.0", "augmented_schema/Schema_WellStatusSummary.json"),
                entry( "osdu:wks:reference-data--WellProductType:1.0.0", "augmented_schema/Schema_WellProductType.json"),
                entry( "osdu:wks:master-data--Organisation:1.2.0", "augmented_schema/Schema_Organisation.json"),
                entry( "osdu:wks:reference-data--FacilityStateType:1.0.0", "augmented_schema/Schema_FacilityStateType.json"),
                entry( "osdu:wks:reference-data--WellboreTrajectoryType:1.0.0", "augmented_schema/Schema_WellboreTrajectoryType.json"),
                entry("osdu:wks:master-data--Well:1.3.0", "augmented_schema/Schema_Well.json"),
                entry("osdu:wks:master-data--GeoPoliticalEntity:1.1.0", "augmented_schema/Schema_GeoPoliticalEntity.json"),
                entry("osdu:wks:master-data--Field:1.1.0", "augmented_schema/Schema_Field.json")
        );
        return map;
    }

    private Map<String, String> getConfigurationFilesMap() {
        Map<String, String> map = Map.ofEntries(
                entry("osdu:wks:work-product-component--WellLog:1.4.0", "augmented_schema/Configuration_WellLog.json"),
                entry("osdu:wks:work-product-component--WellLog:1.", "augmented_schema/Configuration_WellLog.json"),
                entry("osdu:wks:master-data--Wellbore:1.4.0", "augmented_schema/Configuration_Wellbore.json"),
                entry("osdu:wks:master-data--Wellbore:1.", "augmented_schema/Configuration_Wellbore.json"),
                entry("osdu:wks:master-data--Well:1.3.0", "augmented_schema/Configuration_Well.json"),
                entry("osdu:wks:master-data--Well:1.", "augmented_schema/Configuration_Well.json")
        );
        return map;
    }

    private Map<String, String> getWellboreVerifiedSchema() {
        Map<String, String> map = Map.ofEntries(
                // Part of the original properties
                entry("RoleID", "text"),
                entry("SequenceNumber", "integer"),
                entry("FacilityName", "text"),
                entry("KickOffWellbore", "text"),
                // Augmented properties
                entry("CountryNames", "text_array"),
                entry("WellUWI", "text"),
                entry("CountryName", "text"),
                entry("RegionName", "text"),
                entry("BlockName", "text"),
                entry("FieldName", "text"),
                entry("WellName", "text"),
                entry("UWI", "text"),
                entry("WellboreStatus", "text"),
                entry("CurrentOperator", "text"),
                entry("WellboreClassification", "text"),
                entry("WellborePhase", "text"),
                entry("WellboreHydrocarbonType", "text"),
                entry("WellboreSituation", "text"),
                entry("WellboreTrajectoryType", "text"),
                entry("WellLogs", "text_array")
        );

        return map;
    }

    private Map<String, String> getWellLogVerifiedSchema() {
        Map<String, String> map = Map.ofEntries(
                // Part of the original properties
                entry("CompanyID", "text"),
                entry("SamplingInterval", "double"),
                entry("LogActivity", "text"),
                entry("AuthorIDs", "text_array"),
                entry("ZeroTime", "date"),
                // Augmented properties
                entry("CountryNames", "text_array"),
                entry("WellUWI", "text"),
                entry("WellboreName", "text"),
                entry("CountryName", "text"),
                entry("RegionName", "text"),
                entry("BlockName", "text"),
                entry("FieldName", "text"),
                entry("WellName", "text"),
                entry("UWI", "text"),
                entry("UBHI", "text"),
                entry("WellboreStatus", "text"),
                entry("CurrentOperator", "text"),
                entry("WellborePhase", "text"),
                entry("WellboreHydrocarbonType", "text"),
                entry("WellboreSituation", "text"),
                entry("WellboreTrajectoryType", "text"),
                entry("SpatialLocation.Wgs84Coordinates", "geo_shape"),
                entry("VirtualProperties.DefaultLocation.Wgs84Coordinates", "geo_shape")
        );

        return map;
    }

    private AugmenterConfigurationService getAugmenterConfigurationServiceImpl() {
        AugmenterConfigurationServiceImpl augmenterConfigurationService = new AugmenterConfigurationServiceImpl();
        augmenterConfigurationService.augmenterFunctionFactory = this.augmenterFunctionFactory;
        return augmenterConfigurationService;
    }
}
