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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSerializationContext;
import java.lang.reflect.Type;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.indexer.indexing.config.MessagingConfigProperties;
import org.opengroup.osdu.oqm.core.OqmDriver;
import org.opengroup.osdu.oqm.core.model.OqmDestination;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.opengroup.osdu.oqm.core.model.OqmTopic;

@ExtendWith(MockitoExtension.class)
class StatusPublisherImplTest {

    @Mock
    private OqmDriver driver;

    @Mock
    private MessagingConfigProperties properties;

    @Mock
    private JsonSerializer<JobStatus> statusJsonSerializer;

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private JobStatus jobStatus;

    @InjectMocks
    private StatusPublisherImpl statusPublisher;

    @Captor
    private ArgumentCaptor<OqmMessage> messageCaptor;

    @Captor
    private ArgumentCaptor<OqmTopic> topicCaptor;

    @Captor
    private ArgumentCaptor<OqmDestination> destinationCaptor;

    private static final String TEST_TOPIC_NAME = "test-status-topic";
    private static final String TEST_PARTITION_ID = "test-partition-123";
    private static final String TEST_ACCOUNT_ID = "test-account-456";
    private static final String TEST_CORRELATION_ID = "test-correlation-789";

    @BeforeEach
    void setUp() {
        lenient().when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);

