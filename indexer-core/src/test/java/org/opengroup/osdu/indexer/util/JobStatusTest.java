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

package org.opengroup.osdu.indexer.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.indexer.IndexingStatus;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.indexer.RecordStatus;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.indexer.Records;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
public class JobStatusTest {

    private final String recordChangedMessages = "[{\"id\":\"tenant1:doc:test1\",\"kind\":\"tenant1:testindexer1:well:1.0.0\",\"op\":\"purge\"}," +
            "{\"id\":\"tenant1:doc:test2\",\"kind\":\"tenant1:testindexer12:well:1.0.0\",\"op\":\"create\"}]";

    @Mock
    private JaxRsDpsLog log;
    @InjectMocks
    private JobStatus sut;

    @Test
    public void should_create_emptyStatusList_given_emptyPubSubInfoList_ConstructorTest() {
        List<RecordInfo> recordInfos = new ArrayList<>();
        this.sut.initialize(recordInfos);
        assertEquals(0, this.sut.getStatusesList().size());
    }

    @Test
    public void should_create_twoElementsStatusList_given_twoElementsPubSubInfoList_ConstructorTest() {
        Type listType = new TypeToken<List<RecordInfo>>() {}.getType();
        List<RecordInfo> recordInfos = (new Gson()).fromJson(recordChangedMessages, listType);
        this.sut.initialize(recordInfos);
        assertEquals(2, this.sut.getStatusesList().size());
    }

    @Test
    public void should_get_kind_given_twoElementsPubSubInfoList() {
        Type listType = new TypeToken<List<RecordInfo>>() {}.getType();
        List<RecordInfo> recordInfos = (new Gson()).fromJson(recordChangedMessages, listType);
        this.sut.initialize(recordInfos);

        String kind = this.sut.getRecordKindById("tenant1:doc:test2");
        assertNotNull(kind);
        assertEquals(kind, "tenant1:testindexer12:well:1.0.0");
    }


    @Test
    public void should_return_emptyStatusList_given_emptyIdCollection_addOrUpdateRetryRecordStatusTest() {
        this.sut.addOrUpdateRecordStatus(new ArrayList<>(), IndexingStatus.SUCCESS, 1, "");

        assertEquals(0, this.sut.getStatusesList().size());
    }


    @Test
    public void should_return_emptyStatusList_given_nullIdCollection_addOrUpdateRecordStatusTest() {
        this.sut.addOrUpdateRecordStatus((Collection<String>) null, IndexingStatus.SUCCESS, 1, "");

        assertEquals(0, this.sut.getStatusesList().size());
    }

    @Test
    public void should_return_emptyStatusList_given_emptyIdCollection_addOrUpdateRecordStatusTest() {
        this.sut.addOrUpdateRecordStatus(new ArrayList<>(), IndexingStatus.SUCCESS, 1, "");

        assertEquals(0, this.sut.getStatusesList().size());
    }

    @Test
    public void should_return_fourElementsStatusList_given_fourValidIds_addOrUpdateRecordStatusTest() {
        List<String> invalidRecords = new ArrayList<>();
        invalidRecords.add("0001abc@#$");
        invalidRecords.add("0001abc@#$...");
        invalidRecords.add("0001abc@#$/01");
        invalidRecords.add("0001abc@#$.../02");
        Records records = Records.builder().notFound(invalidRecords).build();
        Collection<String> ids = records.getNotFound();

        this.sut.addOrUpdateRecordStatus(ids, IndexingStatus.SUCCESS, 1, "");
        assertEquals(4, this.sut.getStatusesList().size());
    }

    @Test
    public void should_not_updateMessage_given_nullOrEmptyMessage_updateRecordStatusTest() {
        JobStatus jobStatus = insertTestCasesIntoJobStatus();
        jobStatus.addOrUpdateRecordStatus("success1", IndexingStatus.FAIL, 1, null);
        assertEquals(4, jobStatus.getIdsByIndexingStatus(IndexingStatus.FAIL).size());
        assertEquals("", jobStatus.getStatusesList().get(1).getLatestTrace());
        jobStatus.addOrUpdateRecordStatus("success2", IndexingStatus.SKIP, 1, "");
        assertEquals(5, jobStatus.getIdsByIndexingStatus(IndexingStatus.SKIP).size());
        assertEquals("", jobStatus.getStatusesList().get(2).getLatestTrace());
    }

