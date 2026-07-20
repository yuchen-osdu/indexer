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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.RecordQueryResponse;
import org.opengroup.osdu.core.common.model.indexer.RecordReindexRequest;
import org.opengroup.osdu.core.common.model.indexer.Records;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.opengroup.osdu.indexer.model.SearchRecord;
import org.opengroup.osdu.indexer.util.IndexerQueueTaskBuilder;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.indexer.util.QueryUtil;
import org.opengroup.osdu.indexer.util.SearchClient;

import java.util.*;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;


@RunWith(MockitoJUnitRunner.class)
public class ReindexServiceTest {

    private static MockedStatic<UUID> mockedUUIDs;

    private final String cursor = "100";

    private final String correlationId = UUID.randomUUID().toString();

    @Mock
    private IndexerConfigurationProperties configurationProperties;
    @Mock
    private StorageService storageService;
    @Mock
    private SearchClient searchClient;
    @Mock
    private QueryUtil queryUtil;
    @Mock
    private IRequestInfo requestInfo;
    @Mock
    private IndexerQueueTaskBuilder indexerQueueTaskBuilder;
    @Mock
    private JaxRsDpsLog log;
    @InjectMocks
    private ReindexServiceImpl sut;

    private RecordReindexRequest recordReindexRequest;
    private RecordQueryResponse recordQueryResponse;

    private Map<String, String> httpHeaders;

    @Before
    public void setup() {
        initMocks(this);

        mockedUUIDs = mockStatic(UUID.class);

        recordReindexRequest = RecordReindexRequest.builder().kind("tenant:test:test:1.0.0").cursor(cursor).build();
        recordQueryResponse = new RecordQueryResponse();

        httpHeaders = new HashMap<>();
        httpHeaders.put(DpsHeaders.AUTHORIZATION, "testAuth");
        httpHeaders.put(DpsHeaders.CORRELATION_ID, correlationId);
        DpsHeaders standardHeaders = DpsHeaders.createFromMap(httpHeaders);
        when(requestInfo.getHeadersWithDwdAuthZ()).thenReturn(standardHeaders);
    }

    @After
    public void close() {
        mockedUUIDs.close();
    }

    @Test
    public void should_returnNull_givenNullResponseResult_reIndexRecordsTest() {
        try {
            recordQueryResponse.setResults(null);
            when(storageService.getRecordsByKind(any())).thenReturn(recordQueryResponse);

            String response = sut.reindexKind(recordReindexRequest, false, false);

            Assert.assertNull(response);
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    @Test
    public void should_returnNull_givenEmptyResponseResult_reIndexRecordsTest() {
        try {
            recordQueryResponse.setResults(new ArrayList<>());
            when(storageService.getRecordsByKind(any())).thenReturn(recordQueryResponse);

            String response = sut.reindexKind(recordReindexRequest, false, false);

            Assert.assertNull(response);
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    @Test
    public void should_returnRecordQueryRequestPayload_givenValidResponseResult_reIndexRecordsTest() {
        try {
            recordQueryResponse.setCursor(cursor);
            List<String> results = new ArrayList<>();
            results.add("test1");
            recordQueryResponse.setResults(results);

            when(configurationProperties.getStorageRecordsByKindBatchSize()).thenReturn(1);

            when(storageService.getRecordsByKind(any())).thenReturn(recordQueryResponse);

            String taskQueuePayload = sut.reindexKind(recordReindexRequest, false, false);

            Assert.assertEquals("{\"kind\":\"tenant:test:test:1.0.0\",\"cursor\":\"100\"}", taskQueuePayload);
        } catch (Exception e) {
            fail("Should not throw exception" + e.getMessage());
        }
    }

    @Test
    public void should_returnRecordChangedMessage_givenValidResponseResult_reIndexRecordsTest() {
        try {
            List<String> results = new ArrayList<>();
            results.add("test1");
            recordQueryResponse.setResults(results);
            when(storageService.getRecordsByKind(any())).thenReturn(recordQueryResponse);

            String taskQueuePayload = sut.reindexKind(recordReindexRequest, false, false);

            Assert.assertEquals(String.format("{\"data\":\"[{\\\"id\\\":\\\"test1\\\",\\\"kind\\\":\\\"tenant:test:test:1.0.0\\\",\\\"op\\\":\\\"create\\\"}]\",\"attributes\":{\"correlation-id\":\"%s\"}}", correlationId), taskQueuePayload);
        } catch (Exception e) {
            fail("Should not throw exception" + e.getMessage());
        }
    }

    @Test
    public void should_createReindexTaskForValidRecords_givenValidRecordIds_reIndexRecordsTest() throws Exception {
        DpsHeaders headers = new DpsHeaders();
        when(requestInfo.getHeadersWithDwdAuthZ()).thenReturn(headers);
        List<String> recordIds = Arrays.asList("id1", "id2");
        when(storageService.getStorageRecords(recordIds)).thenReturn(
                Records.builder().records(Collections.singletonList(Records.Entity.builder().id("id1").kind("kind1").build())).notFound(Collections.singletonList("id2")).build()
        );
        SearchRecord deletedRecord = new SearchRecord();
        deletedRecord.setKind("kind2");
        deletedRecord.setId("id2");
        List<SearchRecord> mockNotFoundRecordsSearchResponse = List.of(deletedRecord);
        when(searchClient.search(anyString(), any(), any(), any(), anyInt())).thenReturn(mockNotFoundRecordsSearchResponse);
        Records records = sut.reindexRecords(recordIds);
        Assert.assertEquals(1, records.getRecords().size());
        Assert.assertEquals(1, records.getNotFound().size());
        verify(indexerQueueTaskBuilder).createWorkerTask("{\"data\":\"[{\\\"id\\\":\\\"id1\\\",\\\"kind\\\":\\\"kind1\\\",\\\"op\\\":\\\"create\\\"}]\",\"attributes\":{}}", 0L, headers);
        verify(indexerQueueTaskBuilder).createWorkerTask("{\"data\":\"[{\\\"id\\\":\\\"id2\\\",\\\"kind\\\":\\\"kind2\\\",\\\"op\\\":\\\"delete\\\"}]\",\"attributes\":{}}", 0L, headers);
    }
}
