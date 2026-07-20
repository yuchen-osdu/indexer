/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/
package org.opengroup.osdu.indexer.ibm.service;
//package org.opendes.indexer.service;
//
//import com.google.gson.Gson;
//import com.google.gson.reflect.TypeToken;
//import org.elasticsearch.action.bulk.BulkItemResponse;
//import org.elasticsearch.action.bulk.BulkResponse;
//import org.elasticsearch.client.RequestOptions;
//import org.elasticsearch.client.RestHighLevelClient;
//import org.junit.Before;
//import org.junit.Ignore;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.Spy;
//import org.opendes.client.api.DpsHeaders;
//import org.opendes.core.logging.JaxRsDpsLog;
//import org.opendes.core.model.DeploymentEnvironment;
//import org.opendes.core.model.RecordChangedMessages;
//import org.opendes.core.service.IndicesService;
//import org.opendes.core.util.Config;
//import org.opendes.core.util.ElasticClientHandler;
//import org.opendes.core.util.ElasticIndexNameResolver;
//import org.opendes.core.util.HeadersUtil;
//import org.opendes.indexer.logging.AuditLogger;
//import org.opendes.indexer.model.*;
//import org.opendes.indexer.publish.IPublisher;
//import org.opendes.indexer.util.IRequestInfo;
//import org.opendes.indexer.util.IndexerQueueTaskBuilder;
//import org.opendes.indexer.util.JobStatus;
//import org.opendes.indexer.util.RecordInfo;
//import org.powermock.core.classloader.annotations.PrepareForTest;
//import org.springframework.context.annotation.Lazy;
//import org.springframework.test.context.junit4.SpringRunner;
//
//import jakarta.inject.Inject;
//import java.io.IOException;
//import java.lang.reflect.Type;
//import java.util.*;
//
//import static java.util.Collections.singletonList;
//import static org.junit.Assert.*;
//import static org.mockito.Matchers.any;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//import static org.powermock.api.mockito.PowerMockito.mock;
//import static org.powermock.api.mockito.PowerMockito.mockStatic;
//
//@Ignore
//@RunWith(SpringRunner.class)
//@PrepareForTest({RestHighLevelClient.class, BulkResponse.class, StorageAcl.class, HeadersUtil.class, Config.class})
//public class IndexerServiceTest {
//
//    private final String pubsubMsg = "[{\"id\":\"tenant1:doc:test1\",\"kind\":\"tenant1:testindexer1:well:1.0.0\",\"op\":\"update\"}," +
//            "{\"id\":\"tenant1:doc:test2\",\"kind\":\"tenant1:testindexer2:well:1.0.0\",\"op\":\"create\"}]";
//    private final String kind1 = "tenant1:testindexer1:well:1.0.0";
//    private final String kind2 = "tenant1:testindexer2:well:1.0.0";
//    private final String recordId1 = "tenant1:doc:test1";
//    private final String recordId2 = "tenant1:doc:test2";
//    private final String failureMassage = "test failure";
//
//    @Mock
//    private IndexSchemaService indexSchemaService;
//    @Mock
//    private IndicesService indicesService;
//    @Mock
//    private IndexerMappingService indexerMappingService;
//    @Mock
//    private StorageService storageService;
//    @Mock
//    private IPublisher publisherImpl;
//    @Mock
//    private RestHighLevelClient restHighLevelClient;
//    @Mock
//    private ElasticClientHandler elasticClientHandler;
//    @Mock
//    private BulkResponse bulkResponse;
//    @Mock
//    private IRequestInfo requestInfo;
//    @Mock
//    private ElasticIndexNameResolver elasticIndexNameResolver;
//    @Mock
//    private AttributeParsingServiceImpl attributeParsingServiceImpl;
//    @Mock
//    private IndexerQueueTaskBuilder indexerQueueTaskBuilder;
//    @Mock
//    private JaxRsDpsLog log;
//    @Mock
//    private AuditLogger auditLogger;
//    @InjectMocks
//    private IndexerServiceImpl sut;
//    @InjectMocks @Spy
//    private JobStatus jobStatus = new JobStatus();
//
//    @Inject
//    @Lazy
//    private DpsHeaders dpsHeaders;
//    private RecordChangedMessages recordChangedMessages;
//    private List<RecordInfo> recordInfos;
//
//    @Before
//    public void setup() throws IOException {
//
//        mockStatic(StorageAcl.class);
//        mockStatic(Config.class);
//
//        when(Config.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
//        when(Config.getElasticClusterName()).thenReturn("CLUSTER");
//        when(Config.getElasticServerAddress()).thenReturn("testsite");
//
//        dpsHeaders = new DpsHeaders();
//        dpsHeaders.put(AppEngineHeaders.TASK_QUEUE_RETRY_COUNT, "1");
//        dpsHeaders.put(DpsHeaders.AUTHORIZATION, "testAuth");
//        when(requestInfo.getHeaders()).thenReturn(dpsHeaders);
//        when(requestInfo.getHeadersMapWithDwdAuthZ()).thenReturn(dpsHeaders.getHeaders());
//
//        Type listType = new TypeToken<List<RecordInfo>>() {}.getType();
//        recordInfos = (new Gson()).fromJson(pubsubMsg, listType);
//
//        when(elasticClientHandler.createRestClient()).thenReturn(restHighLevelClient);
//        when(restHighLevelClient.bulk(any(), any(RequestOptions.class))).thenReturn(bulkResponse);
//
//        BulkItemResponse[] responses = new BulkItemResponse[]{prepareResponseFail(), prepareResponseSuccess()};
//        when(bulkResponse.getItems()).thenReturn(responses);
//        Map<String, String> attr = new HashMap<>();
//        attr.put(DpsHeaders.ACCOUNT_ID, "slb");
//        recordChangedMessages = RecordChangedMessages.builder().attributes(attr).messageId("xxxx").publishTime("2000-01-02T10:10:44+0000").data("{}").build();
//        when(StorageAcl.flattenAcl(any())).thenReturn(null);
//    }
//
//    @Test
//    public void should_returnNull_givenEmptyJobSubInfo_processRecordChangedMessageTest() throws Exception {
//        JobStatus jobStatus = this.sut.processRecordChangedMessages(recordChangedMessages, new ArrayList<>());
//
//        assertNull(jobStatus);
//    }
//
//    @Test
//    public void should_returnValidJobStatus_givenNullSchema_processRecordChangedMessageTest() {
//        try {
//            indexSchemaServiceMock(kind1, null);
//            indexSchemaServiceMock(kind2, null);
//            List<ConversionStatus> conversionStatus = new LinkedList<>();
//            List<Records.Entity> validRecords = new ArrayList<>();
//            Map<String, Object> storageData = new HashMap<>();
//            storageData.put("schema1", "test-value");
//            storageData.put("schema2", "test-value");
//            storageData.put("schema3", "test-value");
//            storageData.put("schema4", "test-value");
//            storageData.put("schema5", "test-value");
//            storageData.put("schema6", "test-value");
//            validRecords.add(Records.Entity.builder().id(recordId2).kind(kind2).data(storageData).build());
//            Records storageRecords = Records.builder().records(validRecords).conversionStatuses(conversionStatus).build();
//
//            when(storageService.getStorageRecords(any())).thenReturn(storageRecords);
//            when(indicesService.createIndex(any(), any(), any(), any(), any())).thenReturn(true);
//
//            JobStatus jobStatus = this.sut.processRecordChangedMessages(recordChangedMessages, recordInfos);
//
//            assertEquals(2, jobStatus.getStatusesList().size());
//            assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.FAIL).size());
//            assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.WARN).size());
//        } catch (Exception e) {
//            fail("Should not throw this exception" + e.getMessage());
//        }
//    }
//
//    @Test
//    public void should_returnValidJobStatus_givenFailedUnitsConversion_processRecordChangedMessageTest() {
//        try {
//            indexSchemaServiceMock(kind1, null);
//            indexSchemaServiceMock(kind2, null);
//            List<ConversionStatus> conversionStatuses = new LinkedList<>();
//            List<String> status=new ArrayList<>();
//            status.add("crs bla bla");
//            ConversionStatus conversionStatus=ConversionStatus.builder().status("ERROR").errors(status).id(recordId2).build();
//            conversionStatuses.add(conversionStatus);
//            List<Records.Entity> validRecords = new ArrayList<>();
//            Map<String, Object> storageData = new HashMap<>();
//            storageData.put("schema1", "test-value");
//            storageData.put("schema2", "test-value");
//            storageData.put("schema3", "test-value");
//            storageData.put("schema4", "test-value");
//            storageData.put("schema5", "test-value");
//            storageData.put("schema6", "test-value");
//            validRecords.add(Records.Entity.builder().id(recordId2).kind(kind2).data(storageData).build());
//            Records storageRecords = Records.builder().records(validRecords).conversionStatuses(conversionStatuses).build();
//
//            when(storageService.getStorageRecords(any())).thenReturn(storageRecords);
//            when(indicesService.createIndex(any(), any(), any(), any(), any())).thenReturn(true);
//
//            JobStatus jobStatus = this.sut.processRecordChangedMessages(recordChangedMessages, recordInfos);
//
//            assertEquals(2, jobStatus.getStatusesList().size());
//            assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.FAIL).size());
//            assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.WARN).size());
//            assertTrue(jobStatus.getJobStatusByRecordId(jobStatus.getIdsByIndexingStatus(IndexingStatus.WARN).get(0)).getIndexProgress().getTrace().contains("crs bla bla"));
//        } catch (Exception e) {
//            fail("Should not throw this exception" + e.getMessage());
//        }
//    }
//
//    @Test
//    public void should_returnValidJobStatus_givenNullSchemaForARecord_processRecordChangedMessageTest() {
//        try {
//            List<Records.Entity> validRecords = new ArrayList<>();
//            List<ConversionStatus> conversionStatus = new LinkedList<>();
//            Map<String, Object> storageData = new HashMap<>();
//            storageData.put("schema1", "test-value");
//            storageData.put("schema2", "test-value");
//            storageData.put("schema3", "test-value");
//            storageData.put("schema4", "test-value");
//            storageData.put("schema5", "test-value");
//            storageData.put("schema6", "test-value");
//            validRecords.add(Records.Entity.builder().id(recordId2).kind(kind2).data(storageData).build());
//            Records storageRecords = Records.builder().records(validRecords).conversionStatuses(conversionStatus).build();
//            when(storageService.getStorageRecords(any())).thenReturn(storageRecords);
//
//            Map<String, String> schema = createSchema();
//            indexSchemaServiceMock(kind1, schema);
//            indexSchemaServiceMock(kind2, null);
//            when(elasticIndexNameResolver.getIndexNameFromKind(kind2)).thenReturn("tenant1-testindexer2-well-1.0.0");
//            when(indicesService.createIndex(any(), any(), any(), any(), any())).thenReturn(true);
//            JobStatus jobStatus = sut.processRecordChangedMessages(recordChangedMessages, recordInfos);
//
//            assertEquals(2, jobStatus.getStatusesList().size());
//            assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.FAIL).size());
//            assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.WARN).size());
//            assertEquals("Indexed Successfully", jobStatus.getStatusesList().get(1).getIndexProgress().getTrace().pop());
//            assertEquals("schema not found", jobStatus.getStatusesList().get(1).getIndexProgress().getTrace().pop());
//        } catch (Exception e) {
//            fail("Should not throw this exception" + e.getMessage());
//        }
//    }
//
//    @Test
//    public void should_returnValidJobStatus_givenValidCreateAndUpdateRecords_processRecordChangedMessagesTest() {
//        try {
//            Map<String, Object> storageData = new HashMap<>();
//            storageData.put("schema1", "test-value");
//            List<ConversionStatus> conversionStatus = new LinkedList<>();
//            List<Records.Entity> validRecords = new ArrayList<>();
//            validRecords.add(Records.Entity.builder().id(recordId2).kind(kind2).data(storageData).build());
//            Records storageRecords = Records.builder().records(validRecords).conversionStatuses(conversionStatus).build();
//
//            when(storageService.getStorageRecords(any())).thenReturn(storageRecords);
//            when(indicesService.createIndex(any(), any(), any(), any(), any())).thenReturn(true);
//            Map<String, String> schema = createSchema();
//            indexSchemaServiceMock(kind2, schema);
//            indexSchemaServiceMock(kind1, null);
//            JobStatus jobStatus = sut.processRecordChangedMessages(recordChangedMessages, recordInfos);
//
//            assertEquals(2, jobStatus.getStatusesList().size());
//            assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.FAIL).size());
//            assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.SUCCESS).size());
//        } catch (Exception e) {
//            fail("Should not throw this exception" + e.getMessage());
//        }
//    }
//
//    @Test
//    public void should_properlyUpdateAuditLogs_givenValidCreateAndUpdateRecords() {
//        try {
//            Map<String, Object> storageData = new HashMap<>();
//            List<ConversionStatus> conversionStatus = new LinkedList<>();
//
//            storageData.put("schema1", "test-value");
//            List<Records.Entity> validRecords = new ArrayList<>();
//            validRecords.add(Records.Entity.builder().id(recordId2).kind(kind2).data(storageData).build());
//            Records storageRecords = Records.builder().records(validRecords).conversionStatuses(conversionStatus).build();
//
//            when(this.storageService.getStorageRecords(any())).thenReturn(storageRecords);
//            when(this.indicesService.createIndex(any(), any(), any(), any(), any())).thenReturn(true);
//            Map<String, String> schema = createSchema();
//            indexSchemaServiceMock(kind2, schema);
//            indexSchemaServiceMock(kind1, null);
//            JobStatus jobStatus = this.sut.processRecordChangedMessages(recordChangedMessages, recordInfos);
//
//            assertEquals(2, jobStatus.getStatusesList().size());
//            assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.FAIL).size());
//            assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.SUCCESS).size());
//
//            verify(this.auditLogger).indexCreateRecordSuccess(singletonList("RecordStatus(id=tenant1:doc:test2, kind=tenant1:testindexer2:well:1.0.0, operationType=create, status=SUCCESS)"));
//            verify(this.auditLogger).indexUpdateRecordFail(singletonList("RecordStatus(id=tenant1:doc:test1, kind=tenant1:testindexer1:well:1.0.0, operationType=update, status=FAIL)"));
//        } catch (Exception e) {
//            fail("Should not throw this exception" + e.getMessage());
//        }
//    }
//
//    private BulkItemResponse prepareResponseFail() {
//        BulkItemResponse responseFail = mock(BulkItemResponse.class);
//        when(responseFail.isFailed()).thenReturn(true);
//        when(responseFail.getFailureMessage()).thenReturn(failureMassage);
//        when(responseFail.getId()).thenReturn(recordId1);
//        when(responseFail.getFailure()).thenReturn(new BulkItemResponse.Failure("failure index", "failure type", "failure id", new Exception("test failure")));
//        return responseFail;
//    }
//
//    private BulkItemResponse prepareResponseSuccess() {
//        BulkItemResponse responseSuccess = mock(BulkItemResponse.class);
//        when(responseSuccess.getId()).thenReturn(recordId2);
//        return responseSuccess;
//    }
//
//    private void indexSchemaServiceMock(String kind, Map<String, String> schema) {
//        if (schema == null) {
//            IndexSchema indexSchema = IndexSchema.builder().kind(kind).dataSchema(null).build();
//            when(indexSchemaService.getIndexerInputSchema(kind)).thenReturn(indexSchema);
//        } else {
//            IndexSchema indexSchema = IndexSchema.builder().kind(kind).dataSchema(schema).build();
//            when(indexSchemaService.getIndexerInputSchema(kind)).thenReturn(indexSchema);
//        }
//    }
//
//    private Map<String, String> createSchema() {
//        Map<String, String> schema = new HashMap<>();
//        schema.put("schema1", "keyword");
//        schema.put("schema2", "boolean");
//        schema.put("schema3", "date");
//        schema.put("schema6", "object");
//        return schema;
//    }
//}
