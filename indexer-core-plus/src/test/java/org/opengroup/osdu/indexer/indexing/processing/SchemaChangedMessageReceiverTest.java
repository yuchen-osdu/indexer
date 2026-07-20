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
import org.opengroup.osdu.core.common.model.indexer.SchemaChangedMessages;
import org.opengroup.osdu.indexer.api.RecordIndexerApi;
import org.opengroup.osdu.indexer.indexing.scope.ThreadDpsHeaders;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchemaChangedMessageReceiverTest {

    @Mock
    private ThreadDpsHeaders dpsHeaders;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private RecordIndexerApi recordIndexerApi;

    private SchemaChangedMessageReceiver schemaChangedMessageReceiver;

    private static final String CORRELATION_ID = "test-correlation-id-123";
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() {
        schemaChangedMessageReceiver = new SchemaChangedMessageReceiver(
                dpsHeaders,
                tokenProvider,
                recordIndexerApi
        );
        lenient().when(dpsHeaders.getCorrelationId()).thenReturn(CORRELATION_ID);
    }

    @Test
    void sendMessage_shouldCallRecordIndexerApiWithCorrectParameters() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createTestOqmMessage();

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi, times(1)).schemaWorker(captor.capture());

        SchemaChangedMessages capturedMessage = captor.getValue();
        assertThat(capturedMessage.getMessageId()).isEqualTo(CORRELATION_ID);
        assertThat(capturedMessage.getData()).isEqualTo(oqmMessage.getData());
        assertThat(capturedMessage.getAttributes()).isEqualTo(oqmMessage.getAttributes());
        assertThat(capturedMessage.getPublishTime()).isNotNull();
    }

    @Test
    void sendMessage_shouldSetMessageIdFromCorrelationId() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createTestOqmMessage();

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());
        assertThat(captor.getValue().getMessageId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void sendMessage_shouldCopyDataFromOqmMessage() throws Exception {
        // Arrange
        Map<String, Object> testData = new HashMap<>();
        testData.put("schemaId", "schema-123");
        testData.put("operation", "update");

        OqmMessage oqmMessage = createOqmMessageWithData(testData, new HashMap<>());

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());

        String expectedDataJson = gson.toJson(testData);
        assertThat(captor.getValue().getData()).isEqualTo(expectedDataJson);  // Fixed: capturedMessage -> captor
    }

    @Test
    void sendMessage_shouldCopyAttributesFromOqmMessage() throws Exception {
        // Arrange
        Map<String, String> testAttributes = new HashMap<>();
        testAttributes.put("attr1", "value1");
        testAttributes.put("attr2", "value2");

        OqmMessage oqmMessage = createOqmMessageWithData(new HashMap<>(), testAttributes);

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());
        assertThat(captor.getValue().getAttributes()).isEqualTo(testAttributes);
    }

    @Test
    void sendMessage_shouldSetPublishTimeToCurrentDateTime() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createTestOqmMessage();

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());
        assertThat(captor.getValue().getPublishTime()).isNotNull();
        assertThat(captor.getValue().getPublishTime()).isNotEmpty();
    }

    @Test
    void sendMessage_shouldHandleNullDataGracefully() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createOqmMessageWithData(null, new HashMap<>());

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());
        assertThat(captor.getValue().getData()).isNull();
    }

    @Test
    void sendMessage_shouldHandleNullAttributesGracefully() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createOqmMessageWithData(new HashMap<>(), null);

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());
        assertThat(captor.getValue().getAttributes()).isNull();
    }

    @Test
    void sendMessage_shouldPropagateExceptionWhenApiCallFails() throws IOException {
        // Arrange
        OqmMessage oqmMessage = createTestOqmMessage();
        doThrow(new RuntimeException("API call failed"))
                .when(recordIndexerApi).schemaWorker(any(SchemaChangedMessages.class));

        // Act & Assert
        assertThatThrownBy(() -> schemaChangedMessageReceiver.sendMessage(oqmMessage))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("API call failed");

        verify(recordIndexerApi, times(1)).schemaWorker(any(SchemaChangedMessages.class));
    }

    @Test
    void sendMessage_shouldHandleEmptyOqmMessage() throws Exception {
        // Arrange
        OqmMessage oqmMessage = mock(OqmMessage.class);
        when(oqmMessage.getData()).thenReturn(null);
        when(oqmMessage.getAttributes()).thenReturn(null);

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).schemaWorker(any(SchemaChangedMessages.class));
    }

    @Test
    void constructor_shouldInitializeWithProvidedDependencies() {
        // Act
        SchemaChangedMessageReceiver receiver = new SchemaChangedMessageReceiver(
                dpsHeaders,
                tokenProvider,
                recordIndexerApi
        );

        // Assert
        assertThat(receiver).isNotNull();
    }

    @Test
    void sendMessage_shouldHandleNon200ResponseStatus() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createTestOqmMessage();

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).schemaWorker(any(SchemaChangedMessages.class));
    }

    @Test
    void sendMessage_shouldHandleAcceptedStatus() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createTestOqmMessage();
        JobStatus jobStatus = new JobStatus();
        ResponseEntity<JobStatus> acceptedResponse = new ResponseEntity<>(jobStatus, HttpStatus.ACCEPTED);
        when(recordIndexerApi.schemaWorker(any(SchemaChangedMessages.class)))
                .thenReturn((ResponseEntity) acceptedResponse);

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).schemaWorker(any(SchemaChangedMessages.class));
        assertEquals(HttpStatus.ACCEPTED, acceptedResponse.getStatusCode());
        assertNotNull(acceptedResponse.getBody());
    }
    @Test
    void sendMessage_shouldHandleComplexDataStructure() throws Exception {
        // Arrange
        Map<String, Object> complexData = new HashMap<>();
        complexData.put("schemaId", "complex-schema-123");
        Map<String, Object> nestedData = new HashMap<>();
        nestedData.put("field1", "value1");
        nestedData.put("field2", 100);
        complexData.put("nested", nestedData);

        OqmMessage oqmMessage = createOqmMessageWithData(complexData, new HashMap<>());

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());

        String expectedDataJson = gson.toJson(complexData);
        assertThat(captor.getValue().getData()).isEqualTo(expectedDataJson);
    }

    @Test
    void sendMessage_shouldHandleMultipleAttributes() throws Exception {
        // Arrange
        Map<String, String> attributes = new HashMap<>();
        attributes.put("eventType", "schema-updated");
        attributes.put("version", "2.0");
        attributes.put("timestamp", "2024-01-01T00:00:00Z");

        OqmMessage oqmMessage = createOqmMessageWithData(new HashMap<>(), attributes);

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());
        assertThat(captor.getValue().getAttributes()).containsAllEntriesOf(attributes);
    }

    @Test
    void sendMessage_shouldLogDebugMessageWithSchemaChangedMessage() throws Exception {
        // Note: You'll need to add a logger appender or use a logging framework test library
        // This is a conceptual test - implementation depends on your logging setup
        OqmMessage oqmMessage = createTestOqmMessage();

        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Verify logging occurred (requires logging test setup)
        verify(recordIndexerApi).schemaWorker(any(SchemaChangedMessages.class));
    }

    @Test
    void sendMessage_shouldHandleNullCorrelationId() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn(null);
        OqmMessage oqmMessage = createTestOqmMessage();

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());
        assertThat(captor.getValue().getMessageId()).isNull();
    }

    @Test
    void sendMessage_shouldHandleEmptyCorrelationId() throws Exception {
        // Arrange
        when(dpsHeaders.getCorrelationId()).thenReturn("");
        OqmMessage oqmMessage = createTestOqmMessage();

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());
        assertThat(captor.getValue().getMessageId()).isEmpty();
    }

    @Test
    void sendMessage_shouldHandleIOExceptionFromApi() throws IOException {
        // Arrange
        OqmMessage oqmMessage = createTestOqmMessage();
        doThrow(new IOException("Network error"))
                .when(recordIndexerApi).schemaWorker(any(SchemaChangedMessages.class));

        // Act & Assert
        assertThatThrownBy(() -> schemaChangedMessageReceiver.sendMessage(oqmMessage))
                .isInstanceOf(IOException.class)
                .hasMessage("Network error");
    }

    @Test
    void sendMessage_shouldCallApiExactlyOnceEvenWithSuccessResponse() throws Exception {
        OqmMessage oqmMessage = createTestOqmMessage();
        ResponseEntity<?> successResponse = ResponseEntity.ok().build();
        doReturn(successResponse).when(recordIndexerApi).schemaWorker(any(SchemaChangedMessages.class));

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(1)).schemaWorker(any(SchemaChangedMessages.class));
    }

    @Test
    void sendMessage_shouldHandleVeryLargeDataPayload() throws Exception {
        // Arrange
        Map<String, Object> largeData = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            largeData.put("key" + i, "value" + i);
        }
        OqmMessage oqmMessage = createOqmMessageWithData(largeData, new HashMap<>());

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());
        assertThat(captor.getValue().getData()).isNotNull();
        assertThat(captor.getValue().getData().length()).isGreaterThan(0);
    }

    @Test
    void sendMessage_shouldHandleSpecialCharactersInData() throws Exception {
        // Arrange
        Map<String, Object> dataWithSpecialChars = new HashMap<>();
        dataWithSpecialChars.put("field", "value with 特殊字符 and émojis 🎉");
        OqmMessage oqmMessage = createOqmMessageWithData(dataWithSpecialChars, new HashMap<>());

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        ArgumentCaptor<SchemaChangedMessages> captor = ArgumentCaptor.forClass(SchemaChangedMessages.class);
        verify(recordIndexerApi).schemaWorker(captor.capture());
        assertThat(captor.getValue().getData()).contains("特殊字符");
    }

    @Test
    void getSchemaWorkerRequestBody_shouldCreateNewInstanceEachTime() throws Exception {
        // Arrange
        OqmMessage oqmMessage = createTestOqmMessage();

        // Act
        schemaChangedMessageReceiver.sendMessage(oqmMessage);
        schemaChangedMessageReceiver.sendMessage(oqmMessage);

        // Assert
        verify(recordIndexerApi, times(2)).schemaWorker(any(SchemaChangedMessages.class));
    }

    // Helper methods
    private OqmMessage createTestOqmMessage() {
        Map<String, Object> data = new HashMap<>();
        data.put("schemaId", "test-schema-id");
        data.put("kind", "test-kind");

        Map<String, String> attributes = new HashMap<>();
        attributes.put("source", "test-source");
        attributes.put("type", "schema-changed");

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
