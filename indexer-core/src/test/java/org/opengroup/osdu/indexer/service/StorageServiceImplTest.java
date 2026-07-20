// Copyright 2017-2023, Schlumberger
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

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.opengroup.osdu.core.common.http.IHttpClientHandler;
import org.opengroup.osdu.core.common.http.IUrlFetchService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.HttpResponse;
import org.opengroup.osdu.core.common.model.indexer.IndexingStatus;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.indexer.RecordQueryResponse;
import org.opengroup.osdu.core.common.model.indexer.RecordReindexRequest;
import org.opengroup.osdu.core.common.model.indexer.Records;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.opengroup.osdu.indexer.model.XcollaborationHolder;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class StorageServiceImplTest {
    @Mock
    private IUrlFetchService urlFetchService;
    @Mock
    private IHttpClientHandler httpClientHandler;
    @Mock
    private JobStatus jobStatus;
    @Mock
    private JaxRsDpsLog log;
    @Mock
    private IRequestInfo requestInfo;
    @Mock
    private IndexerConfigurationProperties configurationProperties;
    @Mock
    private XcollaborationHolder xcollaborationHolder;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private StorageServiceImpl sut;

    private List<String> ids;
    private static final String RECORD_ID1 = "tenant1:doc:1dbf528e0e0549cab7a08f29fbfc8465";
    private static final String RECORDS_ID2 = "tenant1:doc:15e790a69beb4d789b1f979e2af2e813";

    @Before
    public void setup() {

        // Initialize retry configuration defaults (not injected by @InjectMocks)
        sut.maxRetryAttempts = 5;
        sut.baseDelayMs = 1000;
        sut.maxDelayMs = 60000;
        sut.jitterFactor = 0.5;

        String recordChangedMessages = "[{\"id\":\"tenant1:doc:1dbf528e0e0549cab7a08f29fbfc8465\",\"kind\":\"tenant1:testindexer1528919679710:well:1.0.0\",\"op\":\"purge\"}," +
                "{\"id\":\"tenant1:doc:15e790a69beb4d789b1f979e2af2e813\",\"kind\":\"tenant1:testindexer1528919679710:well:1.0.0\",\"op\":\"create\"}]";

        when(this.requestInfo.getHeadersMap()).thenReturn(new HashMap<>());
        when(this.requestInfo.getHeaders()).thenReturn(new DpsHeaders());
        when(this.requestInfo.getHeadersMapWithDwdAuthZ()).thenReturn(new HashMap<>());
        when(this.requestInfo.getHeadersWithDwdAuthZ()).thenReturn(new DpsHeaders());

        Type listType = new TypeToken<List<RecordInfo>>() {
        }.getType();

        List<RecordInfo> msgs = (new Gson()).fromJson(recordChangedMessages, listType);
        jobStatus.initialize(msgs);
        ids = Arrays.asList(RECORD_ID1, RECORDS_ID2);

        when(configurationProperties.getStorageRecordsBatchSize()).thenReturn(20);
        when(xcollaborationHolder.isFeatureEnabledAndHeaderExists()).thenReturn(false);
    }

    @Test
    public void should_parse_long_integer_values_as_integer_types() throws URISyntaxException {
        when(this.requestInfo.getHeaders()).thenReturn(new DpsHeaders());

        String body = "{\"records\":[{\"id\":\"id1\",\"kind\":\"tenant:test:test:1.0.0\",\"version\":0,\"data\":{\"long_int\":1000000000000000000000000,\"int\":123}}],\"notFound\":[],\"conversionStatuses\":[],\"missingRetryRecords\":[]}";
        Map<String, String> recordChangedMap = new HashMap<>();
        Map<String, String> validRecordKindPatchMap = new HashMap<>();
        recordChangedMap.put("id1", "tenant:test:test:1.0.0");

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(body);
        when(this.urlFetchService.sendRequest(any())).thenReturn(httpResponse);

        Records rec = sut.getRecords(Collections.singletonList("id1"), recordChangedMap, validRecordKindPatchMap);
        assertEquals(1, rec.getRecords().size());
        assertEquals("1000000000000000000000000", rec.getRecords().get(0).getData().get("long_int").toString());
        assertEquals("123", rec.getRecords().get(0).getData().get("int").toString());
    }

    @Test
    public void should_return404_givenNullData_getValidStorageRecordsTest() throws URISyntaxException {

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(null);

        when(this.urlFetchService.sendRequest(ArgumentMatchers.any())).thenReturn(httpResponse);

        should_return404_getValidStorageRecordsTest();
    }

    @Test
    public void should_return404_givenEmptyData_getValidStorageRecordsTest() throws URISyntaxException {

        String emptyDataFromStorage = "{\"records\":[],\"notFound\":[]}";

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(emptyDataFromStorage);

        when(this.urlFetchService.sendRequest(ArgumentMatchers.any())).thenReturn(httpResponse);

        should_return404_getValidStorageRecordsTest();
    }

    @Test
    public void should_returnOneValidRecords_givenValidData_getValidStorageRecordsTest() throws URISyntaxException {

        String validDataFromStorage = "{\"records\":[{\"id\":\"testid\", \"version\":1, \"kind\":\"tenant:test:test:1.0.0\"}],\"notFound\":[\"invalid1\"], \"conversionStatuses\": []}";
        List<RecordInfo> recordChangeInfos = Arrays.asList(RecordInfo.builder().id("testid").kind("tenant:test:test:1.0.0").op(OperationType.create.getValue()).build());

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(validDataFromStorage);

        when(this.urlFetchService.sendRequest(ArgumentMatchers.any())).thenReturn(httpResponse);
        Records storageRecords = this.sut.getStorageRecords(ids, recordChangeInfos);

        assertEquals(1, storageRecords.getRecords().size());
    }

    @Test
    public void should_returnOneValidRecords_givenRecord_withValidKindUpdate_getStorageRecordsTest() throws URISyntaxException {

        String validDataFromStorage = "{\"records\":[{\"id\":\"testid\", \"version\":1, \"kind\":\"tenant:test:test:1.2.0\"}],\"notFound\":[], \"conversionStatuses\": []}";
        List<RecordInfo> recordChangeInfos = Arrays.asList(RecordInfo.builder().id("testid").kind("tenant:test:test:1.2.0").op(OperationType.update.getValue()).previousVersionKind("tenant:test:test:1.1.0").build());

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(validDataFromStorage);

        when(this.urlFetchService.sendRequest(ArgumentMatchers.any())).thenReturn(httpResponse);
        Records storageRecords = this.sut.getStorageRecords(ids, recordChangeInfos);

        assertEquals(1, storageRecords.getRecords().size());
    }

    @Test
    public void should_returnZeroRecords_givenStaleMessage_getStorageRecordsTest() throws URISyntaxException {

        String validDataFromStorage = "{\"records\":[{\"id\":\"testid\", \"version\":1, \"kind\":\"tenant:test:test:1.2.0\"}],\"notFound\":[], \"conversionStatuses\": []}";
        List<RecordInfo> recordChangeInfos = Arrays.asList(RecordInfo.builder().id("testid").kind("tenant:test:test:1.1.0").op(OperationType.update.getValue()).build());

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(validDataFromStorage);

        when(this.urlFetchService.sendRequest(ArgumentMatchers.any())).thenReturn(httpResponse);
        Records storageRecords = this.sut.getStorageRecords(ids, recordChangeInfos);

        assertEquals(0, storageRecords.getRecords().size());
        verify(this.log).warning("stale records found with older kind, skipping indexing | record ids: testid");
    }

    @Test
    public void should_returnStorageRecords_givenRecordIds_getValidStorageRecordsTest() throws URISyntaxException {

        String validDataFromStorage = "{\"records\":[{\"id\":\"tenant1:doc:1dbf528e0e0549cab7a08f29fbfc8465\", \"version\":1, \"kind\":\"tenant:test:test:1.0.0\"}," +
                "{\"id\":\"tenant1:doc:15e790a69beb4d789b1f979e2af2e813\", \"version\":1, \"kind\":\"tenant:test:test:1.0.0\"}]}";

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(validDataFromStorage);

        when(configurationProperties.getStorageQueryRecordHost()).thenReturn("storageUrl");
        when(this.httpClientHandler.sendRequest(any(), any())).thenReturn(httpResponse);
        List<String> idsCopy = new ArrayList<>();
        idsCopy.addAll(ids);
        Records storageRecords = this.sut.getStorageRecords(idsCopy);

        assertEquals(2, storageRecords.getRecords().size());
        assertEquals(0, storageRecords.getNotFound().size());
    }

    @Test
    public void should_returnStorageRecords_givenRecordIds_allFound_getValidStorageRecordsTest() throws URISyntaxException {

        String validDataFromStorage = "{\"records\":[]}";

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(validDataFromStorage);

        when(configurationProperties.getStorageQueryRecordHost()).thenReturn("storageUrl");
        when(this.httpClientHandler.sendRequest(any(), any())).thenReturn(httpResponse);
        List<String> idsCopy = new ArrayList<>();
        idsCopy.addAll(ids);
        Records storageRecords = this.sut.getStorageRecords(idsCopy);

        assertEquals(0, storageRecords.getRecords().size());
        assertEquals(2, storageRecords.getNotFound().size());
    }

    @Test
    public void should_returnStorageRecords_givenRecordIds_noneFound_getValidStorageRecordsTest() throws URISyntaxException {

        String validDataFromStorage = "{\"records\":[{\"id\":\"tenant1:doc:1dbf528e0e0549cab7a08f29fbfc8465\", \"version\":1, \"kind\":\"tenant:test:test:1.0.0\"}]}";

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(validDataFromStorage);

        when(configurationProperties.getStorageQueryRecordHost()).thenReturn("storageUrl");
        when(this.httpClientHandler.sendRequest(any(), any())).thenReturn(httpResponse);
        List<String> idsCopy = new ArrayList<>();
        idsCopy.addAll(ids);
        Records storageRecords = this.sut.getStorageRecords(idsCopy);

        assertEquals(1, storageRecords.getRecords().size());
        assertEquals(1, storageRecords.getNotFound().size());
    }

    @Test
    public void should_logMissingRecord_given_storageMissedRecords() throws URISyntaxException {

        String validDataFromStorage = "{\"records\":[{\"id\":\"tenant1:doc:1dbf528e0e0549cab7a08f29fbfc8465\", \"version\":1, \"kind\":\"tenant:test:test:1.0.0\"}],\"notFound\":[]}";
        List<RecordInfo> recordChangeInfos = Arrays.asList(RecordInfo.builder().id("tenant1:doc:1dbf528e0e0549cab7a08f29fbfc8465").kind("tenant:test:test:1.0.0").op(OperationType.update.getValue()).build());

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(validDataFromStorage);

        when(this.urlFetchService.sendRequest(any())).thenReturn(httpResponse);
        Records storageRecords = this.sut.getStorageRecords(ids, recordChangeInfos);

        assertEquals(1, storageRecords.getRecords().size());
        verify(this.jobStatus).addOrUpdateRecordStatus(singletonList(RECORDS_ID2), IndexingStatus.FAIL, HttpStatus.NOT_FOUND.value(), "Partial response received from Storage service - missing records", "Partial response received from Storage service: tenant1:doc:15e790a69beb4d789b1f979e2af2e813");
    }

    @Test
    public void should_returnValidJobStatus_givenFailedUnitsConversion_processRecordChangedMessageTest() throws URISyntaxException {
        String validDataFromStorage = "{\"records\":[{\"id\":\"tenant1:doc:15e790a69beb4d789b1f979e2af2e813\", \"version\":1, \"kind\":\"tenant:test:test:1.0.0\"}],\"notFound\":[],\"conversionStatuses\":[{\"id\":\"tenant1:doc:15e790a69beb4d789b1f979e2af2e813\",\"status\":\"ERROR\",\"errors\":[\"crs conversion failed\"]}]}";
        List<RecordInfo> recordChangeInfos = Arrays.asList(RecordInfo.builder().id("tenant1:doc:15e790a69beb4d789b1f979e2af2e813").kind("tenant:test:test:1.0.0").op(OperationType.update.getValue()).build());

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(validDataFromStorage);

        when(this.urlFetchService.sendRequest(any())).thenReturn(httpResponse);
        Records storageRecords = this.sut.getStorageRecords(singletonList(RECORDS_ID2), recordChangeInfos);

        assertEquals(1, storageRecords.getRecords().size());
        verify(this.jobStatus).addOrUpdateRecordStatus(RECORDS_ID2, IndexingStatus.WARN, HttpStatus.BAD_REQUEST.value(), "crs conversion failed", String.format("record-id: %s | %s", "tenant1:doc:15e790a69beb4d789b1f979e2af2e813", "crs conversion failed"));
    }

    @Test
    public void should_returnValidResponse_givenValidRecordQueryRequest_getRecordListByKind() throws Exception {

        RecordReindexRequest recordReindexRequest = RecordReindexRequest.builder().kind("tenant:test:test:1.0.0").cursor("100").build();

        HttpResponse httpResponse = new HttpResponse();
        httpResponse.setResponseCode(200);
        httpResponse.setBody(new Gson().toJson(recordReindexRequest, RecordReindexRequest.class));

        when(this.urlFetchService.sendRequest(ArgumentMatchers.any())).thenReturn(httpResponse);

        RecordQueryResponse recordQueryResponse = this.sut.getRecordsByKind(recordReindexRequest);

        assertEquals("100", recordQueryResponse.getCursor());
        assertNull(recordQueryResponse.getResults());
    }

    @Test
    public void should_returnValidResponse_givenValidKind_getSchemaByKind() throws Exception {

        String validSchemaFromStorage = "{" +
                "  \"kind\": \"tenant:test:test:1.0.0\"," +
                "  \"schema\": [" +
                "    {" +
                "      \"path\": \"msg\"," +
                "      \"kind\": \"string\"" +
                "    }," +
                "    {" +
                "      \"path\": \"references.entity\"," +
                "      \"kind\": \"string\"" +
                "    }" +
                "  ]," +
                "  \"ext\": null" +
                "}";
        String kind = "tenant:test:test:1.0.0";

        HttpResponse httpResponse = new HttpResponse();
        httpResponse.setResponseCode(HttpStatus.OK.value());
        httpResponse.setBody(validSchemaFromStorage);

        when(this.urlFetchService.sendRequest(ArgumentMatchers.any())).thenReturn(httpResponse);

        String recordSchemaResponse = this.sut.getStorageSchema(kind);

        assertNotNull(recordSchemaResponse);
    }

    @Test
    public void should_returnNullResponse_givenAbsentKind_getSchemaByKind() throws Exception {

        String kind = "tenant:test:test:1.0.0";

        HttpResponse httpResponse = new HttpResponse();
        httpResponse.setResponseCode(HttpStatus.NOT_FOUND.value());

        when(this.urlFetchService.sendRequest(ArgumentMatchers.any())).thenReturn(httpResponse);

        String recordSchemaResponse = this.sut.getStorageSchema(kind);

        assertNull(recordSchemaResponse);
    }

    @Test
    public void should_returnOneValidRecords_givenValidData_getValidStorageRecordsWithInvalidConversionTest() throws URISyntaxException {

        String validDataFromStorage = "{\"records\":[{\"id\":\"testid\", \"version\":1, \"kind\":\"tenant:test:test:1.0.0\"}],\"notFound\":[\"invalid1\"],\"conversionStatuses\": [{\"id\":\"testid\",\"status\":\"ERROR\",\"errors\":[\"conversion error occurred\"] } ]}";
        List<RecordInfo> recordChangeInfos = Arrays.asList(RecordInfo.builder().id("testid").kind("tenant:test:test:1.0.0").op(OperationType.update.getValue()).build());

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getBody()).thenReturn(validDataFromStorage);

        when(this.urlFetchService.sendRequest(ArgumentMatchers.any())).thenReturn(httpResponse);
        Records storageRecords = this.sut.getStorageRecords(ids, recordChangeInfos);

        assertEquals(1, storageRecords.getRecords().size());

        assertEquals(1, storageRecords.getConversionStatuses().get(0).getErrors().size());

        assertEquals("conversion error occurred", storageRecords.getConversionStatuses().get(0).getErrors().get(0));
    }

    private void should_return404_getValidStorageRecordsTest() {
        List<RecordInfo> recordChangeInfos = Arrays.asList(RecordInfo.builder().id("testid").kind("tenant:test:test:1.0.0").op(OperationType.update.getValue()).build());
        try {
            this.sut.getStorageRecords(ids, recordChangeInfos);
            fail("Should throw exception");
        } catch (AppException e) {
            assertEquals(HttpStatus.NOT_FOUND.value(), e.getError().getCode());
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }
}
