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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.oqm.core.model.OqmMessageReceiver;
import org.opengroup.osdu.oqm.core.model.OqmSubscriberThroughput;
import org.opengroup.osdu.indexer.api.RecordIndexerApi;
import org.opengroup.osdu.indexer.api.ReindexApi;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.indexer.indexing.config.MessagingConfigProperties;
import org.opengroup.osdu.indexer.indexing.processing.RecordsChangedMessageReceiver;
import org.opengroup.osdu.indexer.indexing.processing.ReindexMessageReceiver;
import org.opengroup.osdu.indexer.indexing.processing.ReprocessorMessageReceiver;
import org.opengroup.osdu.indexer.indexing.processing.SchemaChangedMessageReceiver;
import org.opengroup.osdu.indexer.indexing.scope.ThreadDpsHeaders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantSubscriberConfigurationTest {

    @Mock
    private MessagingConfigProperties properties;

    @Mock
    private OqmSubscriberManager subscriberManager;

    @Mock
    private ITenantFactory tenantInfoFactory;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private ThreadDpsHeaders headers;

    @Mock
    private RecordIndexerApi recordIndexerApi;

    @Mock
    private ReindexApi reindexApi;

    @Mock
    private IFeatureFlag featureFlag;

    @Captor
    private ArgumentCaptor<String> dataPartitionIdCaptor;

    @Captor
    private ArgumentCaptor<String> topicNameCaptor;

    @Captor
    private ArgumentCaptor<String> subscriptionNameCaptor;

    @Captor
    private ArgumentCaptor<OqmMessageReceiver> receiverCaptor;

    @Captor
    private ArgumentCaptor<OqmSubscriberThroughput> throughputCaptor;

    private TenantSubscriberConfiguration configuration;

    private static final String RECORDS_CHANGED_TOPIC = "records-changed";
    private static final String SCHEMA_CHANGED_TOPIC = "schema-changed";
    private static final String REPROCESS_TOPIC = "reprocess";
    private static final String REINDEX_TOPIC = "reindex";

    @BeforeEach
    void setUp() {
        configuration = new TenantSubscriberConfiguration(
                properties,
                subscriberManager,
                tenantInfoFactory,
                tokenProvider,
                headers,
                recordIndexerApi,
                reindexApi,
                featureFlag
        );

        // Setup default mock behavior
        when(properties.getRecordsChangedTopicName()).thenReturn(RECORDS_CHANGED_TOPIC);
        when(properties.getSchemaChangedTopicName()).thenReturn(SCHEMA_CHANGED_TOPIC);
        when(properties.getReprocessTopicName()).thenReturn(REPROCESS_TOPIC);
        when(properties.getReindexTopicName()).thenReturn(REINDEX_TOPIC);
    }

    @Test
    void postConstruct_shouldRegisterSubscribersForSingleTenant() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager, times(4)).registerSubscriber(
                any(String.class),
                any(String.class),
                any(String.class),
                any(OqmMessageReceiver.class),
                any(OqmSubscriberThroughput.class)
        );
    }

    @Test
    void postConstruct_shouldRegisterSubscribersForMultipleTenants() {
        // Arrange
        List<TenantInfo> tenants = Arrays.asList(
                createTenantInfo("tenant1"),
                createTenantInfo("tenant2"),
                createTenantInfo("tenant3")
        );
        when(tenantInfoFactory.listTenantInfo()).thenReturn(tenants);

        // Act
        configuration.postConstruct();

        // Assert
        // Each tenant gets 4 subscribers (records-changed, schema-changed, reprocess, reindex)
        verify(subscriberManager, times(12)).registerSubscriber(
                any(String.class),
                any(String.class),
                any(String.class),
                any(OqmMessageReceiver.class),
                any(OqmSubscriberThroughput.class)
        );
    }

    @Test
    void postConstruct_shouldRegisterRecordsChangedSubscriberWithCorrectParameters() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager).registerSubscriber(
                eq("tenant1"),
                eq(RECORDS_CHANGED_TOPIC),
                eq("indexer-" + RECORDS_CHANGED_TOPIC),
                any(RecordsChangedMessageReceiver.class),
                eq(OqmSubscriberThroughput.MAX)
        );
    }

    @Test
    void postConstruct_shouldRegisterSchemaChangedSubscriberWithCorrectParameters() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager).registerSubscriber(
                eq("tenant1"),
                eq(SCHEMA_CHANGED_TOPIC),
                eq("indexer-" + SCHEMA_CHANGED_TOPIC),
                any(SchemaChangedMessageReceiver.class),
                eq(OqmSubscriberThroughput.MIN)
        );
    }

    @Test
    void postConstruct_shouldRegisterReprocessSubscriberWithCorrectParameters() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager).registerSubscriber(
                eq("tenant1"),
                eq(REPROCESS_TOPIC),
                eq("indexer-" + REPROCESS_TOPIC),
                any(ReprocessorMessageReceiver.class),
                eq(OqmSubscriberThroughput.MIN)
        );
    }

    @Test
    void postConstruct_shouldRegisterReindexSubscriberWithCorrectParameters() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager).registerSubscriber(
                eq("tenant1"),
                eq(REINDEX_TOPIC),
                eq("indexer-" + REINDEX_TOPIC),
                any(ReindexMessageReceiver.class),
                eq(OqmSubscriberThroughput.MAX)
        );
    }

    @Test
    void postConstruct_shouldUseCorrectSubscriptionNamePrefix() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager, times(4)).registerSubscriber(
                any(String.class),
                any(String.class),
                subscriptionNameCaptor.capture(),
                any(OqmMessageReceiver.class),
                any(OqmSubscriberThroughput.class)
        );

        List<String> subscriptionNames = subscriptionNameCaptor.getAllValues();
        assertThat(subscriptionNames)
                .allMatch(name -> name.startsWith("indexer-"))
                .containsExactlyInAnyOrder(
                        "indexer-" + RECORDS_CHANGED_TOPIC,
                        "indexer-" + SCHEMA_CHANGED_TOPIC,
                        "indexer-" + REPROCESS_TOPIC,
                        "indexer-" + REINDEX_TOPIC
                );
    }

    @Test
    void postConstruct_shouldRegisterCorrectReceiverTypes() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager, times(4)).registerSubscriber(
                any(String.class),
                any(String.class),
                any(String.class),
                receiverCaptor.capture(),
                any(OqmSubscriberThroughput.class)
        );

        List<OqmMessageReceiver> receivers = receiverCaptor.getAllValues();
        assertThat(receivers)
                .hasSize(4)
                .hasAtLeastOneElementOfType(RecordsChangedMessageReceiver.class)
                .hasAtLeastOneElementOfType(SchemaChangedMessageReceiver.class)
                .hasAtLeastOneElementOfType(ReprocessorMessageReceiver.class)
                .hasAtLeastOneElementOfType(ReindexMessageReceiver.class);
    }

    @Test
    void postConstruct_shouldUseMaxThroughputForRecordsChangedAndReindex() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager, times(4)).registerSubscriber(
                any(String.class),
                topicNameCaptor.capture(),
                any(String.class),
                any(OqmMessageReceiver.class),
                throughputCaptor.capture()
        );

        List<String> topics = topicNameCaptor.getAllValues();
        List<OqmSubscriberThroughput> throughputs = throughputCaptor.getAllValues();

        for (int i = 0; i < topics.size(); i++) {
            String topic = topics.get(i);
            OqmSubscriberThroughput throughput = throughputs.get(i);

            if (RECORDS_CHANGED_TOPIC.equals(topic) || REINDEX_TOPIC.equals(topic)) {
                assertThat(throughput).isEqualTo(OqmSubscriberThroughput.MAX);
            }
        }
    }

    @Test
    void postConstruct_shouldUseMinThroughputForSchemaChangedAndReprocess() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager, times(4)).registerSubscriber(
                any(String.class),
                topicNameCaptor.capture(),
                any(String.class),
                any(OqmMessageReceiver.class),
                throughputCaptor.capture()
        );

        List<String> topics = topicNameCaptor.getAllValues();
        List<OqmSubscriberThroughput> throughputs = throughputCaptor.getAllValues();

        for (int i = 0; i < topics.size(); i++) {
            String topic = topics.get(i);
            OqmSubscriberThroughput throughput = throughputs.get(i);

            if (SCHEMA_CHANGED_TOPIC.equals(topic) || REPROCESS_TOPIC.equals(topic)) {
                assertThat(throughput).isEqualTo(OqmSubscriberThroughput.MIN);
            }
        }
    }

    @Test
    void postConstruct_shouldRegisterSubscribersForEachTenantSeparately() {
        // Arrange
        List<TenantInfo> tenants = Arrays.asList(
                createTenantInfo("tenant1"),
                createTenantInfo("tenant2")
        );
        when(tenantInfoFactory.listTenantInfo()).thenReturn(tenants);

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager, times(8)).registerSubscriber(
                dataPartitionIdCaptor.capture(),
                any(String.class),
                any(String.class),
                any(OqmMessageReceiver.class),
                any(OqmSubscriberThroughput.class)
        );

        List<String> dataPartitionIds = dataPartitionIdCaptor.getAllValues();
        assertThat(dataPartitionIds)
                .hasSize(8)
                .contains("tenant1", "tenant2");

        // Each tenant should have 4 registrations
        assertThat(dataPartitionIds.stream().filter(id -> id.equals("tenant1")).count()).isEqualTo(4);
        assertThat(dataPartitionIds.stream().filter(id -> id.equals("tenant2")).count()).isEqualTo(4);
    }

    @Test
    void postConstruct_shouldHandleEmptyTenantList() {
        // Arrange
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.emptyList());

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager, never()).registerSubscriber(
                any(String.class),
                any(String.class),
                any(String.class),
                any(OqmMessageReceiver.class),
                any(OqmSubscriberThroughput.class)
        );
    }

    @Test
    void postConstruct_shouldRetrieveTopicNamesFromProperties() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(properties, atLeastOnce()).getRecordsChangedTopicName();
        verify(properties, atLeastOnce()).getSchemaChangedTopicName();
        verify(properties, atLeastOnce()).getReprocessTopicName();
        verify(properties, atLeastOnce()).getReindexTopicName();
    }

    @Test
    void postConstruct_shouldUseCustomTopicNames() {
        // Arrange
        String customRecordsTopic = "custom-records-topic";
        String customSchemaTopic = "custom-schema-topic";
        String customReprocessTopic = "custom-reprocess-topic";
        String customReindexTopic = "custom-reindex-topic";

        when(properties.getRecordsChangedTopicName()).thenReturn(customRecordsTopic);
        when(properties.getSchemaChangedTopicName()).thenReturn(customSchemaTopic);
        when(properties.getReprocessTopicName()).thenReturn(customReprocessTopic);
        when(properties.getReindexTopicName()).thenReturn(customReindexTopic);

        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager).registerSubscriber(
                eq("tenant1"),
                eq(customRecordsTopic),
                eq("indexer-" + customRecordsTopic),
                any(RecordsChangedMessageReceiver.class),
                any(OqmSubscriberThroughput.class)
        );
        verify(subscriberManager).registerSubscriber(
                eq("tenant1"),
                eq(customSchemaTopic),
                eq("indexer-" + customSchemaTopic),
                any(SchemaChangedMessageReceiver.class),
                any(OqmSubscriberThroughput.class)
        );
        verify(subscriberManager).registerSubscriber(
                eq("tenant1"),
                eq(customReprocessTopic),
                eq("indexer-" + customReprocessTopic),
                any(ReprocessorMessageReceiver.class),
                any(OqmSubscriberThroughput.class)
        );
        verify(subscriberManager).registerSubscriber(
                eq("tenant1"),
                eq(customReindexTopic),
                eq("indexer-" + customReindexTopic),
                any(ReindexMessageReceiver.class),
                any(OqmSubscriberThroughput.class)
        );
    }

    @Test
    void postConstruct_shouldCreateNewReceiverInstancesForEachTenant() {
        // Arrange
        List<TenantInfo> tenants = Arrays.asList(
                createTenantInfo("tenant1"),
                createTenantInfo("tenant2")
        );
        when(tenantInfoFactory.listTenantInfo()).thenReturn(tenants);

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager, times(8)).registerSubscriber(
                any(String.class),
                any(String.class),
                any(String.class),
                receiverCaptor.capture(),
                any(OqmSubscriberThroughput.class)
        );

        List<OqmMessageReceiver> receivers = receiverCaptor.getAllValues();
        assertThat(receivers).hasSize(8);

        // Verify we have new instances for each tenant (not reusing the same instance)
        long distinctRecordsChangedReceivers = receivers.stream()
                .filter(RecordsChangedMessageReceiver.class::isInstance)
                .distinct()
                .count();
        assertThat(distinctRecordsChangedReceivers).isEqualTo(2);
    }

    @Test
    void postConstruct_shouldHandleTenantWithSpecialCharactersInId() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant-with-dashes_and_underscores");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager, times(4)).registerSubscriber(
                eq("tenant-with-dashes_and_underscores"),
                any(String.class),
                any(String.class),
                any(OqmMessageReceiver.class),
                any(OqmSubscriberThroughput.class)
        );
    }

    @Test
    void postConstruct_shouldRegisterAllFourSubscribersInCorrectOrder() {
        // Arrange
        TenantInfo tenantInfo = createTenantInfo("tenant1");
        when(tenantInfoFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));

        // Act
        configuration.postConstruct();

        // Assert
        verify(subscriberManager, times(4)).registerSubscriber(
                any(String.class),
                topicNameCaptor.capture(),
                any(String.class),
                any(OqmMessageReceiver.class),
                any(OqmSubscriberThroughput.class)
        );

        List<String> capturedTopics = topicNameCaptor.getAllValues();
        assertThat(capturedTopics).containsExactly(
                RECORDS_CHANGED_TOPIC,
                SCHEMA_CHANGED_TOPIC,
                REPROCESS_TOPIC,
                REINDEX_TOPIC
        );
    }

    // Helper method
    private TenantInfo createTenantInfo(String dataPartitionId) {
        TenantInfo tenantInfo = new TenantInfo();
        tenantInfo.setDataPartitionId(dataPartitionId);
        return tenantInfo;
    }
}
