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

package org.opengroup.osdu.indexer.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.SchemaChangedMessages;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.service.IndexerService;
import org.opengroup.osdu.indexer.service.SchemaEventsProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RunWith(MockitoJUnitRunner.class)
public class RecordIndexerApiTest {

    private final String recordMessageValid = "{\"data\":\"[{\\\"id\\\":\\\"opendes:welldb:wellbore-d9033ae1-fb15-496c-9ba0-880fd1d2b2cf\\\",\\\"kind\\\":\\\"tenant1:welldb:wellbore:1.0.0\\\",\\\"op\\\":\\\"create\\\"}]\",\"attributes\":{\"account-id\":\"opendes\",\"correlation-id\":\"b5a281bd-f59d-4db2-9939-b2d85036fc7e\"},\"messageId\":\"75328163778221\",\"publishTime\":\"2018-05-08T21:48:56.131Z\"}";
    private final String schemaMessageValid = "{\"data\":\"[{\\\"kind\\\":\\\"tenant1:welldb:wellbore:1.0.0\\\",\\\"op\\\":\\\"create\\\"}]\",\"attributes\":{\"account-id\":\"opendes\",\"correlation-id\":\"b5a281bd-f59d-4db2-9939-b2d85036fc7e\"},\"messageId\":\"75328163778221\",\"publishTime\":\"2018-05-08T21:48:56.131Z\"}";
    private final String messageEmpty = "{}";
    private final String messageWithEmptyData = "{\"data\":\"[]\",\"attributes\":{\"account-id\":\"opendes\",\"correlation-id\":\"b5a281bd-f59d-4db2-9939-b2d85036fc7e\"},\"messageId\":\"75328163778221\",\"publishTime\":\"2018-05-08T21:48:56.131Z\"}";
    private final String messageWithIncorrectJsonFormat = "{\"data\":\"[{}}]\",\"attributes\":{\"account-id\":\"opendes\",\"correlation-id\":\"b5a281bd-f59d-4db2-9939-b2d85036fc7e\"},\"messageId\":\"75328163778221\",\"publishTime\":\"2018-05-08T21:48:56.131Z\"}";

    private final String ACCOUNT_ID = "any-account";
    private final String DATA_PARTITION_ID = "opendes";

    @InjectMocks
    private RecordIndexerApi sut;
    @Mock
    private JaxRsDpsLog log;
    @Mock
    private IndexerService indexService;
    @Mock
    private IRequestInfo requestInfo;
    @Mock
    private SchemaEventsProcessor eventsProcessingService;

    @Mock
    private DpsHeaders dpsHeaders;

    @Before
    public void setup() {
        initMocks(this);

        dpsHeaders.put(DpsHeaders.ACCOUNT_ID, this.ACCOUNT_ID);
        dpsHeaders.put(DpsHeaders.DATA_PARTITION_ID, this.DATA_PARTITION_ID);
        when(this.requestInfo.getHeaders()).thenReturn(dpsHeaders);
    }

    @Test
    public void should_return200_given_validMessage_indexWorkerTest() throws Exception {
        should_return200_indexerWorkerTest(recordMessageValid);
    }

    @Test
    public void should_return200_given_emptyData_indexWorkerTest() throws Exception {
        should_return200_indexerWorkerTest(messageWithEmptyData);
    }

    @Test
    public void should_return400_given_emptyMessage_indexWorkerTest() {
        should_return400_indexerWorkerTest(messageEmpty, String.format("Required header: '%s' not found", DpsHeaders.DATA_PARTITION_ID));
    }

    @Test
    public void should_addCorrelationIdToHeader_IfExists_indexWorkerTest() throws Exception {
        this.sut.indexWorker(createRecordChangedMessage(recordMessageValid));
        Mockito.verify(this.requestInfo.getHeaders()).put("correlation-id", "b5a281bd-f59d-4db2-9939-b2d85036fc7e");
    }

    @Test
    public void should_return400_given_incorrectJsonFormatMessage_indexWorkerTest() {
        should_return400_indexerWorkerTest(messageWithIncorrectJsonFormat, "Unable to parse request payload.");
    }

    @Test
    public void should_return200_given_validMessage_schemaWorkerTest() throws Exception {
        should_return200_schemaWorkerTest(schemaMessageValid);
    }

    @Test
    public void should_return200_given_emptyData_schemaWorkerTest() throws Exception {
        should_return200_schemaWorkerTest(messageWithEmptyData);
    }

    @Test
    public void should_return400_given_incorrectJsonFormatMessage_SchemaWorkerTest() {
        should_return400_schemaWorkerTest(messageWithIncorrectJsonFormat, "Unable to parse request payload.");
    }

    private void should_return200_indexerWorkerTest(String message) throws Exception {
        ResponseEntity response = this.sut.indexWorker(createRecordChangedMessage(message));
        assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
    }

    private void should_return400_indexerWorkerTest(String message, String errorMessage) {
        try {
            this.sut.indexWorker(createRecordChangedMessage(message));
            fail("Should throw exception");
        } catch (AppException e) {
            assertEquals(HttpStatus.BAD_REQUEST.value(), e.getError().getCode());
            assertEquals(errorMessage, e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    private RecordChangedMessages createRecordChangedMessage(String message) {
        return (new Gson()).fromJson(message, RecordChangedMessages.class);
    }

    private void should_return200_schemaWorkerTest(String message) throws Exception {
        ResponseEntity response = this.sut.schemaWorker(createSchemaChangedMessage(message));
        assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
    }

    private void should_return400_schemaWorkerTest(String message, String errorMessage) {
        try {
            this.sut.schemaWorker(createSchemaChangedMessage(message));
            fail("Should throw exception");
        } catch (AppException e) {
            assertEquals(HttpStatus.BAD_REQUEST.value(), e.getError().getCode());
            assertEquals(errorMessage, e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    private SchemaChangedMessages createSchemaChangedMessage(String message) {
        return (new Gson()).fromJson(message, SchemaChangedMessages.class);
    }
}
