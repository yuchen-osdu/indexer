/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.opengroup.osdu.indexer.indexing.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.indexer.api.RecordIndexerApi;
import org.opengroup.osdu.indexer.indexing.scope.ThreadDpsHeaders;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class RecordsChangedMessageReceiverTest {

    @Mock
    private ThreadDpsHeaders dpsHeaders;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private RecordIndexerApi recordIndexerApi;

    private RecordsChangedMessageReceiver receiver;

    private static final String TEST_CORRELATION_ID = "test-correlation-id";
    private static final String TEST_DATA_PARTITION_ID = "test-partition";
    private static final String TEST_TOKEN = "test-bearer-token";
    private static final String TEST_MESSAGE_DATA = "{\"id\":\"test-record-id\",\"kind\":\"test-kind\"}";

    @BeforeEach
    void setUp() {
        // Reset mocks
        Mockito.reset(dpsHeaders, tokenProvider, recordIndexerApi);

        // Setup common stubs
        Mockito.lenient().when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);
        Mockito.lenient().when(tokenProvider.getIdToken()).thenReturn(TEST_TOKEN);

        // Create receiver instance
        receiver = new RecordsChangedMessageReceiver(dpsHeaders, tokenProvider, recordIndexerApi);
    }

    // ==================== Test 1: Constructor ====================

    @Test
    void constructorShouldInitializeSuccessfully() {
        // Arrange & Act
        RecordsChangedMessageReceiver testReceiver = new RecordsChangedMessageReceiver(
                dpsHeaders, tokenProvider, recordIndexerApi);

        // Assert
        assertNotNull(testReceiver);
    }

    // ==================== Test 2-8: Core SendMessage Functionality ====================

    @Test
    void shouldCallIndexWorkerWhenSendingMessage() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
    }

    @Test
    void shouldPassMessageDataToIndexWorker() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);

        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        when(recordIndexerApi.indexWorker(captor.capture())).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        RecordChangedMessages capturedMessage = captor.getValue();
        assertEquals(TEST_MESSAGE_DATA, capturedMessage.getData());
    }

    @Test
    void shouldPassMessageAttributesToIndexWorker() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);

        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        when(recordIndexerApi.indexWorker(captor.capture())).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        RecordChangedMessages capturedMessage = captor.getValue();
        assertNotNull(capturedMessage.getAttributes());
        assertEquals(TEST_CORRELATION_ID, capturedMessage.getAttributes().get("correlation-id"));
    }

    @Test
    void shouldSetMessageIdFromCorrelationId() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);

        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        when(recordIndexerApi.indexWorker(captor.capture())).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        RecordChangedMessages capturedMessage = captor.getValue();
        assertEquals(TEST_CORRELATION_ID, capturedMessage.getMessageId());
    }

    @Test
    void shouldSetPublishTimeWhenSendingMessage() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);

        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        when(recordIndexerApi.indexWorker(captor.capture())).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        RecordChangedMessages capturedMessage = captor.getValue();
        assertNotNull(capturedMessage.getPublishTime());
    }

    @Test
    void shouldHandleEmptyDataInMessage() throws Exception {
        // Arrange
        OqmMessage oqmMessage = OqmMessage.builder()
                .id("test-message-id")
                .data("")
                .attributes(createValidAttributes())
                .build();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);

        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        when(recordIndexerApi.indexWorker(captor.capture())).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        RecordChangedMessages capturedMessage = captor.getValue();
        assertEquals("", capturedMessage.getData());
    }

    @Test
    void shouldHandleComplexJsonData() throws Exception {
        // Arrange
        String complexJson = "{\"id\":\"record-123\",\"kind\":\"osdu:wks:dataset--File.Generic:1.0.0\",\"data\":{\"field1\":\"value1\"}}";
        OqmMessage oqmMessage = OqmMessage.builder()
                .id("test-message-id")
                .data(complexJson)
                .attributes(createValidAttributes())
                .build();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);

        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        when(recordIndexerApi.indexWorker(captor.capture())).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        RecordChangedMessages capturedMessage = captor.getValue();
        assertEquals(complexJson, capturedMessage.getData());
    }

    // ==================== Test 9-12: HTTP Response Handling ====================

    @Test
    void shouldHandleOkResponse() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        JobStatus jobStatus = new JobStatus();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(jobStatus, HttpStatus.OK);
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
        // Verify the response was successful and no exception was thrown
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void shouldHandleCreatedResponse() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.CREATED);
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
    }

    @Test
    void shouldHandleAcceptedResponse() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.ACCEPTED);
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
    }

    @Test
    void shouldHandleNullJobStatusInResponse() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(null, HttpStatus.OK);
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
    }

    // ==================== Test 13-17: Exception Handling ====================

    @Test
    void shouldPropagateRuntimeException() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        RuntimeException expectedException = new RuntimeException("Index worker failed");
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenThrow(expectedException);

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            receiver.sendMessage(oqmMessage);
        });

        assertEquals("Index worker failed", thrown.getMessage());
    }

    @Test
    void shouldPropagateCheckedException() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        Exception expectedException = new Exception("Checked exception");
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenThrow(expectedException);

        // Act & Assert
        assertThrows(Exception.class, () -> {
            receiver.sendMessage(oqmMessage);
        });
    }

    @Test
    void shouldPropagateNullPointerException() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        NullPointerException expectedException = new NullPointerException("Null encountered");
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenThrow(expectedException);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            receiver.sendMessage(oqmMessage);
        });
    }

    @Test
    void shouldPropagateIllegalArgumentException() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        IllegalArgumentException expectedException = new IllegalArgumentException("Invalid argument");
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenThrow(expectedException);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            receiver.sendMessage(oqmMessage);
        });
    }

    @Test
    void shouldPropagateIllegalStateException() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createValidOqmMessage();
        IllegalStateException expectedException = new IllegalStateException("Invalid state");
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenThrow(expectedException);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            receiver.sendMessage(oqmMessage);
        });
    }

    // ==================== Test 18-20: Multiple Message Handling ====================

    @Test
    void shouldHandleMultipleConsecutiveMessages() throws Exception {
        // Arrange
        OqmMessage message1 = createOqmMessageWithId("message-1");
        OqmMessage message2 = createOqmMessageWithId("message-2");
        OqmMessage message3 = createOqmMessageWithId("message-3");
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenReturn(response);

        // Act
        receiver.sendMessage(message1);
        receiver.sendMessage(message2);
        receiver.sendMessage(message3);

        // Assert
        verify(recordIndexerApi, times(3)).indexWorker(any(RecordChangedMessages.class));
    }

    @Test
    void shouldHandleMessageWithCustomAttributes() throws Exception {
        // Arrange
        Map<String, String> customAttributes = new HashMap<>();
        customAttributes.put("correlation-id", TEST_CORRELATION_ID);
        customAttributes.put("data-partition-id", TEST_DATA_PARTITION_ID);
        customAttributes.put("custom-header", "custom-value");

        OqmMessage oqmMessage = OqmMessage.builder()
                .id("test-message-id")
                .data(TEST_MESSAGE_DATA)
                .attributes(customAttributes)
                .build();

        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        when(recordIndexerApi.indexWorker(captor.capture())).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        RecordChangedMessages capturedMessage = captor.getValue();
        assertEquals("custom-value", capturedMessage.getAttributes().get("custom-header"));
    }

    @Test
    void shouldHandleMessageWithEmptyAttributes() throws Exception {
        // Arrange
        OqmMessage oqmMessage = OqmMessage.builder()
                .id("test-message-id")
                .data(TEST_MESSAGE_DATA)
                .attributes(new HashMap<>())
                .build();

        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        when(recordIndexerApi.indexWorker(captor.capture())).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        RecordChangedMessages capturedMessage = captor.getValue();
        assertNotNull(capturedMessage.getAttributes());
        assertEquals(0, capturedMessage.getAttributes().size());
    }

    // ==================== Test 21-24: Edge Cases ====================

    @Test
    void shouldUseCorrelationIdFromDpsHeaders() throws Exception {
        // Arrange
        String customCorrelationId = "custom-correlation-12345";
        when(dpsHeaders.getCorrelationId()).thenReturn(customCorrelationId);

        OqmMessage oqmMessage = createValidOqmMessage();
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        when(recordIndexerApi.indexWorker(captor.capture())).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        RecordChangedMessages capturedMessage = captor.getValue();
        assertEquals(customCorrelationId, capturedMessage.getMessageId());
        verify(dpsHeaders, times(1)).getCorrelationId();
    }

    @Test
    void shouldHandleVeryLongJsonData() throws Exception {
        // Arrange
        StringBuilder longJson = new StringBuilder("{\"id\":\"record-123\",\"data\":{");
        for (int i = 0; i < 100; i++) {
            longJson.append("\"field").append(i).append("\":\"value").append(i).append("\"");
            if (i < 99) longJson.append(",");
        }
        longJson.append("}}");

        OqmMessage oqmMessage = OqmMessage.builder()
                .id("test-message-id")
                .data(longJson.toString())
                .attributes(createValidAttributes())
                .build();

        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        when(recordIndexerApi.indexWorker(captor.capture())).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        RecordChangedMessages capturedMessage = captor.getValue();
        assertEquals(longJson.toString(), capturedMessage.getData());
    }

    @Test
    void shouldHandleSpecialCharactersInData() throws Exception {
        // Arrange
        String specialData = "{\"id\":\"test\",\"data\":\"Special chars: <>&\\\"'\"}";
        OqmMessage oqmMessage = OqmMessage.builder()
                .id("test-message-id")
                .data(specialData)
                .attributes(createValidAttributes())
                .build();

        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        when(recordIndexerApi.indexWorker(captor.capture())).thenReturn(response);

        // Act
        receiver.sendMessage(oqmMessage);

        // Assert
        RecordChangedMessages capturedMessage = captor.getValue();
        assertEquals(specialData, capturedMessage.getData());
    }

    @Test
    void shouldCallDpsHeadersGetCorrelationIdForEachMessage() throws Exception {
        // Arrange
        OqmMessage message1 = createOqmMessageWithId("message-1");
        OqmMessage message2 = createOqmMessageWithId("message-2");
        ResponseEntity<JobStatus> response = new ResponseEntity<>(new JobStatus(), HttpStatus.OK);
        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class))).thenReturn(response);

        // Act
        receiver.sendMessage(message1);
        receiver.sendMessage(message2);

        // Assert
        verify(dpsHeaders, times(2)).getCorrelationId();
        verify(recordIndexerApi, times(2)).indexWorker(any(RecordChangedMessages.class));
    }

    // ==================== Helper Methods ====================

    private OqmMessage createValidOqmMessage() {
        return OqmMessage.builder()
                .id("test-message-id")
                .data(TEST_MESSAGE_DATA)
                .attributes(createValidAttributes())
                .build();
    }

    private OqmMessage createOqmMessageWithId(String id) {
        return OqmMessage.builder()
                .id(id)
                .data(TEST_MESSAGE_DATA)
                .attributes(createValidAttributes())
                .build();
    }

    private Map<String, String> createValidAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("correlation-id", TEST_CORRELATION_ID);
        attributes.put("data-partition-id", TEST_DATA_PARTITION_ID);
        return attributes;
    }
}
