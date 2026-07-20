/*
 *  Copyright 2020-2023 Google LLC
 *  Copyright 2020-2023 EPAM Systems, Inc
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

import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.COLLABORATIONS_FEATURE_NAME;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.indexer.model.XcollaborationHolder;
import org.opengroup.osdu.oqm.core.model.OqmSubscriberThroughput;
import org.opengroup.osdu.indexer.api.RecordIndexerApi;
import org.opengroup.osdu.indexer.api.ReindexApi;
import org.opengroup.osdu.indexer.indexing.config.MessagingConfigProperties;
import org.opengroup.osdu.indexer.indexing.processing.RecordsChangedMessageReceiver;
import org.opengroup.osdu.indexer.indexing.processing.ReindexMessageReceiver;
import org.opengroup.osdu.indexer.indexing.processing.ReprocessorMessageReceiver;
import org.opengroup.osdu.indexer.indexing.processing.SchemaChangedMessageReceiver;
import org.opengroup.osdu.indexer.indexing.scope.ThreadDpsHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Subscription configuration class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSubscriberConfiguration {

  private static final String SUBSCRIPTION_PREFIX = "indexer-";
  private final MessagingConfigProperties properties;
  private final OqmSubscriberManager subscriberManager;
  private final ITenantFactory tenantInfoFactory;
  private final TokenProvider tokenProvider;
  private final ThreadDpsHeaders headers;
  private final RecordIndexerApi recordIndexerApi;
  private final ReindexApi reindexApi;
  private final IFeatureFlag featureFlagChecker;

  /**
   * Tenant configurations provided by the Partition service will be used to configure subscribers. If tenants use the
   * same message broker(The same RabbitMQ instance, or the same GCP project Pub/Sub) then only one subscriber in this
   * broker will be used.
   */
  @PostConstruct
  void postConstruct() {
    log.info("OqmSubscriberManager provisioning STARTED");
    String recordsChangedTopicName = properties.getRecordsChangedTopicName();
    String recordsChangedTopicNameV2 = properties.getRecordsChangedTopicNameV2();
    String schemaChangedTopicName = properties.getSchemaChangedTopicName();
    String reprocessTopicName = properties.getReprocessTopicName();
    String reindexTopicName = properties.getReindexTopicName();

    for (TenantInfo tenantInfo : tenantInfoFactory.listTenantInfo()) {
      String dataPartitionId = tenantInfo.getDataPartitionId();
      subscriberManager.registerSubscriber(
          dataPartitionId,
          recordsChangedTopicName,
          getSubscriptionName(recordsChangedTopicName),
          new RecordsChangedMessageReceiver(headers, tokenProvider, recordIndexerApi),
          OqmSubscriberThroughput.MAX
      );
      subscriberManager.registerSubscriber(
          dataPartitionId,
          schemaChangedTopicName,
          getSubscriptionName(schemaChangedTopicName),
          new SchemaChangedMessageReceiver(headers, tokenProvider, recordIndexerApi),
          OqmSubscriberThroughput.MIN
      );
      subscriberManager.registerSubscriber(
          dataPartitionId,
          reprocessTopicName,
          getSubscriptionName(reprocessTopicName),
          new ReprocessorMessageReceiver(headers, tokenProvider, reindexApi),
          OqmSubscriberThroughput.MIN
      );
      subscriberManager.registerSubscriber(
          dataPartitionId,
          reindexTopicName,
          getSubscriptionName(reindexTopicName),
          new ReindexMessageReceiver(headers, tokenProvider, recordIndexerApi),
          OqmSubscriberThroughput.MAX
      );
      if (featureFlagChecker.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME, dataPartitionId)) {
        subscriberManager.registerSubscriber(
            dataPartitionId,
            recordsChangedTopicNameV2,
            getSubscriptionName(recordsChangedTopicNameV2),
            new RecordsChangedMessageReceiver(headers, tokenProvider, recordIndexerApi),
            OqmSubscriberThroughput.MAX
        );
      }
    }
    log.info("OqmSubscriberManager provisioning COMPLETED");
  }

  private String getSubscriptionName(String topicName) {
    return SUBSCRIPTION_PREFIX + topicName;
  }
}
