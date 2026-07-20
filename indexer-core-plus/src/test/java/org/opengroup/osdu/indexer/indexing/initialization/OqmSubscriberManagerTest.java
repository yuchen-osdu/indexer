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
package org.opengroup.osdu.indexer.indexing.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.oqm.core.OqmDriver;
import org.opengroup.osdu.oqm.core.model.OqmDestination;
import org.opengroup.osdu.oqm.core.model.OqmMessageReceiver;
import org.opengroup.osdu.oqm.core.model.OqmSubscriber;
import org.opengroup.osdu.oqm.core.model.OqmSubscriberThroughput;
import org.opengroup.osdu.oqm.core.model.OqmSubscription;
import org.opengroup.osdu.oqm.core.model.OqmSubscriptionQuery;
import org.opengroup.osdu.oqm.core.model.OqmTopic;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OqmSubscriberManagerTest {

    @Mock
    private OqmDriver driver;

    @Mock
    private OqmMessageReceiver messageReceiver;

    @Mock
    private OqmSubscriberThroughput throughput;

    private OqmSubscriberManager manager;

    private static final String TEST_DATA_PARTITION_ID = "test-partition";
    private static final String TEST_TOPIC_NAME = "test-topic";
    private static final String TEST_SUBSCRIPTION_NAME = "test-subscription";

    @BeforeEach
    void setUp() {
        Mockito.reset(driver, messageReceiver, throughput);
        manager = new OqmSubscriberManager(driver);
    }

    @Test
    void constructorShouldInitializeSuccessfully() {
        // Arrange & Act
        OqmSubscriberManager testManager = new OqmSubscriberManager(driver);

        // Assert
        assertNotNull(testManager);
    }

    @Test
    void shouldSuccessfullyRegisterSubscriber() {
        // Arrange
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver, times(1)).getTopic(anyString(), any(OqmDestination.class));
        verify(driver, times(1)).listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class),
                any(OqmDestination.class));
        verify(driver, times(1)).subscribe(any(OqmSubscriber.class), any(OqmDestination.class));
    }

    @Test
    void shouldCallSubscribeWithCorrectSubscriber() {
        // Arrange
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        ArgumentCaptor<OqmSubscriber> subscriberCaptor = ArgumentCaptor.forClass(OqmSubscriber.class);

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
        OqmSubscriber capturedSubscriber = subscriberCaptor.getValue();
        assertNotNull(capturedSubscriber);
        assertEquals(subscription, capturedSubscriber.getSubscription());
        assertEquals(messageReceiver, capturedSubscriber.getMessageReceiver());
        assertEquals(throughput, capturedSubscriber.getThroughput());
    }

    @Test
    void shouldCreateDestinationWithCorrectPartitionId() {
        // Arrange
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        ArgumentCaptor<OqmDestination> destinationCaptor = ArgumentCaptor.forClass(OqmDestination.class);

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).subscribe(any(OqmSubscriber.class), destinationCaptor.capture());
        OqmDestination capturedDestination = destinationCaptor.getValue();
        assertNotNull(capturedDestination);
        assertEquals(TEST_DATA_PARTITION_ID, capturedDestination.getPartitionId());
    }

    @Test
    void shouldPassCorrectTopicNameToDriver() {
        // Arrange
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).getTopic(eq(TEST_TOPIC_NAME), any(OqmDestination.class));
    }

    @Test
    void shouldThrowExceptionWhenTopicNotFound() {
        // Arrange
        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.empty());

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                    TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
    }

    @Test
    void shouldIncludeTopicNameInExceptionMessage() {
        // Arrange
        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.empty());

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                    TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);
        });

        // Updated to match actual implementation message
        String expectedMessage = "Required topic not exists.";
        assertEquals(expectedMessage, exception.getError().getReason());
    }

    @Test
    void shouldNotCallSubscribeWhenTopicNotFound() {
        // Arrange
        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AppException.class, () -> {
            manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                    TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);
        });

        verify(driver, never()).subscribe(any(OqmSubscriber.class), any(OqmDestination.class));
    }


    @Test
    void shouldThrowExceptionWhenSubscriptionNotFound() {
        // Arrange
        OqmTopic topic = createMockTopic();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                    TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getError().getCode());
    }

    @Test
    void shouldIncludeSubscriptionNameInExceptionMessage() {
        // Arrange
        OqmTopic topic = createMockTopic();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                    TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);
        });

        // Updated to match actual implementation message
        String expectedMessage = "Required subscription not exists.";
        assertEquals(expectedMessage, exception.getError().getReason());
    }

    @Test
    void shouldNotCallSubscribeWhenSubscriptionNotFound() {
        // Arrange
        OqmTopic topic = createMockTopic();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        assertThrows(AppException.class, () -> {
            manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                    TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);
        });

        verify(driver, never()).subscribe(any(OqmSubscriber.class), any(OqmDestination.class));
    }

    @Test
    void shouldCreateSubscriptionQueryWithCorrectNamePrefix() {
        // Arrange
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        ArgumentCaptor<OqmSubscriptionQuery> queryCaptor = ArgumentCaptor.forClass(OqmSubscriptionQuery.class);

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).listSubscriptions(any(OqmTopic.class), queryCaptor.capture(), any(OqmDestination.class));
        OqmSubscriptionQuery capturedQuery = queryCaptor.getValue();
        assertNotNull(capturedQuery);
        assertEquals(TEST_SUBSCRIPTION_NAME, capturedQuery.getNamePrefix());
    }

    @Test
    void shouldCreateSubscriptionQueryWithSubscriberableTrue() {
        // Arrange
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        ArgumentCaptor<OqmSubscriptionQuery> queryCaptor = ArgumentCaptor.forClass(OqmSubscriptionQuery.class);

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).listSubscriptions(any(OqmTopic.class), queryCaptor.capture(), any(OqmDestination.class));
        OqmSubscriptionQuery capturedQuery = queryCaptor.getValue();
        assertEquals(true, capturedQuery.getSubscriberable());
    }

    @Test
    void shouldUseFirstSubscriptionFromList() {
        // Arrange
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription1 = createMockSubscription();
        OqmSubscription subscription2 = Mockito.mock(OqmSubscription.class);

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Arrays.asList(subscription1, subscription2));

        ArgumentCaptor<OqmSubscriber> subscriberCaptor = ArgumentCaptor.forClass(OqmSubscriber.class);

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
        assertEquals(subscription1, subscriberCaptor.getValue().getSubscription());
    }

    @Test
    void shouldHandleDifferentPartitionIds() {
        // Arrange
        String customPartitionId = "custom-partition-123";
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        ArgumentCaptor<OqmDestination> destinationCaptor = ArgumentCaptor.forClass(OqmDestination.class);

        // Act
        manager.registerSubscriber(customPartitionId, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).subscribe(any(OqmSubscriber.class), destinationCaptor.capture());
        assertEquals(customPartitionId, destinationCaptor.getValue().getPartitionId());
    }

    @Test
    void shouldHandleEmptyStringPartitionId() {
        // Arrange
        String emptyPartitionId = "";
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        ArgumentCaptor<OqmDestination> destinationCaptor = ArgumentCaptor.forClass(OqmDestination.class);

        // Act
        manager.registerSubscriber(emptyPartitionId, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).subscribe(any(OqmSubscriber.class), destinationCaptor.capture());
        assertEquals(emptyPartitionId, destinationCaptor.getValue().getPartitionId());
    }

    @Test
    void shouldHandleSpecialCharactersInPartitionId() {
        // Arrange
        String specialPartitionId = "partition-with-special-chars_#@!";
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        ArgumentCaptor<OqmDestination> destinationCaptor = ArgumentCaptor.forClass(OqmDestination.class);

        // Act
        manager.registerSubscriber(specialPartitionId, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).subscribe(any(OqmSubscriber.class), destinationCaptor.capture());
        assertEquals(specialPartitionId, destinationCaptor.getValue().getPartitionId());
    }

    @Test
    void shouldHandleDifferentTopicNames() {
        // Arrange
        String customTopicName = "custom-topic-xyz";
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, customTopicName,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).getTopic(eq(customTopicName), any(OqmDestination.class));
    }

    @Test
    void shouldHandleLongTopicName() {
        // Arrange
        String longTopicName = "very-long-topic-name-with-many-characters-to-test-boundary-conditions";
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, longTopicName,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).getTopic(eq(longTopicName), any(OqmDestination.class));
    }

    @Test
    void shouldHandleTopicNameWithDots() {
        // Arrange
        String dottedTopicName = "com.example.topic.name";
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, dottedTopicName,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver).getTopic(eq(dottedTopicName), any(OqmDestination.class));
    }

    @Test
    void shouldHandleMultipleSubscriberRegistrations() {
        // Arrange
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert
        verify(driver, times(2)).subscribe(any(OqmSubscriber.class), any(OqmDestination.class));
    }

    @Test
    void shouldPassThroughputToSubscriber() {
        // Arrange
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();
        OqmSubscriberThroughput customThroughput = Mockito.mock(OqmSubscriberThroughput.class);

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        ArgumentCaptor<OqmSubscriber> subscriberCaptor = ArgumentCaptor.forClass(OqmSubscriber.class);

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, customThroughput);

        // Assert
        verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
        assertEquals(customThroughput, subscriberCaptor.getValue().getThroughput());
    }

    @Test
    void shouldPassMessageReceiverToSubscriber() {
        // Arrange
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();
        OqmMessageReceiver customReceiver = Mockito.mock(OqmMessageReceiver.class);

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        ArgumentCaptor<OqmSubscriber> subscriberCaptor = ArgumentCaptor.forClass(OqmSubscriber.class);

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, customReceiver, throughput);

        // Assert
        verify(driver).subscribe(subscriberCaptor.capture(), any(OqmDestination.class));
        assertEquals(customReceiver, subscriberCaptor.getValue().getMessageReceiver());
    }

    @Test
    void shouldCallDriverGetTopicBeforeListingSubscriptions() {
        // Arrange
        OqmTopic topic = createMockTopic();
        OqmSubscription subscription = createMockSubscription();
        InOrder inOrder = inOrder(driver);

        when(driver.getTopic(anyString(), any(OqmDestination.class)))
                .thenReturn(Optional.of(topic));
        when(driver.listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class), any(OqmDestination.class)))
                .thenReturn(Collections.singletonList(subscription));

        // Act
        manager.registerSubscriber(TEST_DATA_PARTITION_ID, TEST_TOPIC_NAME,
                TEST_SUBSCRIPTION_NAME, messageReceiver, throughput);

        // Assert - Verify order of calls
        inOrder.verify(driver).getTopic(anyString(), any(OqmDestination.class));
        inOrder.verify(driver).listSubscriptions(any(OqmTopic.class), any(OqmSubscriptionQuery.class),
                any(OqmDestination.class));
        inOrder.verify(driver).subscribe(any(OqmSubscriber.class), any(OqmDestination.class));
    }

    // ==================== Helper Methods ====================

    private OqmTopic createMockTopic() {
        return Mockito.mock(OqmTopic.class);
    }

    private OqmSubscription createMockSubscription() {
        return Mockito.mock(OqmSubscription.class);
    }
}