        // Mock the JSON serializer to return a test JSON object
        lenient().when(statusJsonSerializer.serialize(any(JobStatus.class), any(Type.class), any(JsonSerializationContext.class)))
                .thenAnswer(invocation -> {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("status", "completed");
                    return jsonObject;
                });
    }

    @Test
    void setUpShouldInitializeSuccessfully() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);

        // Act
        statusPublisher.setUp();

        // Assert
        assertNotNull(statusPublisher);
        verify(properties, times(1)).getStatusChangedTopicName();
    }

    @Test
    void shouldPublishStatusToCorrectTopic() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver, times(1)).publish(any(OqmMessage.class), topicCaptor.capture(), any(OqmDestination.class));
        assertEquals(TEST_TOPIC_NAME, topicCaptor.getValue().getName());
    }

    @Test
    void shouldPublishToCorrectPartition() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver, times(1)).publish(any(OqmMessage.class), any(OqmTopic.class), destinationCaptor.capture());
        assertEquals(TEST_PARTITION_ID, destinationCaptor.getValue().getPartitionId());
    }

    @Test
    void shouldSerializeJobStatusToJson() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver, times(1)).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        assertNotNull(messageCaptor.getValue().getData());
    }

    @Test
    void shouldCallDriverPublishOnce() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver, times(1)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
    }

    @Test
    void shouldInvokeAddCorrelationIdIfMissing() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(dpsHeaders, times(1)).addCorrelationIdIfMissing();
    }

    @Test
    void shouldSetAccountIdAttributeCorrectly() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_ACCOUNT_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        Map<String, String> attributes = messageCaptor.getValue().getAttributes();
        assertEquals(TEST_ACCOUNT_ID, attributes.get(DpsHeaders.ACCOUNT_ID));
    }

    @Test
    void shouldSetDataPartitionIdAttributeCorrectly() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        Map<String, String> attributes = messageCaptor.getValue().getAttributes();
        assertEquals(TEST_PARTITION_ID, attributes.get(DpsHeaders.DATA_PARTITION_ID));
    }

    @Test
    void shouldSetCorrelationIdAttributeCorrectly() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        Map<String, String> attributes = messageCaptor.getValue().getAttributes();
        assertEquals(TEST_CORRELATION_ID, attributes.get(DpsHeaders.CORRELATION_ID));
    }

    @Test
    void shouldContainAllRequiredAttributes() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        Map<String, String> attributes = messageCaptor.getValue().getAttributes();
        assertEquals(3, attributes.size());
        assertNotNull(attributes.get(DpsHeaders.ACCOUNT_ID));
        assertNotNull(attributes.get(DpsHeaders.DATA_PARTITION_ID));
        assertNotNull(attributes.get(DpsHeaders.CORRELATION_ID));
    }

    @Test
    void shouldCallGetPartitionIdWithFallbackTwice() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(dpsHeaders, times(2)).getPartitionIdWithFallbackToAccountId();
    }

    @Test
    void shouldHandleDifferentPartitionIds() {
        // Arrange
        String differentPartitionId = "different-partition-999";
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(differentPartitionId);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(differentPartitionId);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver).publish(any(OqmMessage.class), any(OqmTopic.class), destinationCaptor.capture());
        assertEquals(differentPartitionId, destinationCaptor.getValue().getPartitionId());
    }

    @Test
    void shouldHandleDifferentCorrelationIds() {
        // Arrange
        String differentCorrelationId = "different-correlation-888";
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(differentCorrelationId);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        Map<String, String> attributes = messageCaptor.getValue().getAttributes();
        assertEquals(differentCorrelationId, attributes.get(DpsHeaders.CORRELATION_ID));
    }

    @Test
    void shouldHandleDifferentAccountAndPartitionIds() {
        // Arrange
        String accountId = "account-111";
        String partitionId = "partition-222";
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(partitionId);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(accountId);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver).publish(messageCaptor.capture(), any(OqmTopic.class), destinationCaptor.capture());
        assertEquals(partitionId, destinationCaptor.getValue().getPartitionId());
        Map<String, String> attributes = messageCaptor.getValue().getAttributes();
        assertEquals(accountId, attributes.get(DpsHeaders.ACCOUNT_ID));
        assertEquals(accountId, attributes.get(DpsHeaders.DATA_PARTITION_ID));
    }

    @Test
    void shouldHandleDifferentTopicNames() {
        // Arrange
        String differentTopicName = "different-topic-name";
        when(properties.getStatusChangedTopicName()).thenReturn(differentTopicName);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver).publish(any(OqmMessage.class), topicCaptor.capture(), any(OqmDestination.class));
        assertEquals(differentTopicName, topicCaptor.getValue().getName());
    }

    @Test
    void shouldHandleMultiplePublishCalls() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver, times(3)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
    }

    @Test
    void shouldCallGetPartitionIdForEachPublish() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(dpsHeaders, times(2)).getPartitionId();
    }

    @Test
    void shouldCallGetCorrelationIdForEachPublish() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(dpsHeaders, times(2)).getCorrelationId();
    }

    @Test
    void shouldCreateOqmMessageWithData() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        OqmMessage message = messageCaptor.getValue();
        assertNotNull(message.getData());
        assertNotNull(message.getAttributes());
    }

    @Test
    void shouldVerifyMessageAttributesNotNull() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver).publish(messageCaptor.capture(), any(OqmTopic.class), any(OqmDestination.class));
        OqmMessage message = messageCaptor.getValue();
        assertNotNull(message.getAttributes());
        assertEquals(3, message.getAttributes().size());
    }

    @Test
    void shouldVerifyTopicBuilderUsed() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(driver).publish(any(OqmMessage.class), topicCaptor.capture(), any(OqmDestination.class));
        OqmTopic topic = topicCaptor.getValue();
        assertEquals(TEST_TOPIC_NAME, topic.getName());
    }

    @Test
    void shouldVerifyCompletePublishFlow() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_ACCOUNT_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        statusPublisher.setUp();

        // Act
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert - Verify complete flow
        verify(dpsHeaders).addCorrelationIdIfMissing();
        verify(dpsHeaders).getPartitionId();
        verify(dpsHeaders, times(2)).getPartitionIdWithFallbackToAccountId();
        verify(dpsHeaders).getCorrelationId();

        // Capture and verify separately to avoid ambiguity
        verify(driver).publish(messageCaptor.capture(), topicCaptor.capture(), destinationCaptor.capture());

        OqmMessage message = messageCaptor.getValue();
        OqmTopic topic = topicCaptor.getValue();
        OqmDestination destination = destinationCaptor.getValue();

        assertNotNull(message.getData());
        assertNotNull(message.getAttributes());
        assertEquals(3, message.getAttributes().size());
        assertEquals(TEST_TOPIC_NAME, topic.getName());
        assertEquals(TEST_PARTITION_ID, destination.getPartitionId());
    }

    @Test
    void shouldVerifySetUpIsCalledBeforePublish() {
        // Arrange
        when(properties.getStatusChangedTopicName()).thenReturn(TEST_TOPIC_NAME);
        when(dpsHeaders.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(TEST_PARTITION_ID);
        when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);

        // Act
        statusPublisher.setUp();
        statusPublisher.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        // Assert
        verify(properties, times(1)).getStatusChangedTopicName();
        verify(driver, times(1)).publish(any(OqmMessage.class), any(OqmTopic.class), any(OqmDestination.class));
    }
}
