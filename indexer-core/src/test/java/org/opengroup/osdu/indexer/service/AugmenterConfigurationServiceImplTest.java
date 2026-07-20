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

package org.opengroup.osdu.indexer.service;


import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.IndexingStatus;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.cache.partitionsafe.AugmenterConfigurationCache;
import org.opengroup.osdu.indexer.cache.partitionsafe.AugmenterConfigurationEnabledCache;
import org.opengroup.osdu.indexer.cache.partitionsafe.ChildRelationshipSpecsCache;
import org.opengroup.osdu.indexer.cache.partitionsafe.ChildrenKindsCache;
import org.opengroup.osdu.indexer.cache.partitionsafe.KindCache;
import org.opengroup.osdu.indexer.cache.partitionsafe.RecordChangeInfoCache;
import org.opengroup.osdu.indexer.cache.partitionsafe.RelatedObjectCache;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.opengroup.osdu.indexer.model.Constants;
import org.opengroup.osdu.indexer.model.RecordChangeInfo;
import org.opengroup.osdu.indexer.model.SchemaIdentity;
import org.opengroup.osdu.indexer.model.SchemaInfo;
import org.opengroup.osdu.indexer.model.SchemaInfoResponse;
import org.opengroup.osdu.indexer.model.SearchRecord;
import org.opengroup.osdu.indexer.model.indexproperty.AugmenterConfiguration;
import org.opengroup.osdu.indexer.util.IndexerQueueTaskBuilder;
import org.opengroup.osdu.indexer.util.SearchClient;
import org.opengroup.osdu.indexer.util.function.AugmenterFunctionFactory;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class AugmenterConfigurationServiceImplTest {
    private final Gson gson = new Gson();

    @InjectMocks
    private AugmenterConfigurationServiceImpl sut;

    @Mock
    private IndexerConfigurationProperties configurationProperties;
    @Mock
    private AugmenterConfigurationCache augmenterConfigurationCache;
    @Mock
    private AugmenterConfigurationEnabledCache augmenterConfigurationEnabledCache;
    @Mock
    private ChildRelationshipSpecsCache parentChildRelationshipSpecsCache;
    @Mock
    private ChildrenKindsCache childrenKindsCache;
    @Mock
    private KindCache kindCache;
    @Mock
    private RelatedObjectCache relatedObjectCache;
    @Mock
    private RecordChangeInfoCache recordChangeInfoCache;
    @Mock
    private SearchClient searchClient;
    @Mock
    private SchemaService schemaService;
    @Mock
    private IndexerQueueTaskBuilder indexerQueueTaskBuilder;
    @Mock
    private IRequestInfo requestInfo;
    @Mock
    private JaxRsDpsLog jaxRsDpsLog;
    @Mock
    private JobStatus jobStatus;
    @Mock
    private AugmenterFunctionFactory augmenterFunctionFactory;

    private final String augmenterConfigurationKind = "osdu:wks:reference-data--IndexPropertyPathConfiguration:*";
    private String childKind;
    private String childId;
    private String parentKind;
    private String parentId;

    @Before
    public void setup() throws Exception {
        this.sut.maxSizeOfExtendedListValue = 1000;
    }

    @Test
    public void isAugmenterConfigurationEnabled_invalid_kind() {
        Assert.assertFalse(sut.isConfigurationEnabled(null));
        Assert.assertFalse(sut.isConfigurationEnabled(""));
        Assert.assertFalse(sut.isConfigurationEnabled("anyAuth:anySource:anyEntity"));
    }

    @Test
    public void isAugmenterConfigurationEnabled_with_value_true_in_cache() {
        String kind = "anyAuth:anySource:anyEntity:1.";
        when(this.augmenterConfigurationEnabledCache.get(any())).thenReturn(true);
        Assert.assertTrue(sut.isConfigurationEnabled(kind));
        verify(this.augmenterConfigurationEnabledCache, times(0)).put(any(), any());
    }

    @Test
    public void isAugmenterConfigurationEnabled_with_value_false_in_cache() {
        String kind = "anyAuth:anySource:anyEntity:1.";
        when(this.augmenterConfigurationEnabledCache.get(any())).thenReturn(false);
        Assert.assertFalse(sut.isConfigurationEnabled(kind));
        verify(this.augmenterConfigurationEnabledCache, times(0)).put(any(), any());
    }

    @Test
    public void isAugmenterConfigurationEnabled_with_result_from_search() throws Exception {
        String kind = "anyAuth:anySource:anyEntity:1.";
        when(this.augmenterConfigurationEnabledCache.get(any())).thenReturn(null);
        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenReturn(List.of(new SearchRecord()));
        Assert.assertTrue(sut.isConfigurationEnabled(kind));
        verify(this.augmenterConfigurationEnabledCache, times(1)).put(any(), any());
    }

    @Test
    public void isAugmenterConfigurationEnabled_without_result_from_search() throws Exception {
        String kind = "anyAuth:anySource:anyEntity:1.";
        when(this.augmenterConfigurationEnabledCache.get(any())).thenReturn(null);
        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenReturn(new ArrayList<>());
        Assert.assertFalse(sut.isConfigurationEnabled(kind));
        verify(this.augmenterConfigurationEnabledCache, times(1)).put(any(), any());
    }

    @Test
    public void getAugmenterConfiguration_invalid_kind() {
        Assert.assertNull(sut.getConfiguration(null));
        Assert.assertNull(sut.getConfiguration(""));
        Assert.assertNull(sut.getConfiguration("anyAuth:anySource:anyEntity"));
    }

    @Test
    public void getAugmenterConfiguration_with_configuration_in_cache() {
        String code = "anyAuth:anySource:anyEntity:1.";
        String kind = "anyAuth:anySource:anyEntity:1.0.0";
        AugmenterConfiguration configuration = new AugmenterConfiguration();
        configuration.setCode(code);
        when(this.augmenterConfigurationCache.get(eq(code))).thenReturn(configuration);
        AugmenterConfiguration configuration2 = sut.getConfiguration(kind);

        Assert.assertNotNull(configuration2);
        Assert.assertEquals(code, configuration2.getCode());
    }

    @Test
    public void getAugmenterConfiguration_with_empty_configuration_in_cache() {
        String code = "anyAuth:anySource:anyEntity:1.";
        String kind = "anyAuth:anySource:anyEntity:1.0.0";
        AugmenterConfiguration configuration = new AugmenterConfiguration();
        when(this.augmenterConfigurationCache.get(eq(code))).thenReturn(configuration);
        AugmenterConfiguration configuration2 = sut.getConfiguration(kind);

        Assert.assertNull(configuration2);
    }

    @Test
    public void getAugmenterConfiguration_with_result_from_search() throws Exception {
        Map<String, Object> data = this.getDataMap("well_configuration_record.json");
        SearchRecord searchRecord = new SearchRecord();
        searchRecord.setData(data);
        List<SearchRecord> results = Arrays.asList(searchRecord);
        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenReturn(results);
        String kind = "osdu:wks:master-data--Well:1.0.0";
        String code = "osdu:wks:master-data--Well:1.";
        AugmenterConfiguration configuration = sut.getConfiguration(kind);

        ArgumentCaptor<AugmenterConfiguration> argumentCaptor = ArgumentCaptor.forClass(AugmenterConfiguration.class);
        // If we mock the implementation of propertyConfigurationCache, it should be called once
        verify(this.augmenterConfigurationCache, times(2)).put(any(), argumentCaptor.capture());
        Assert.assertNotNull(configuration);
        Assert.assertEquals(code, configuration.getCode());
        Assert.assertEquals(code, argumentCaptor.getValue().getCode());
    }

    @Test
    public void getAugmenterConfiguration_without_result_from_search() throws Exception {
        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenReturn(new ArrayList<>());

        String kind = "osdu:wks:master-data--Well:1.0.0";
        AugmenterConfiguration configuration = sut.getConfiguration(kind);

        ArgumentCaptor<AugmenterConfiguration> argumentCaptor = ArgumentCaptor.forClass(AugmenterConfiguration.class);
        verify(this.augmenterConfigurationCache, times(1)).put(any(), argumentCaptor.capture());
        Assert.assertNull(configuration);
        Assert.assertNull(argumentCaptor.getValue().getCode());
    }

    @Test
    public void getExtendedProperties_from_children_objects() throws Exception {
        AugmenterConfiguration propertyConfigurations = getConfiguration("wellbore_configuration_record.json");
        Map<String, Object> originalDataMap = getDataMap("wellbore_data.json");
        String jsonText = getJsonFromFile("welllog_search_records.json");
        Type type = new TypeToken<List<SearchRecord>>() {}.getType();
        List<SearchRecord> childrenRecords = gson.fromJson(jsonText, type);
        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenReturn(childrenRecords);

        Map<String, Object> extendedProperties = this.sut.getExtendedProperties("anyId", originalDataMap, propertyConfigurations);
        Map<String, Object> expectedExtendedProperties = getDataMap("wellbore_extended_data.json");
        verifyMap(expectedExtendedProperties, extendedProperties);
        List<String> wellLogs = (List<String>)extendedProperties.getOrDefault("WellLogs", null);
        Assert.assertNotNull(wellLogs);
        Assert.assertEquals(88, wellLogs.size());
        verify(jobStatus, times(0)).addOrUpdateRecordStatus(anyString(), eq(IndexingStatus.WARN), eq(HttpStatus.SC_BAD_REQUEST), anyString());
    }

    @Test
    public void getExtendedProperties_from_children_objects_with_small_list_size() throws Exception {
        // Set small threshold on the size of extended list value
        this.sut.maxSizeOfExtendedListValue = 50;
        AugmenterConfiguration propertyConfigurations = getConfiguration("wellbore_configuration_record.json");
        Map<String, Object> originalDataMap = getDataMap("wellbore_data.json");
        String jsonText = getJsonFromFile("welllog_search_records.json");
        Type type = new TypeToken<List<SearchRecord>>() {}.getType();
        List<SearchRecord> childrenRecords = gson.fromJson(jsonText, type);
        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenReturn(childrenRecords);

        Map<String, Object> extendedProperties = this.sut.getExtendedProperties("anyId", originalDataMap, propertyConfigurations);
        List<String> wellLogs = (List<String>)extendedProperties.getOrDefault("WellLogs", null);
        // WellLogs property should be removed because of oversize
        Assert.assertNull(wellLogs);
        verify(jobStatus, times(1)).addOrUpdateRecordStatus(anyString(), eq(IndexingStatus.WARN), eq(HttpStatus.SC_BAD_REQUEST), anyString());
    }

    @Test
    public void getExtendedProperties_from_self_and_parent_objects() throws Exception {
        AugmenterConfiguration propertyConfigurations = getConfiguration("welllog_configuration_record.json");
        Map<String, Object> originalDataMap = getDataMap("welllog_original_data.json");

        List<SearchRecord> records = new ArrayList<>();
        Map<String, Object> relatedObjectData = getDataMap("wellbore_data.json");
        SearchRecord record = new SearchRecord();
        record.setId("opendes:master-data--Wellbore:nz-100000113552");
        record.setData(relatedObjectData);
        records.add(record);

        relatedObjectData = getDataMap("organisation_data1.json");
        record = new SearchRecord();
        record.setId("opendes:master-data--Organisation:BigOil-Department-SeismicInterpretation");
        record.setData(relatedObjectData);
        records.add(record);

        relatedObjectData = getDataMap("organisation_data2.json");
        record = new SearchRecord();
        record.setId("opendes:master-data--Organisation:BigOil-Department-SeismicProcessing");
        record.setData(relatedObjectData);
        records.add(record);

        when(this.searchClient.search(any(List.class), any(), any(), any(), anyInt())).thenReturn(records);

        Map<String, Object> extendedProperties = this.sut.getExtendedProperties("anyId", originalDataMap, propertyConfigurations);
        Map<String, Object> expectedExtendedProperties = getDataMap("welllog_extended_data.json");
        verifyMap(expectedExtendedProperties, extendedProperties);
    }

    @Test
    public void getExtendedProperties_value_extract_match() throws Exception {
        AugmenterConfiguration propertyConfigurations = getConfiguration("value_extraction_match_configuration_record.json");
        Map<String, Object> originalDataMap = getDataMap("value_extraction_match_data_record.json");

        List<SearchRecord> records = new ArrayList<>();
        SearchRecord record = new SearchRecord();
        record.setData(originalDataMap);
        records.add(record);

        when(this.searchClient.search(any(List.class), any(), any(), any(), anyInt())).thenReturn(records);

        Map<String, Object> extendedProperties = this.sut.getExtendedProperties("anyId", originalDataMap, propertyConfigurations);
        Double groundLevel = (Double)extendedProperties.getOrDefault("DCGroundlevel", null);
        Assert.assertNotNull(groundLevel);
        Assert.assertEquals(1143, groundLevel, 0.00001);

        Double totalDepth = (Double)extendedProperties.getOrDefault("DCTotalDepth", null);
        Assert.assertNotNull(totalDepth);
        Assert.assertEquals(4977, totalDepth, 0.00001);
    }

    private void verifyMap(Map<String, Object> expectedExtendedProperties, Map<String, Object> extendedProperties) {
        Assert.assertEquals(expectedExtendedProperties.size(), extendedProperties.size());

        for(Map.Entry<String, Object> entry: expectedExtendedProperties.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            Assert.assertTrue(extendedProperties.containsKey(name));
            if(value instanceof String) {
                Assert.assertEquals(value, extendedProperties.get(name));
            }
            else if(value instanceof List) {
                List<String> expectedValues = ((List<String>)value).stream().sorted().toList();
                List<String> values = ((List<String>)extendedProperties.get(name)).stream().sorted().toList();
                Assert.assertEquals(expectedValues.size(), values.size());
                for(int i = 0; i < expectedValues.size(); i++) {
                    Assert.assertEquals(expectedValues.get(i), values.get(i));
                }
            }
            else {
                Assert.assertEquals(value, extendedProperties.get(name));
            }
        }
    }

    @Test
    public void getExtendedSchemaItems_from_self_and_parent_object_kind() throws JsonProcessingException {
        AugmenterConfiguration augmenterConfiguration = getConfiguration("well_configuration_record.json");
        Schema originalSchema = getSchema("well_storage_schema.json");
        Schema geoPoliticalEntitySchema = getSchema("geo_political_entity_storage_schema.json");
        String relatedObjectKind = "osdu:wks:master-data--GeoPoliticalEntity:1.";
        Map<String, Schema> relatedObjectKindSchemas = new HashMap<>();
        relatedObjectKindSchemas.put(relatedObjectKind, geoPoliticalEntitySchema);

        List<SchemaItem> extendedSchemaItems = this.sut.getExtendedSchemaItems(originalSchema, relatedObjectKindSchemas, augmenterConfiguration);
        Assert.assertEquals(3, extendedSchemaItems.size());
        SchemaItem countryNameItem = extendedSchemaItems.stream().filter(item -> item.getPath().equals("CountryNames")).findFirst().orElse(null);
        Assert.assertNotNull(countryNameItem);
        Assert.assertEquals("[]string", countryNameItem.getKind());

        SchemaItem wellUWIItem = extendedSchemaItems.stream().filter(item -> item.getPath().equals("WellUWI")).findFirst().orElse(null);
        Assert.assertNotNull(wellUWIItem);
        Assert.assertEquals("string", wellUWIItem.getKind());

        SchemaItem associatedIdentitiesItem = extendedSchemaItems.stream().filter(item -> item.getPath().equals("AssociatedIdentities")).findFirst().orElse(null);
        Assert.assertNotNull(associatedIdentitiesItem);
        Assert.assertEquals("[]string", associatedIdentitiesItem.getKind());
    }

    @Test
    public void getExtendedSchemaItems_from_multiple_object_kinds() throws JsonProcessingException {
        AugmenterConfiguration augmenterConfiguration = getConfiguration("welllog_configuration_record.json");
        Schema originalSchema = getSchema("welllog_storage_schema.json");
        Map<String, Schema> relatedObjectKindSchemas = new HashMap<>();
        Schema wellboreSchema = getSchema("wellbore_storage_schema.json");
        relatedObjectKindSchemas.put("osdu:wks:master-data--Wellbore:1.", wellboreSchema);
        Schema organisationSchema = getSchema("organisation_storage_schema.json");
        relatedObjectKindSchemas.put("osdu:wks:master-data--Organisation:1.", organisationSchema);

        String jsonText = getJsonFromFile("welllog_extended_schema_items.json");
        Type type = new TypeToken<List<SchemaItem>>() {}.getType();
        List<SchemaItem> expectedExtendedSchemaItems = gson.fromJson(jsonText, type);

        List<SchemaItem> extendedSchemaItems = this.sut.getExtendedSchemaItems(originalSchema, relatedObjectKindSchemas, augmenterConfiguration);
        Assert.assertEquals(expectedExtendedSchemaItems.size(), extendedSchemaItems.size());
        for(int i = 0; i < expectedExtendedSchemaItems.size(); i++) {
            SchemaItem expectedExtendedSchemaItem = expectedExtendedSchemaItems.get(i);
            SchemaItem extendedSchemaItem = extendedSchemaItems.get(i);
            Assert.assertEquals(expectedExtendedSchemaItem.getKind(), extendedSchemaItem.getKind());
            Assert.assertEquals(expectedExtendedSchemaItem.getPath(), extendedSchemaItem.getPath());
        }
    }

    @Test
    public void resolveConcreteKind_with_concreteKind() {
        String kind = "osdu:wks:master-data--Well:1.0.0";
        Assert.assertEquals(kind, sut.resolveConcreteKind(kind));
    }

    @Test
    public void resolveConcreteKind_with_null_empty_kind() {
        Assert.assertTrue(Strings.isNullOrEmpty(sut.resolveConcreteKind(null)));
        Assert.assertTrue(Strings.isNullOrEmpty(sut.resolveConcreteKind("")));
    }

    @Test
    public void resolveConcreteKind_with_value_in_cache() {
        String kind = "osdu:wks:master-data--Well:1.";
        String expectedKind = kind + "2.3";

        when(this.kindCache.get(any())).thenReturn(expectedKind);
        Assert.assertEquals(expectedKind, sut.resolveConcreteKind(kind));
    }

    @Test
    public void resolveConcreteKind_with_result_from_schemaService() throws UnsupportedEncodingException, URISyntaxException {
        String kind = "osdu:wks:master-data--Well:1.";
        String expectedKind = kind + "2.3";

        SchemaIdentity schemaIdentity = new SchemaIdentity();
        schemaIdentity.setAuthority("osdu");
        schemaIdentity.setSource("wks");
        schemaIdentity.setEntityType("master-data--Well");
        schemaIdentity.setSchemaVersionMajor(1);
        schemaIdentity.setSchemaVersionMinor(2);
        schemaIdentity.setSchemaVersionPatch(3);
        SchemaInfo schemaInfo = new SchemaInfo();
        schemaInfo.setSchemaIdentity(schemaIdentity);
        List<SchemaInfo> schemaInfos = Arrays.asList(schemaInfo);
        SchemaInfoResponse response = new SchemaInfoResponse();
        response.setSchemaInfos(schemaInfos);
        response.setTotalCount(schemaInfos.size());
        when(this.schemaService.getSchemaInfos(any(), any(), any(), any(), eq(null), eq(null), eq(true))).thenReturn(response);
        String latestKind = sut.resolveConcreteKind(kind);

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.kindCache, times(1)).put(any(), argumentCaptor.capture());
        Assert.assertEquals(expectedKind, latestKind);
        Assert.assertEquals(expectedKind, argumentCaptor.getValue());
    }

    @Test
    public void resolveConcreteKind_without_result_from_schemaService() throws UnsupportedEncodingException, URISyntaxException {
        String kind = "osdu:wks:master-data--Well:1.";

        SchemaInfoResponse response = new SchemaInfoResponse();
        when(this.schemaService.getSchemaInfos(any(), any(), any(), any(), eq(null), eq(null), eq(true))).thenReturn(response);
        String latestKind = sut.resolveConcreteKind(kind);

        verify(this.kindCache, times(0)).put(any(), any());
        Assert.assertNull(latestKind);
    }

    @Test
    public void cacheDataRecord_create_record() throws Exception {
        ArgumentCaptor<RecordChangeInfo> recordInfoArgumentCaptor = ArgumentCaptor.forClass(RecordChangeInfo.class);
        ArgumentCaptor<RecordData> dataMapArgumentCaptor = ArgumentCaptor.forClass(RecordData.class);
        String recordId = "anyId";
        String kind = "anyKind";
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("p1", "v1");

        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenReturn(new ArrayList<>());

        this.sut.cacheDataRecord(recordId, kind, dataMap);

        verify(this.recordChangeInfoCache, times(1)).put(any(), recordInfoArgumentCaptor.capture());
        verify(this.relatedObjectCache, times(1)).put(any(), dataMapArgumentCaptor.capture());

        Assert.assertEquals(OperationType.create.getValue(), recordInfoArgumentCaptor.getValue().getRecordInfo().getOp());
        Assert.assertEquals(1, dataMapArgumentCaptor.getValue().getData().size());
    }

    @Test
    public void cacheDataRecord_update_record() throws Exception {
        ArgumentCaptor<RecordChangeInfo> recordInfoArgumentCaptor = ArgumentCaptor.forClass(RecordChangeInfo.class);
        ArgumentCaptor<RecordData> dataMapArgumentCaptor = ArgumentCaptor.forClass(RecordData.class);
        String recordId = "anyId";
        String kind = "anyKind";
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("p1", "v1");
        Map<String, Object> previousDataMap = new HashMap<>();
        previousDataMap.put("p1", "v10");
        previousDataMap.put("p2", "v2");

        SearchRecord searchRecord = new SearchRecord();
        searchRecord.setKind(kind);
        searchRecord.setId(recordId);
        searchRecord.setData(previousDataMap);
        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenReturn(List.of(searchRecord));

        this.sut.cacheDataRecord(recordId, kind, dataMap);

        verify(this.recordChangeInfoCache, times(1)).put(any(), recordInfoArgumentCaptor.capture());
        verify(this.relatedObjectCache, times(2)).put(any(), dataMapArgumentCaptor.capture());

        RecordChangeInfo changedInfo = recordInfoArgumentCaptor.getValue();
        Assert.assertEquals(OperationType.update.getValue(), changedInfo.getRecordInfo().getOp());
        Assert.assertEquals(2, changedInfo.getUpdatedProperties().size());
        Assert.assertTrue(changedInfo.getUpdatedProperties().contains("p1"));
        Assert.assertTrue(changedInfo.getUpdatedProperties().contains("p2"));
        Assert.assertEquals(1, dataMapArgumentCaptor.getValue().getData().size());
        Assert.assertEquals("v1", dataMapArgumentCaptor.getValue().getData().get("p1"));
    }

    @Test
    public void cacheDataRecord_update_record_merge_previous_UpdateChangedInfo() throws Exception {
        ArgumentCaptor<RecordChangeInfo> recordInfoArgumentCaptor = ArgumentCaptor.forClass(RecordChangeInfo.class);
        ArgumentCaptor<RecordData> dataMapArgumentCaptor = ArgumentCaptor.forClass(RecordData.class);
        String recordId = "anyId";
        String kind = "anyKind";
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("p1", "v1");
        Map<String, Object> previousDataMap = new HashMap<>();
        previousDataMap.put("p1", "v1");
        previousDataMap.put("p2", "v2");

        RecordChangeInfo previousChangedInfo = new RecordChangeInfo();
        RecordInfo recordInfo = new RecordInfo();
        recordInfo.setId(recordId);
        recordInfo.setKind(kind);
        recordInfo.setOp(OperationType.update.getValue());
        previousChangedInfo.setRecordInfo(recordInfo);
        previousChangedInfo.setUpdatedProperties(Arrays.asList("p1"));

        SearchRecord searchRecord = new SearchRecord();
        searchRecord.setKind(kind);
        searchRecord.setId(recordId);
        searchRecord.setData(previousDataMap);

        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenReturn(List.of(searchRecord));
        when(this.recordChangeInfoCache.get(any())).thenReturn(previousChangedInfo);

        this.sut.cacheDataRecord(recordId, kind, dataMap);

        verify(this.recordChangeInfoCache, times(1)).put(any(), recordInfoArgumentCaptor.capture());
        verify(this.relatedObjectCache, times(2)).put(any(), dataMapArgumentCaptor.capture());

        RecordChangeInfo changedInfo = recordInfoArgumentCaptor.getValue();
        Assert.assertEquals(OperationType.update.getValue(), changedInfo.getRecordInfo().getOp());
        Assert.assertEquals(2, changedInfo.getUpdatedProperties().size());
        Assert.assertTrue(changedInfo.getUpdatedProperties().contains("p1"));
        Assert.assertTrue(changedInfo.getUpdatedProperties().contains("p2"));
        Assert.assertEquals(1, dataMapArgumentCaptor.getValue().getData().size());
        Assert.assertEquals("v1", dataMapArgumentCaptor.getValue().getData().get("p1"));
    }

    @Test
    public void cacheDataRecord_update_record_merge_previous_CreateChangedInfo() throws Exception {
        ArgumentCaptor<RecordChangeInfo> recordInfoArgumentCaptor = ArgumentCaptor.forClass(RecordChangeInfo.class);
        ArgumentCaptor<RecordData> dataMapArgumentCaptor = ArgumentCaptor.forClass(RecordData.class);
        String recordId = "anyId";
        String kind = "anyKind";
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("p1", "v1");
        Map<String, Object> previousDataMap = new HashMap<>();
        previousDataMap.put("p1", "v1");
        previousDataMap.put("p2", "v2");

        RecordChangeInfo previousChangedInfo = new RecordChangeInfo();
        RecordInfo recordInfo = new RecordInfo();
        recordInfo.setId(recordId);
        recordInfo.setKind(kind);
        recordInfo.setOp(OperationType.create.getValue());
        previousChangedInfo.setRecordInfo(recordInfo);

        SearchRecord searchRecord = new SearchRecord();
        searchRecord.setKind(kind);
        searchRecord.setId(recordId);
        searchRecord.setData(previousDataMap);

        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenReturn(List.of(searchRecord));
        when(this.recordChangeInfoCache.get(any())).thenReturn(previousChangedInfo);

        this.sut.cacheDataRecord(recordId, kind, dataMap);

        verify(this.recordChangeInfoCache, times(1)).put(any(), recordInfoArgumentCaptor.capture());
        verify(this.relatedObjectCache, times(2)).put(any(), dataMapArgumentCaptor.capture());

        RecordChangeInfo changedInfo = recordInfoArgumentCaptor.getValue();
        Assert.assertEquals(OperationType.create.getValue(), changedInfo.getRecordInfo().getOp());
        Assert.assertNull(changedInfo.getUpdatedProperties());
        Assert.assertEquals(1, dataMapArgumentCaptor.getValue().getData().size());
        Assert.assertEquals("v1", dataMapArgumentCaptor.getValue().getData().get("p1"));
    }

    @Test
    public void updateAssociatedRecords_updateAssociatedParentRecords_for_created_childRecord() throws Exception {
        updateAssociatedRecords_updateAssociatedParentRecords_for_created_delete(OperationType.create);
    }

    @Test
    public void updateAssociatedRecords_updateAssociatedParentRecords_for_deleted_childRecord() throws Exception {
        updateAssociatedRecords_updateAssociatedParentRecords_for_created_delete(OperationType.delete);
    }

    private void updateAssociatedRecords_updateAssociatedParentRecords_for_created_delete(OperationType operationType) throws Exception {
        updateAssociatedRecords_updateAssociatedParentRecords_baseSetup();

        // Test
        RecordChangedMessages recordChangedMessages = new RecordChangedMessages();
        recordChangedMessages.setAttributes(new HashMap<>());
        Map<String, List<String>> upsertKindIds = new HashMap<>();
        Map<String, List<String>> deleteKindIds = new HashMap<>();
        if(operationType == OperationType.create)
            upsertKindIds.put(childKind, Arrays.asList(childId));
        else
            deleteKindIds.put(childKind, Arrays.asList(childId));
        this.sut.updateAssociatedRecords(recordChangedMessages, upsertKindIds, deleteKindIds, new ArrayList<>());

        // Verify
        ArgumentCaptor<String> payloadArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.indexerQueueTaskBuilder,times(1)).createWorkerTask(payloadArgumentCaptor.capture(), any(), any());

        RecordChangedMessages newMessages = gson.fromJson(payloadArgumentCaptor.getValue(), RecordChangedMessages.class);
        Type type = new TypeToken<List<RecordInfo>>() {}.getType();
        List<RecordInfo> infoList = gson.fromJson(newMessages.getData(), type);
        Assert.assertEquals(childKind, newMessages.getAttributes().get(Constants.ANCESTRY_KINDS));
        Assert.assertEquals(1, infoList.size());
        Assert.assertEquals(parentKind, infoList.get(0).getKind());
        Assert.assertEquals(parentId, infoList.get(0).getId());
    }

    @Test
    public void updateAssociatedRecords_updateAssociatedParentRecords_for_updated_childRecord_with_extendedPropertyChanged() throws Exception {
        updateAssociatedRecords_updateAssociatedParentRecords_baseSetup();

        RecordChangeInfo recordChangeInfo = new RecordChangeInfo();
        RecordInfo recordInfo = new RecordInfo();
        recordInfo.setKind(childKind);
        recordInfo.setId(childId);
        recordInfo.setOp(OperationType.update.getValue());
        recordChangeInfo.setRecordInfo(recordInfo);
        recordChangeInfo.setUpdatedProperties(Arrays.asList("Curves[].Mnemonic", "Name"));
        when(this.recordChangeInfoCache.get(any())).thenReturn(recordChangeInfo);

        // Test
        RecordChangedMessages recordChangedMessages = new RecordChangedMessages();
        recordChangedMessages.setAttributes(new HashMap<>());
        Map<String, List<String>> upsertKindIds = new HashMap<>();
        Map<String, List<String>> deleteKindIds = new HashMap<>();
        upsertKindIds.put(childKind, Arrays.asList(childId));
        this.sut.updateAssociatedRecords(recordChangedMessages, upsertKindIds, deleteKindIds, new ArrayList<>());

        // Verify
        ArgumentCaptor<String> payloadArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.indexerQueueTaskBuilder,times(1)).createWorkerTask(payloadArgumentCaptor.capture(), any(), any());

        RecordChangedMessages newMessages = gson.fromJson(payloadArgumentCaptor.getValue(), RecordChangedMessages.class);
        Type type = new TypeToken<List<RecordInfo>>() {}.getType();
        List<RecordInfo> infoList = gson.fromJson(newMessages.getData(), type);
        Assert.assertEquals(childKind, newMessages.getAttributes().get(Constants.ANCESTRY_KINDS));
        Assert.assertEquals(1, infoList.size());
        Assert.assertEquals(parentKind, infoList.get(0).getKind());
        Assert.assertEquals(parentId, infoList.get(0).getId());
    }

    @Test
    public void updateAssociatedRecords_updateAssociatedParentRecords_for_updated_childRecord_without_extendedPropertyChanged() throws Exception {
        updateAssociatedRecords_updateAssociatedParentRecords_baseSetup();

        RecordChangeInfo recordChangeInfo = new RecordChangeInfo();
        RecordInfo recordInfo = new RecordInfo();
        recordInfo.setKind(childKind);
        recordInfo.setId(childId);
        recordInfo.setOp(OperationType.update.getValue());
        recordChangeInfo.setRecordInfo(recordInfo);
        recordChangeInfo.setUpdatedProperties(Arrays.asList("Name"));
        when(this.recordChangeInfoCache.get(any())).thenReturn(recordChangeInfo);

        // Test
        RecordChangedMessages recordChangedMessages = new RecordChangedMessages();
        recordChangedMessages.setAttributes(new HashMap<>());
        Map<String, List<String>> upsertKindIds = new HashMap<>();
        Map<String, List<String>> deleteKindIds = new HashMap<>();
        upsertKindIds.put(childKind, Arrays.asList(childId));
        this.sut.updateAssociatedRecords(recordChangedMessages, upsertKindIds, deleteKindIds, new ArrayList<>());

        // Verify
        verify(this.indexerQueueTaskBuilder,times(0)).createWorkerTask(any(), any(), any());
    }

    @Test
    public void updateAssociatedRecords_updateAssociatedParentRecords_circularIndexing() throws Exception {
        updateAssociatedRecords_updateAssociatedParentRecords_baseSetup();

        // Test
        RecordChangedMessages recordChangedMessages = new RecordChangedMessages();
        Map<String, String> attributes = new HashMap<>();
        attributes.put(Constants.ANCESTRY_KINDS, parentKind);
        recordChangedMessages.setAttributes(attributes);
        Map<String, List<String>> upsertKindIds = new HashMap<>();
        Map<String, List<String>> deleteKindIds = new HashMap<>();
        upsertKindIds.put(childKind, Arrays.asList(childId));
        this.sut.updateAssociatedRecords(recordChangedMessages, upsertKindIds, deleteKindIds, new ArrayList<>());

        // Verify
        verify(this.indexerQueueTaskBuilder,times(0)).createWorkerTask(any(), any(), any());
    }

    private void updateAssociatedRecords_updateAssociatedParentRecords_baseSetup() throws Exception {
        childKind = "osdu:wks:work-product-component--WellLog:1.0.0";
        childId = "anyChildId";
        parentKind = "osdu:wks:master-data--Wellbore:1.0.0";
        parentId = "anyParentId";

        // Setup search response for searchClient.search(...)
        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenAnswer(invocation -> {
            String kind = invocation.getArgument(0);
            return this.searchClient.search(List.of(kind), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4));
        });
        when(this.searchClient.search(any(List.class), any(), any(), any(), anyInt())).thenAnswer(invocation -> {
            String kind = ((List<String>)invocation.getArgument(0)).get(0);
            List<SearchRecord> records = new ArrayList<>();
            if (kind.toString().equals(augmenterConfigurationKind)) {
                String queryString = invocation.getArgument(1).toString();
                if (queryString.contains("ParentToChildren")) {
                    // Return of getParentChildRelatedObjectsSpecs(...)
                    Map<String, Object> dataMap = getDataMap("wellbore_configuration_record.json");
                    SearchRecord searchRecord = new SearchRecord();
                    searchRecord.setData(dataMap);
                    records.add(searchRecord);
                } else {
                    // search ChildToParent.
                    // NO result
                }
            } else {
                if(kind.toString().equals(childKind)) {
                    // Return of searchUniqueParentIds(...)
                    SearchRecord searchRecord = new SearchRecord();
                    Map<String, Object> childDataMap = new HashMap<>();
                    childDataMap.put("WellboreID", parentId);
                    searchRecord.setKind(childKind);
                    searchRecord.setId(childId);
                    searchRecord.setData(childDataMap);
                    records.add(searchRecord);
                }
                else if(kind.toString().equals("osdu:wks:master-data--Wellbore:1.*")) {
                    // Return of searchKindIds(...)
                    SearchRecord searchRecord = new SearchRecord();
                    searchRecord.setKind(parentKind);
                    searchRecord.setId(parentId);
                    records.add(searchRecord);
                }
            }
            return records;
        });

        // setup headers
        DpsHeaders dpsHeaders = new DpsHeaders();
        dpsHeaders.put(DpsHeaders.AUTHORIZATION, "testAuth");
        dpsHeaders.put(DpsHeaders.DATA_PARTITION_ID, "opendes");
        dpsHeaders.put(DpsHeaders.CORRELATION_ID, "123");
        when(this.requestInfo.getHeadersWithDwdAuthZ()).thenReturn(dpsHeaders);
    }

    @Test
    public void updateAssociatedRecords_updateAssociatedChildrenRecords_for_created_parentRecord() throws Exception {
        updateAssociatedRecords_updateAssociatedChildrenRecords_for_created_delete(OperationType.create);
    }

    @Test
    public void updateAssociatedRecords_updateAssociatedChildrenRecords_for_deleted_parentRecord() throws Exception {
        updateAssociatedRecords_updateAssociatedChildrenRecords_for_created_delete(OperationType.delete);
    }

    private void updateAssociatedRecords_updateAssociatedChildrenRecords_for_created_delete(OperationType op) throws Exception {
        updateAssociatedRecords_updateAssociatedChildrenRecords_baseSetup();

        // Test
        RecordChangedMessages recordChangedMessages = new RecordChangedMessages();
        recordChangedMessages.setAttributes(new HashMap<>());
        Map<String, List<String>> upsertKindIds = new HashMap<>();
        Map<String, List<String>> deleteKindIds = new HashMap<>();
        if(op == OperationType.create)
            upsertKindIds.put(parentKind, Arrays.asList(parentId));
        else
            deleteKindIds.put(parentKind, Arrays.asList(parentId));
        this.sut.updateAssociatedRecords(recordChangedMessages, upsertKindIds, deleteKindIds, new ArrayList<>());

        // Verify
        ArgumentCaptor<String> payloadArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.indexerQueueTaskBuilder,times(1)).createWorkerTask(payloadArgumentCaptor.capture(), any(), any());

        RecordChangedMessages newMessages = gson.fromJson(payloadArgumentCaptor.getValue(), RecordChangedMessages.class);
        Type type = new TypeToken<List<RecordInfo>>() {}.getType();
        List<RecordInfo> infoList = gson.fromJson(newMessages.getData(), type);
        Assert.assertEquals(parentKind, newMessages.getAttributes().get(Constants.ANCESTRY_KINDS));
        Assert.assertEquals(1, infoList.size());
        Assert.assertEquals(childKind, infoList.get(0).getKind());
        Assert.assertEquals(childId, infoList.get(0).getId());
    }

    @Test
    public void updateAssociatedRecords_updateAssociatedChildrenRecords_for_updated_parentRecord_with_extendedPropertyChanged() throws Exception {
        updateAssociatedRecords_updateAssociatedChildrenRecords_baseSetup();

        RecordChangeInfo recordChangeInfo = new RecordChangeInfo();
        RecordInfo recordInfo = new RecordInfo();
        recordInfo.setKind(parentKind);
        recordInfo.setId(parentId);
        recordInfo.setOp(OperationType.update.getValue());
        recordChangeInfo.setRecordInfo(recordInfo);
        recordChangeInfo.setUpdatedProperties(Arrays.asList("GeoPoliticalEntityName"));
        when(this.recordChangeInfoCache.get(any())).thenReturn(recordChangeInfo);

        // Test
        RecordChangedMessages recordChangedMessages = new RecordChangedMessages();
        recordChangedMessages.setAttributes(new HashMap<>());
        Map<String, List<String>> upsertKindIds = new HashMap<>();
        Map<String, List<String>> deleteKindIds = new HashMap<>();
        upsertKindIds.put(parentKind, Arrays.asList(parentId));
        this.sut.updateAssociatedRecords(recordChangedMessages, upsertKindIds, deleteKindIds, new ArrayList<>());

        // Verify
        ArgumentCaptor<String> payloadArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.indexerQueueTaskBuilder,times(1)).createWorkerTask(payloadArgumentCaptor.capture(), any(), any());

        RecordChangedMessages newMessages = gson.fromJson(payloadArgumentCaptor.getValue(), RecordChangedMessages.class);
        Type type = new TypeToken<List<RecordInfo>>() {}.getType();
        List<RecordInfo> infoList = gson.fromJson(newMessages.getData(), type);
        Assert.assertEquals(parentKind, newMessages.getAttributes().get(Constants.ANCESTRY_KINDS));
        Assert.assertEquals(1, infoList.size());
        Assert.assertEquals(childKind, infoList.get(0).getKind());
        Assert.assertEquals(childId, infoList.get(0).getId());
        verify(this.searchClient,times(4)).search(any(List.class), any(), any(), any(), anyInt());
    }

    @Test
    public void updateAssociatedRecords_updateAssociatedChildrenRecords_for_updated_parentRecord_without_extendedPropertyChanged() throws Exception {
        updateAssociatedRecords_updateAssociatedChildrenRecords_baseSetup();

        RecordChangeInfo recordChangeInfo = new RecordChangeInfo();
        RecordInfo recordInfo = new RecordInfo();
        recordInfo.setKind(parentKind);
        recordInfo.setId(parentId);
        recordInfo.setOp(OperationType.update.getValue());
        recordChangeInfo.setRecordInfo(recordInfo);
        recordChangeInfo.setUpdatedProperties(Arrays.asList("abc"));
        when(this.recordChangeInfoCache.get(any())).thenReturn(recordChangeInfo);

        // Test
        RecordChangedMessages recordChangedMessages = new RecordChangedMessages();
        recordChangedMessages.setAttributes(new HashMap<>());
        Map<String, List<String>> upsertKindIds = new HashMap<>();
        Map<String, List<String>> deleteKindIds = new HashMap<>();
        upsertKindIds.put(parentKind, Arrays.asList(parentId));
        this.sut.updateAssociatedRecords(recordChangedMessages, upsertKindIds, deleteKindIds, new ArrayList<>());

        // Verify
        verify(this.indexerQueueTaskBuilder,times(0)).createWorkerTask(any(), any(), any());
        verify(this.searchClient,times(4)).search(any(List.class), any(), any(), any(), anyInt());
    }

    @Test
    public void updateAssociatedRecords_updateAssociatedChildrenRecords_circularIndexing() throws Exception {
        updateAssociatedRecords_updateAssociatedChildrenRecords_baseSetup();

        // Test
        RecordChangedMessages recordChangedMessages = new RecordChangedMessages();
        Map<String, String> attributes = new HashMap<>();
        attributes.put(Constants.ANCESTRY_KINDS, childKind);
        recordChangedMessages.setAttributes(attributes);
        Map<String, List<String>> upsertKindIds = new HashMap<>();
        Map<String, List<String>> deleteKindIds = new HashMap<>();
        upsertKindIds.put(parentKind, Arrays.asList(parentId));
        this.sut.updateAssociatedRecords(recordChangedMessages, upsertKindIds, deleteKindIds, new ArrayList<>());

        // Verify
        verify(this.indexerQueueTaskBuilder,times(0)).createWorkerTask(any(), any(), any());
        verify(this.searchClient,times(2)).search(any(List.class), any(), any(), any(), anyInt());
    }

    private void updateAssociatedRecords_updateAssociatedChildrenRecords_baseSetup() throws Exception {
        childKind = "osdu:wks:master-data--Well:1.0.0";
        childId = "anyChildId";
        parentKind = "osdu:wks:master-data--GeoPoliticalEntity:1.0.0";
        parentId = "anyParentId";

        // Setup search response for searchClient.search(...)
        when(this.searchClient.search(anyString(), any(), any(), any(), anyInt())).thenAnswer(invocation -> {
            String kind = invocation.getArgument(0);
            return this.searchClient.search(List.of(kind), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4));
        });
        when(this.searchClient.search(any(List.class), any(), any(), any(), anyInt())).thenAnswer(invocation -> {
            String kind = ((List<String>)invocation.getArgument(0)).get(0);
            Query query = invocation.getArgument(1);
            List<SearchRecord> records = new ArrayList<>();
            if (kind.toString().equals(augmenterConfigurationKind)) {
                String queryString = query.toString();
                if (queryString.contains("ChildToParent") || queryString.contains("data.Code:")) {
                    // Return of getParentChildRelatedObjectsSpecs(...) or
                    // getPropertyConfigurations(...)
                    Map<String, Object> dataMap = getDataMap("well_configuration_record.json");
                    SearchRecord searchRecord = new SearchRecord();
                    searchRecord.setData(dataMap);
                    records.add(searchRecord);
                } else {
                    // Search ParentToChildren
                    // No result
                }
            }
            else if(kind.toString().contains("osdu:wks:master-data--Well:1.")) {
                // Return of searchUniqueParentIds(...)
                SearchRecord searchRecord = new SearchRecord();
                Map<String, Object> childDataMap = new HashMap<>();
                childDataMap.put("AssociatedIdentities", Arrays.asList(parentId));
                searchRecord.setKind(childKind);
                searchRecord.setId(childId);
                searchRecord.setData(childDataMap);
                records.add(searchRecord);
            }
            else {
                // This branch is a setup for test case:
                // updateAssociatedRecords_updateAssociatedChildrenRecords_circularIndexing
                throw new Exception("Unexpected search");
            }
            return records;
        });

        // setup headers
        DpsHeaders dpsHeaders = new DpsHeaders();
        dpsHeaders.put(DpsHeaders.AUTHORIZATION, "testAuth");
        dpsHeaders.put(DpsHeaders.DATA_PARTITION_ID, "opendes");
        dpsHeaders.put(DpsHeaders.CORRELATION_ID, "123");
        when(this.requestInfo.getHeadersWithDwdAuthZ()).thenReturn(dpsHeaders);
    }

    @Test
    public void getRelatedKindsOfConfigurations_returns_empty_list_when_configurationIds_is_null_or_empty() {
        List<String> relatedKinds = sut.getRelatedKindsOfConfigurations(null);
        Assert.assertTrue(relatedKinds.isEmpty());

        relatedKinds = sut.getRelatedKindsOfConfigurations(new ArrayList<>());
        Assert.assertTrue(relatedKinds.isEmpty());
    }

    @Test
    public void getRelatedKindsOfConfigurations_returns_empty_list_when_related_objects_not_in_cache() {
        when(relatedObjectCache.get(anyString())).thenReturn(null);

        List<String> relatedKinds = sut.getRelatedKindsOfConfigurations(Arrays.asList("id1", "id2"));
        Assert.assertTrue(relatedKinds.isEmpty());
    }

    @Test
    public void getRelatedKindsOfConfigurations_returns_related_kind_list() throws UnsupportedEncodingException, URISyntaxException {
        Map<String, Object> data =getDataMap("well_configuration_record.json");
        RecordData recordData = new RecordData();
        recordData.setData(data);

        when(relatedObjectCache.get(eq("well_id"))).thenReturn(recordData);
        SchemaInfoResponse response = createSchemaResponseForWell();
        when(schemaService.getSchemaInfos(eq("osdu"), eq("wks"), eq("master-data--Well"), eq("1"),
                eq(null),eq(null), eq(false))).thenReturn(response);

        List<String> relatedKinds = sut.getRelatedKindsOfConfigurations(Arrays.asList("well_id"));
        List<String> expectedRelatedKinds =
                Arrays.asList("osdu:wks:master-data--Well:1.0.0", "osdu:wks:master-data--Well:1.1.0");
        Assert.assertEquals(expectedRelatedKinds.size(), relatedKinds.size());
        for(int i = 0; i < expectedRelatedKinds.size(); i++) {
            Assert.assertEquals(expectedRelatedKinds.get(i), relatedKinds.get(i));
        }
        verify(this.augmenterConfigurationCache,times(1)).put(anyString(), any());
    }

    @Test
    public void getRelatedKindsOfConfigurations_returns_empty_list_when_schemaService_throws_exception()
            throws UnsupportedEncodingException, URISyntaxException {
        Map<String, Object> data =getDataMap("well_configuration_record.json");
        RecordData recordData = new RecordData();
        recordData.setData(data);

        when(relatedObjectCache.get(eq("well_id"))).thenReturn(recordData);
        when(schemaService.getSchemaInfos(eq("osdu"), eq("wks"), eq("master-data--Well"), eq("1"),
                eq(null),eq(null), eq(false))).thenThrow(UnsupportedEncodingException.class);

        List<String> relatedKinds = sut.getRelatedKindsOfConfigurations(Arrays.asList("well_id"));
        Assert.assertTrue(relatedKinds.isEmpty());
    }

    private SchemaInfoResponse createSchemaResponseForWell() {
        SchemaInfoResponse response = new SchemaInfoResponse();
        List<SchemaInfo> schemaInfos = new ArrayList<>();
        SchemaInfo schemaInfo = new SchemaInfo();
        SchemaIdentity schemaIdentity = new SchemaIdentity();
        schemaIdentity.setAuthority("osdu");
        schemaIdentity.setSource("wks");
        schemaIdentity.setEntityType("master-data--Well");
        schemaIdentity.setSchemaVersionMajor(1);
        schemaIdentity.setSchemaVersionMinor(0);
        schemaIdentity.setSchemaVersionPatch(0);
        schemaInfo.setSchemaIdentity(schemaIdentity);
        schemaInfos.add(schemaInfo);
        SchemaInfo schemaInfo2 = new SchemaInfo();
        SchemaIdentity schemaIdentity2 = new SchemaIdentity();
        schemaIdentity2.setAuthority("osdu");
        schemaIdentity2.setSource("wks");
        schemaIdentity2.setEntityType("master-data--Well");
        schemaIdentity2.setSchemaVersionMajor(1);
        schemaIdentity2.setSchemaVersionMinor(1);
        schemaIdentity2.setSchemaVersionPatch(0);
        schemaInfo2.setSchemaIdentity(schemaIdentity2);
        schemaInfos.add(schemaInfo2);
        response.setSchemaInfos(schemaInfos);
        return response;
    }

    private Schema getSchema(String file) {
        String jsonText = getJsonFromFile(file);
        return gson.fromJson(jsonText, Schema.class);
    }

    private AugmenterConfiguration getConfiguration(String file) throws JsonProcessingException {
        Map<String, Object> dataMap = getDataMap(file);
        ObjectMapper objectMapper = new ObjectMapper();
        String data = objectMapper.writeValueAsString(dataMap);
        AugmenterConfiguration configurations = objectMapper.readValue(data, AugmenterConfiguration.class);
        return configurations;
    }

    private Map<String, Object> getDataMap(String file) {
        String jsonText = getJsonFromFile(file);
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        return  gson.fromJson(jsonText, type);
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
}
