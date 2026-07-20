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
package org.opengroup.osdu.indexer.common.publish;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.indexer.indexing.config.MessagingConfigProperties;
import org.opengroup.osdu.indexer.model.Constants;
import org.opengroup.osdu.oqm.core.OqmDriver;
import org.opengroup.osdu.oqm.core.model.OqmDestination;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.opengroup.osdu.oqm.core.model.OqmTopic;

@ExtendWith(MockitoExtension.class)
class ReprocessingTaskPublisherTest {

    @Mock
    private OqmDriver driver;

    @Mock
    private MessagingConfigProperties properties;

    private ReprocessingTaskPublisher publisher;

    private Gson gson;

    private static final String TEST_PARTITION_ID = "test-partition";
    private static final String TEST_USER_EMAIL = "test@example.com";
    private static final String TEST_CORRELATION_ID = "test-correlation-123";
    private static final String TEST_REPROCESS_TOPIC = "reprocess-topic";
    private static final String TEST_REINDEX_TOPIC = "reindex-topic";
    private static final String TEST_PAYLOAD = "{\"data\":\"test-data\"}";

    @BeforeEach
    void setUp() {
        Mockito.reset(driver, properties);
        gson = new Gson();

        when(properties.getReprocessTopicName()).thenReturn(TEST_REPROCESS_TOPIC);
        when(properties.getReindexTopicName()).thenReturn(TEST_REINDEX_TOPIC);

        publisher = new ReprocessingTaskPublisher(driver, properties);
        publisher.setUp();
    }

    // ==================== Test 1-2: Setup and Initialization ====================

    @Test
    void shouldInitializeTopicsOnSetup() {
        // Arrange
        Mockito.reset(properties);
        when(properties.getReprocessTopicName()).thenReturn("custom-reprocess");
        when(properties.getReindexTopicName()).thenReturn("custom-reindex");
        ReprocessingTaskPublisher testPublisher = new ReprocessingTaskPublisher(driver, properties);

        // Act
        testPublisher.setUp();

        // Assert
        assertNotNull(testPublisher);
        verify(properties, times(1)).getReprocessTopicName();
        verify(properties, times(1)).getReindexTopicName();
    }

    // ==================== Test 2-4: createWorkerTask (immediate) ====================

    @Test
    void createWorkerTaskShouldPublishWithCorrectConfiguration() {
        // Arrange
        DpsHeaders headers = createTestHeaders();
        RecordChangedMessages recordChangedMessages = createRecordChangedMessages();
        String payload = gson.toJson(recordChangedMessages);

        ArgumentCaptor<OqmMessage> messageCaptor = ArgumentCaptor.forClass(OqmMessage.class);
        ArgumentCaptor<OqmTopic> topicCaptor = ArgumentCaptor.forClass(OqmTopic.class);
        ArgumentCaptor<OqmDestination> destinationCaptor = ArgumentCaptor.forClass(OqmDestination.class);

        // Act
        publisher.createWorkerTask(payload, headers);

        // Assert
        verify(driver, times(1)).publish(messageCaptor.capture(), topicCaptor.capture(), destinationCaptor.capture());

        // Verify topic
        assertEquals(TEST_REINDEX_TOPIC, topicCaptor.getValue().getName());

        // Verify destination
        assertEquals(TEST_PARTITION_ID, destinationCaptor.getValue().getPartitionId());

        // Verify message
        OqmMessage message = messageCaptor.getValue();
        assertEquals(TEST_CORRELATION_ID, message.getId());
        assertEquals("test-record-data", message.getData());
        assertEquals(TEST_USER_EMAIL, message.getAttributes().get(DpsHeaders.USER_EMAIL));
        assertEquals(TEST_PARTITION_ID, message.getAttributes().get(DpsHeaders.ACCOUNT_ID));
        assertEquals(TEST_PARTITION_ID, message.getAttributes().get(DpsHeaders.DATA_PARTITION_ID));
        assertNotNull(message.getAttributes().get(DpsHeaders.CORRELATION_ID));
    }

    @Test
    void createWorkerTaskShouldHandleAncestryKinds() {
        // Arrange
        DpsHeaders headers = createTestHeaders();

        // Test WITH ancestry kinds
        RecordChangedMessages withAncestry = createRecordChangedMessages();
        Map<String, String> attributes = new HashMap<>();
        attributes.put(Constants.ANCESTRY_KINDS, "kind1,kind2,kind3");
        withAncestry.setAttributes(attributes);
        String payloadWithAncestry = gson.toJson(withAncestry);

        ArgumentCaptor<OqmMessage> messageCaptor = ArgumentCaptor.forClass(OqmMessage.class);

        // Act - with ancestry
        publisher.createWorkerTask(payloadWithAncestry, headers);

        // Assert - ancestry should be included
        verify(driver, times(1)).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        assertTrue(messageCaptor.getValue().getAttributes().containsKey(Constants.ANCESTRY_KINDS));
        assertEquals("kind1,kind2,kind3", messageCaptor.getValue().getAttributes().get(Constants.ANCESTRY_KINDS));

        // Reset and test WITHOUT ancestry kinds
        Mockito.reset(driver);
        RecordChangedMessages withoutAncestry = createRecordChangedMessages();
        String payloadWithoutAncestry = gson.toJson(withoutAncestry);

        // Act - without ancestry
        publisher.createWorkerTask(payloadWithoutAncestry, headers);

        // Assert - ancestry should not be included
        verify(driver, times(1)).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        assertFalse(messageCaptor.getValue().getAttributes().containsKey(Constants.ANCESTRY_KINDS) &&
                messageCaptor.getValue().getAttributes().get(Constants.ANCESTRY_KINDS) != null);
    }

