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

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.indexer.api.RecordIndexerApi;
import org.opengroup.osdu.indexer.indexing.scope.ThreadDpsHeaders;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReindexMessageReceiverTest {

    @Mock
    private ThreadDpsHeaders dpsHeaders;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private RecordIndexerApi recordIndexerApi;

    @Mock
    private JobStatus jobStatus;

    private ReindexMessageReceiver reindexMessageReceiver;

    private static final String CORRELATION_ID = "test-correlation-id-456";
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() {
        reindexMessageReceiver = new ReindexMessageReceiver(
                dpsHeaders,
                tokenProvider,
                recordIndexerApi
        );
    }

    @Test
    void sendMessage_shouldCallRecordIndexerApiWithCorrectParameters() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = createTestOqmMessage();

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.ok(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        verify(recordIndexerApi, times(1)).indexWorker(captor.capture());

        RecordChangedMessages capturedMessage = captor.getValue();
        assertThat(capturedMessage.getMessageId()).isEqualTo(CORRELATION_ID);
        assertThat(capturedMessage.getData()).isEqualTo(oqmMessage.getData());
        assertThat(capturedMessage.getAttributes()).isEqualTo(oqmMessage.getAttributes());
        assertThat(capturedMessage.getPublishTime()).isNotNull();
    }

    @Test
    void sendMessage_shouldSetMessageIdFromCorrelationId() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = createTestOqmMessage();

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.ok(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        verify(recordIndexerApi).indexWorker(captor.capture());
        assertThat(captor.getValue().getMessageId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void sendMessage_shouldCopyDataFromOqmMessage() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);

        Map<String, Object> testData = new HashMap<>();
        testData.put("recordId", "record-123");
        testData.put("kind", "osdu:wks:dataset:1.0.0");

        OqmMessage oqmMessage = createOqmMessageWithData(testData, new HashMap<>());

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.ok(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        verify(recordIndexerApi).indexWorker(captor.capture());

        String expectedDataJson = gson.toJson(testData);
        assertThat(captor.getValue().getData()).isEqualTo(expectedDataJson);
    }

    @Test
    void sendMessage_shouldCopyAttributesFromOqmMessage() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);

        Map<String, String> testAttributes = new HashMap<>();
        testAttributes.put("dataPartitionId", "osdu");
        testAttributes.put("op", "reindex");

        OqmMessage oqmMessage = createOqmMessageWithData(new HashMap<>(), testAttributes);

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.ok(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        verify(recordIndexerApi).indexWorker(captor.capture());
        assertThat(captor.getValue().getAttributes()).isEqualTo(testAttributes);
    }

    @Test
    void sendMessage_shouldSetPublishTimeToCurrentDateTime() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = createTestOqmMessage();

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.ok(jobStatus));

        LocalDateTime beforeCall = LocalDateTime.now();

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        LocalDateTime afterCall = LocalDateTime.now();

        // Assert
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        verify(recordIndexerApi).indexWorker(captor.capture());

        String publishTime = captor.getValue().getPublishTime();
        assertThat(publishTime).isNotNull().isNotEmpty();

        LocalDateTime parsedTime = LocalDateTime.parse(publishTime);
        assertThat(parsedTime)
                .isAfterOrEqualTo(beforeCall.minusSeconds(1))
                .isBeforeOrEqualTo(afterCall.plusSeconds(1));
    }

    @Test
    void sendMessage_shouldHandleNullDataGracefully() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = createOqmMessageWithData(null, new HashMap<>());

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.ok(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        verify(recordIndexerApi).indexWorker(captor.capture());
        assertThat(captor.getValue().getData()).isNull();
    }

    @Test
    void sendMessage_shouldHandleNullAttributesGracefully() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = createOqmMessageWithData(new HashMap<>(), null);

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.ok(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        verify(recordIndexerApi).indexWorker(captor.capture());
        assertThat(captor.getValue().getAttributes()).isNull();
    }

    @Test
    void sendMessage_shouldPropagateExceptionWhenApiCallFails() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = createTestOqmMessage();

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenThrow(new RuntimeException("API call failed"));

        // Act & Assert
        assertThatThrownBy(() -> reindexMessageReceiver.sendMessage(oqmMessage))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("API call failed");

        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
    }

    @Test
    void sendMessage_shouldHandleIOException() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = createTestOqmMessage();

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenThrow(new IOException("Network error"));

        // Act & Assert
        assertThatThrownBy(() -> reindexMessageReceiver.sendMessage(oqmMessage))
                .isInstanceOf(IOException.class)
                .hasMessage("Network error");

        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
    }

    @Test
    void sendMessage_shouldHandleAcceptedStatus() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = createTestOqmMessage();

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
    }

    @Test
    void sendMessage_shouldHandleEmptyOqmMessage() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = mock(OqmMessage.class);
        when(oqmMessage.getData()).thenReturn(null);
        when(oqmMessage.getAttributes()).thenReturn(null);

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.ok(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
    }

    @Test
    void sendMessage_shouldHandleComplexDataStructure() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);

        Map<String, Object> complexData = new HashMap<>();
        complexData.put("recordId", "complex-record-123");
        complexData.put("version", 2);

        Map<String, Object> nestedData = new HashMap<>();
        nestedData.put("field1", "value1");
        nestedData.put("field2", 100);
        nestedData.put("active", true);
        complexData.put("metadata", nestedData);

        OqmMessage oqmMessage = createOqmMessageWithData(complexData, new HashMap<>());

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.ok(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        verify(recordIndexerApi).indexWorker(captor.capture());

        String expectedDataJson = gson.toJson(complexData);
        assertThat(captor.getValue().getData()).isEqualTo(expectedDataJson);
    }

    @Test
    void sendMessage_shouldHandleMultipleAttributes() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("dataPartitionId", "osdu");
        attributes.put("operation", "reindex");
        attributes.put("source", "bulk-reindex-service");
        attributes.put("timestamp", "2024-01-01T00:00:00Z");

        OqmMessage oqmMessage = createOqmMessageWithData(new HashMap<>(), attributes);

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.ok(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<RecordChangedMessages> captor = ArgumentCaptor.forClass(RecordChangedMessages.class);
        verify(recordIndexerApi).indexWorker(captor.capture());
        assertThat(captor.getValue().getAttributes()).containsAllEntriesOf(attributes);
    }

    @Test
    void sendMessage_shouldHandleCreatedStatus() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = createTestOqmMessage();

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
    }

    @Test
    void sendMessage_shouldCreateNewMessageForEachCall() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage1 = createTestOqmMessage();
        OqmMessage oqmMessage2 = createTestOqmMessage();

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.ok(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage1);
        reindexMessageReceiver.sendMessage(oqmMessage2);

        // Assert
        verify(recordIndexerApi, times(2)).indexWorker(any(RecordChangedMessages.class));
    }

    @Test
    void sendMessage_shouldHandleInternalServerError() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = createTestOqmMessage();

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
    }

    @Test
    void sendMessage_shouldHandleBadRequestStatus() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
        OqmMessage oqmMessage = createTestOqmMessage();

        when(recordIndexerApi.indexWorker(any(RecordChangedMessages.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(jobStatus));

        // Act
        reindexMessageReceiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).indexWorker(any(RecordChangedMessages.class));
    }

    // Helper methods
    private OqmMessage createTestOqmMessage() {
        Map<String, Object> data = new HashMap<>();
        data.put("recordId", "test-record-id");
        data.put("kind", "osdu:wks:dataset:1.0.0");
        data.put("operation", "reindex");

        Map<String, String> attributes = new HashMap<>();
        attributes.put("dataPartitionId", "osdu");
        attributes.put("source", "reindex-service");

        return createOqmMessageWithData(data, attributes);
    }

    private OqmMessage createOqmMessageWithData(Map<String, Object> data, Map<String, String> attributes) {
        OqmMessage oqmMessage = mock(OqmMessage.class);

        // Convert data Map to JSON string
        String dataJson = data != null ? gson.toJson(data) : null;
        when(oqmMessage.getData()).thenReturn(dataJson);

        when(oqmMessage.getAttributes()).thenReturn(attributes);
        return oqmMessage;
    }
}