    @Test
    public void should_updateMessage_given_validMessage_updateRecordStatusTest() {
        JobStatus jobStatus = insertTestCasesIntoJobStatus();
        String statusChangeMessage = "unit test status change";
        jobStatus.addOrUpdateRecordStatus("success1", IndexingStatus.FAIL, 1, statusChangeMessage);
        assertEquals(statusChangeMessage, jobStatus.getStatusesList().get(1).getLatestTrace());
    }

    @Test
    public void should_not_updateStatus_given_higherStatus_updateRecordStatusTest() {
        JobStatus jobStatus = insertTestCasesIntoJobStatus();
        String statusChangeMessage = "unit test status change";
        jobStatus.addOrUpdateRecordStatus("fail1", IndexingStatus.SUCCESS, 1, statusChangeMessage);
        assertEquals(2, jobStatus.getIdsByIndexingStatus(IndexingStatus.SUCCESS).size());
        assertEquals(3, jobStatus.getIdsByIndexingStatus(IndexingStatus.FAIL).size());
        assertEquals(statusChangeMessage, jobStatus.getStatusesList().get(3).getLatestTrace());
    }

    @Test
    public void should_updateStatus_given_lowerStatus_updateRecordStatusTest() {
        JobStatus jobStatus = insertTestCasesIntoJobStatus();
        String statusChangeMessage = "unit test status change";
        jobStatus.addOrUpdateRecordStatus("success1", IndexingStatus.SKIP, 1, statusChangeMessage);
        assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.SUCCESS).size());
        assertEquals(5, jobStatus.getIdsByIndexingStatus(IndexingStatus.SKIP).size());
        assertEquals(statusChangeMessage, jobStatus.getStatusesList().get(1).getLatestTrace());
    }

    @Test
    public void should_returnValidResponse_getIdsByIndexingStatusTest() {
        JobStatus jobStatus = insertTestCasesIntoJobStatus();
        assertEquals(1, jobStatus.getIdsByIndexingStatus(IndexingStatus.PROCESSING).size());
        assertEquals(2, jobStatus.getIdsByIndexingStatus(IndexingStatus.SUCCESS).size());
        assertEquals(3, jobStatus.getIdsByIndexingStatus(IndexingStatus.FAIL).size());
        assertEquals(4, jobStatus.getIdsByIndexingStatus(IndexingStatus.SKIP).size());
    }

    @Test
    public void should_returnValidResponse_finalizeRecordStatusTest() {

        String recordChangedMessages = "[{\"id\":\"tenant1:doc:test1\",\"kind\":\"tenant1:testindexer1:well:1.0.0\",\"op\":\"create\"}," +
                "{\"id\":\"tenant1:doc:test2\",\"kind\":\"tenant1:testindexer1:well:1.0.0\",\"op\":\"create\"}," +
                "{\"id\":\"tenant1:doc:test3\",\"kind\":\"tenant1:testindexer1:well:1.0.0\",\"op\":\"purge\"}," +
                "{\"id\":\"tenant1:doc:test4\",\"kind\":\"tenant1:testindexer12:well:1.0.0\",\"op\":\"update\"}]";
        Type listType = new TypeToken<List<RecordInfo>>() {}.getType();
        List<RecordInfo> recordInfos = (new Gson()).fromJson(recordChangedMessages, listType);
        this.sut.initialize(recordInfos);

        this.sut.addOrUpdateRecordStatus("tenant1:doc:test1", IndexingStatus.PROCESSING, 1, "");
        this.sut.addOrUpdateRecordStatus("tenant1:doc:test2", IndexingStatus.SUCCESS, 200, "");
        this.sut.addOrUpdateRecordStatus("tenant1:doc:test3", IndexingStatus.FAIL, 500, "");
        this.sut.addOrUpdateRecordStatus("tenant1:doc:test4", IndexingStatus.SKIP, 404, "");

        String finalizeMessage = "unit test force fail";
        this.sut.finalizeRecordStatus(finalizeMessage);
        assertEquals(0, this.sut.getIdsByIndexingStatus(IndexingStatus.PROCESSING).size());
        assertEquals(1, this.sut.getIdsByIndexingStatus(IndexingStatus.SUCCESS).size());
        assertEquals(2, this.sut.getIdsByIndexingStatus(IndexingStatus.FAIL).size());
        assertEquals(1, this.sut.getIdsByIndexingStatus(IndexingStatus.SKIP).size());
        assertEquals(finalizeMessage, this.sut.getStatusesList().get(0).getLatestTrace());
    }

    private JobStatus insertTestCasesIntoJobStatus() {
        JobStatus jobStatus = new JobStatus();
        jobStatus.addOrUpdateRecordStatus("processing1", IndexingStatus.PROCESSING, 1, "");
        jobStatus.addOrUpdateRecordStatus("success1", IndexingStatus.SUCCESS, 1, "");
        jobStatus.addOrUpdateRecordStatus("success2", IndexingStatus.SUCCESS, 1, "");
        jobStatus.addOrUpdateRecordStatus("fail1", IndexingStatus.FAIL, 1, "");
        jobStatus.addOrUpdateRecordStatus("fail2", IndexingStatus.FAIL, 1, "");
        jobStatus.addOrUpdateRecordStatus("fail3", IndexingStatus.FAIL, 1, "");
        jobStatus.addOrUpdateRecordStatus("skipped1", IndexingStatus.SKIP, 1, "");
        jobStatus.addOrUpdateRecordStatus("skipped2", IndexingStatus.SKIP, 1, "");
        jobStatus.addOrUpdateRecordStatus("skipped3", IndexingStatus.SKIP, 1, "");
        jobStatus.addOrUpdateRecordStatus("skipped4", IndexingStatus.SKIP, 1, "");
        jobStatus.addOrUpdateRecordStatus("warn1", IndexingStatus.WARN, 1, "");
        return jobStatus;
    }

    @Test
    public void should_returnNullList_getRecordStatuses() {

        String recordChangedMessages = "[{\"id\":\"tenant1:doc:test1\",\"kind\":\"tenant1:testindexer1:well:1.0.0\",\"op\":\"purge\"}," +
                "{\"id\":\"tenant1:doc:test2\",\"kind\":\"tenant1:testindexer12:well:1.0.0\",\"op\":\"create\"}]";
        Type listType = new TypeToken<List<RecordInfo>>() {}.getType();
        List<RecordInfo> recordInfos = (new Gson()).fromJson(recordChangedMessages, listType);
        this.sut.initialize(recordInfos);

        this.sut.addOrUpdateRecordStatus("tenant1:doc:test1", IndexingStatus.SUCCESS, 1, "");
        this.sut.addOrUpdateRecordStatus("tenant1:doc:test2", IndexingStatus.SUCCESS, 1, "");

        List<RecordStatus> statuses = this.sut.getRecordStatuses(IndexingStatus.FAIL, OperationType.create);
        assertTrue(statuses.isEmpty());
        assertEquals(statuses.size(), 0);
    }

    @Test
    public void should_get_correctUpdateMessageCount_given_updateRecordStatusTest() {
        JobStatus jobStatus = insertTestCasesIntoJobStatus();

        assertEquals(6, jobStatus.getIdsByValidUpsertIndexingStatus().size());
    }

    @Test
    public void should_returnValidList_getRecordStatuses() {

        String recordChangedMessages = "[{\"id\":\"tenant1:doc:test1\",\"kind\":\"tenant1:testindexer1:well:1.0.0\",\"op\":\"purge\"}," +
                "{\"id\":\"tenant1:doc:test2\",\"kind\":\"tenant1:testindexer12:well:1.0.0\",\"op\":\"create\"}]";
        Type listType = new TypeToken<List<RecordInfo>>() {}.getType();
        List<RecordInfo> recordInfos = (new Gson()).fromJson(recordChangedMessages, listType);
        this.sut.initialize(recordInfos);

        this.sut.addOrUpdateRecordStatus("tenant1:doc:test1", IndexingStatus.SUCCESS, 1, "");
        this.sut.addOrUpdateRecordStatus("tenant1:doc:test2", IndexingStatus.SUCCESS, 1, "");

        List<RecordStatus> statuses = this.sut.getRecordStatuses(IndexingStatus.SUCCESS, OperationType.create);
        assertNotNull(statuses);
        assertEquals(1, statuses.size());

        RecordStatus recordStatus = statuses.get(0);
        assertEquals("tenant1:doc:test2", recordStatus.getId());
        assertEquals("tenant1:testindexer12:well:1.0.0", recordStatus.getKind());
        assertEquals("create", recordStatus.getOperationType());
        assertEquals(IndexingStatus.SUCCESS, recordStatus.getStatus());
    }
}