    // ==================== Test 4-5: createWorkerTask (delayed) ====================

    @Test
    void createWorkerTaskWithDelayShouldScheduleAndHandleErrors() {
        // Arrange
        DpsHeaders headers = createTestHeaders();
        RecordChangedMessages recordChangedMessages = createRecordChangedMessages();
        String payload = gson.toJson(recordChangedMessages);
        Long delayMillis = 50L;

        ArgumentCaptor<OqmMessage> messageCaptor = ArgumentCaptor.forClass(OqmMessage.class);
        ArgumentCaptor<OqmTopic> topicCaptor = ArgumentCaptor.forClass(OqmTopic.class);

        // Act - should not publish immediately
        publisher.createWorkerTask(payload, delayMillis, headers);
        verify(driver, never()).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));

        // Assert - should publish after delay
        verify(driver, timeout(200).times(1)).publish(messageCaptor.capture(), topicCaptor.capture(), any(OqmDestination.class));
        assertEquals(TEST_REINDEX_TOPIC, topicCaptor.getValue().getName());
        assertEquals(TEST_USER_EMAIL, messageCaptor.getValue().getAttributes().get(DpsHeaders.USER_EMAIL));
    }

    @Test
    void createWorkerTaskWithDelayShouldHandleExceptionsAndErrors()  {
        // Arrange
        DpsHeaders headers = createTestHeaders();
        RecordChangedMessages recordChangedMessages = createRecordChangedMessages();
        String payload = gson.toJson(recordChangedMessages);

        // Test Exception handling
        doThrow(new RuntimeException("Test exception"))
                .doNothing()
                .when(driver).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));

        // Act
        publisher.createWorkerTask(payload, 50L, headers);

        // Assert - should have attempted despite exception
        verify(driver, timeout(200).times(1)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));

        // Reset and test Error handling
        Mockito.reset(driver);
        doThrow(new OutOfMemoryError("Test error"))
                .when(driver).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));

        // Act
        publisher.createWorkerTask(payload, 50L, headers);

        // Assert - should have attempted despite error
        verify(driver, timeout(200).times(1)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
    }

    // ==================== Test 6-7: createReIndexTask (immediate) ====================

    @Test
    void createReIndexTaskShouldPublishWithCorrectConfiguration() {
        // Arrange
        DpsHeaders headers = createTestHeaders();
        String payload = TEST_PAYLOAD;

        ArgumentCaptor<OqmMessage> messageCaptor = ArgumentCaptor.forClass(OqmMessage.class);
        ArgumentCaptor<OqmTopic> topicCaptor = ArgumentCaptor.forClass(OqmTopic.class);
        ArgumentCaptor<OqmDestination> destinationCaptor = ArgumentCaptor.forClass(OqmDestination.class);

        // Act
        publisher.createReIndexTask(payload, headers);

        // Assert
        verify(driver, times(1)).publish(messageCaptor.capture(), topicCaptor.capture(), destinationCaptor.capture());

        // Verify topic
        assertEquals(TEST_REPROCESS_TOPIC, topicCaptor.getValue().getName());

        // Verify destination
        assertEquals(TEST_PARTITION_ID, destinationCaptor.getValue().getPartitionId());

        // Verify message
        OqmMessage message = messageCaptor.getValue();
        assertEquals(payload, message.getData());
        assertEquals(TEST_USER_EMAIL, message.getAttributes().get(DpsHeaders.USER_EMAIL));
        assertEquals(TEST_PARTITION_ID, message.getAttributes().get(DpsHeaders.ACCOUNT_ID));
        assertNotNull(message.getAttributes().get(DpsHeaders.CORRELATION_ID));
    }

    // ==================== Test 7-8: createReIndexTask (delayed) ====================

    @Test
    void createReIndexTaskWithDelayShouldScheduleCorrectly()  {
        // Arrange
        DpsHeaders headers = createTestHeaders();
        String payload = TEST_PAYLOAD;
        Long delayMillis = 50L;

        ArgumentCaptor<OqmMessage> messageCaptor = ArgumentCaptor.forClass(OqmMessage.class);
        ArgumentCaptor<OqmTopic> topicCaptor = ArgumentCaptor.forClass(OqmTopic.class);

        // Act - should not publish immediately
        publisher.createReIndexTask(payload, delayMillis, headers);
        verify(driver, never()).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));

        // Assert - should publish after delay (wait up to 200ms)
        verify(driver, timeout(200).times(1)).publish(messageCaptor.capture(), topicCaptor.capture(), any(OqmDestination.class));
        assertEquals(TEST_REPROCESS_TOPIC, topicCaptor.getValue().getName());
        assertEquals(TEST_USER_EMAIL, messageCaptor.getValue().getAttributes().get(DpsHeaders.USER_EMAIL));
        assertEquals(payload, messageCaptor.getValue().getData());
    }

    @Test
    void createReIndexTaskWithDelayShouldHandleExceptionsAndErrors()  {
        // Arrange
        DpsHeaders headers = createTestHeaders();
        String payload = TEST_PAYLOAD;

        // Test Exception handling
        doThrow(new RuntimeException("Test exception"))
                .doNothing()
                .when(driver).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));

        // Act
        publisher.createReIndexTask(payload, 50L, headers);

        // Assert - wait for delayed execution
        verify(driver, timeout(200).times(1)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));

        // Reset and test Error handling
        Mockito.reset(driver);
        doThrow(new OutOfMemoryError("Test error"))
                .when(driver).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));

        // Act
        publisher.createReIndexTask(payload, 50L, headers);

        // Assert - wait for delayed execution
        verify(driver, timeout(200).times(1)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
    }

    // ==================== Test 9-10: Edge Cases ====================

    @Test
    void shouldHandleCorrelationIdGeneration() {
        // Arrange - headers without correlation ID
        DpsHeaders headersWithoutCorrelation = new DpsHeaders();
        headersWithoutCorrelation.put(DpsHeaders.DATA_PARTITION_ID, TEST_PARTITION_ID);
        headersWithoutCorrelation.put(DpsHeaders.USER_EMAIL, TEST_USER_EMAIL);

        String payload = TEST_PAYLOAD;
        ArgumentCaptor<OqmMessage> messageCaptor = ArgumentCaptor.forClass(OqmMessage.class);

        // Act
        publisher.createReIndexTask(payload, headersWithoutCorrelation);

        // Assert - correlation ID should be auto-generated
        verify(driver).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        assertNotNull(messageCaptor.getValue().getAttributes().get(DpsHeaders.CORRELATION_ID));
    }

    @Test
    void shouldHandleMultipleTasksAndEdgeCases()  {
        // Arrange
        DpsHeaders headers = createTestHeaders();

        // Test 1: Multiple immediate tasks
        publisher.createReIndexTask(TEST_PAYLOAD, headers);
        publisher.createReIndexTask(TEST_PAYLOAD, headers);
        verify(driver, times(2)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));

        // Reset for next test
        Mockito.reset(driver);

        // Test 2: Large payload
        RecordChangedMessages largeMessage = new RecordChangedMessages();
        largeMessage.setData("x".repeat(10000));
        largeMessage.setAttributes(new HashMap<>());
        String largePayload = gson.toJson(largeMessage);

        ArgumentCaptor<OqmMessage> messageCaptor = ArgumentCaptor.forClass(OqmMessage.class);
        publisher.createWorkerTask(largePayload, headers);
        verify(driver, times(1)).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        assertEquals("x".repeat(10000), messageCaptor.getValue().getData());

        // Reset for next test
        Mockito.reset(driver);

        // Test 3: Special characters
        RecordChangedMessages specialMessage = new RecordChangedMessages();
        specialMessage.setData("test-特殊字符-🎉");
        specialMessage.setAttributes(new HashMap<>());
        String specialPayload = gson.toJson(specialMessage);

        publisher.createWorkerTask(specialPayload, headers);
        verify(driver, times(1)).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        assertEquals("test-特殊字符-🎉", messageCaptor.getValue().getData());

        // Reset for delayed tasks test
        Mockito.reset(driver);

        // Test 4: Multiple delayed tasks
        publisher.createReIndexTask(TEST_PAYLOAD, 50L, headers);
        publisher.createReIndexTask(TEST_PAYLOAD, 100L, headers);

        // Verify no immediate execution
        verify(driver, never()).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));

        // Wait for first delayed task with timeout
        verify(driver, timeout(200).times(1)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));

        // Wait for second delayed task with timeout
        verify(driver, timeout(200).times(2)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
    }

    // ==================== Helper Methods ====================

    private DpsHeaders createTestHeaders() {
        DpsHeaders headers = new DpsHeaders();
        headers.put(DpsHeaders.DATA_PARTITION_ID, TEST_PARTITION_ID);
        headers.put(DpsHeaders.USER_EMAIL, TEST_USER_EMAIL);
        headers.put(DpsHeaders.CORRELATION_ID, TEST_CORRELATION_ID);
        return headers;
    }

    private RecordChangedMessages createRecordChangedMessages() {
        RecordChangedMessages messages = new RecordChangedMessages();
        messages.setData("test-record-data");
        messages.setAttributes(new HashMap<>());
        return messages;
    }
}
