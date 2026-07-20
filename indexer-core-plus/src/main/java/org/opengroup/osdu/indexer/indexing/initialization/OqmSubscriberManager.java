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

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OqmSubscriberManager {

  private final OqmDriver driver;

  public void registerSubscriber(String dataPartitionId, String topicName, String subscriptionName,
      OqmMessageReceiver messageReceiver, OqmSubscriberThroughput throughput) {
    OqmSubscription subscriptionForTenant = getSubscriptionForTenant(dataPartitionId, topicName, subscriptionName);
    log.info("OQM: registering Subscriber for subscription {}", subscriptionName);
    log.info("dataPartitionId: {}, topicName: {}, subscriptionName: {}, subscriptionForTenant: {}",
        dataPartitionId, topicName, subscriptionName, subscriptionForTenant);

    OqmDestination destination = getDestination(dataPartitionId);
    OqmSubscriber subscriber = OqmSubscriber.builder()
        .subscription(subscriptionForTenant)
        .messageReceiver(messageReceiver)
        .throughput(throughput)
        .build();
    log.info("subscriber: {}, destination: {}", subscriber, destination);

    driver.subscribe(subscriber, destination);
    log.info("OQM: provisioning subscription {}: Subscriber REGISTERED.", subscriptionName);
  }

  private OqmSubscription getSubscriptionForTenant(String dataPartitionId, String topicName, String subscriptionName) {
    log.info("OQM: provisioning tenant {}:", dataPartitionId);
    log.info("OQM: check for topic {} existence:", topicName);
    OqmTopic topic = driver.getTopic(topicName, getDestination(dataPartitionId)).orElse(null);

    if (topic == null) {
      log.error("OQM: check for topic: {}, tenant: {} existence: ABSENT.", topicName, dataPartitionId);
      throw new AppException(
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "Required topic not exists.",
          String.format(
              "Required topic not exists. Create topic: %s for tenant: %s and restart service.",
              topicName, dataPartitionId
          )
      );
    }

    log.info("OQM: check for topic {} existence: PRESENT", topicName);
    OqmSubscription subscription = getSubscription(dataPartitionId, topic, subscriptionName);

    if (subscription == null) {
      log.error(
          "OQM: check for subscription {}, tenant: {} existence: ABSENT.",
          subscriptionName,
          dataPartitionId
      );
      throw new AppException(
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "Required subscription not exists.",
          String.format(
              "Required subscription not exists. Create subscription: %s for tenant: %s and restart service.",
              subscriptionName,
              dataPartitionId
          )
      );
    }
    log.info("OQM: provisioning tenant {}: COMPLETED.", dataPartitionId);
    return subscription;
  }

  @Nullable
  private OqmSubscription getSubscription(String dataPartitionId, OqmTopic topic, String subscriptionName) {
    log.info("OQM: check for subscription {} existence:", subscriptionName);
    OqmSubscriptionQuery query = OqmSubscriptionQuery.builder()
        .namePrefix(subscriptionName)
        .subscriberable(true)
        .build();
    return driver
        .listSubscriptions(topic, query, getDestination(dataPartitionId)).stream()
        .findAny()
        .orElse(null);
  }

  private OqmDestination getDestination(String dataPartitionId) {
    return OqmDestination.builder()
        .partitionId(dataPartitionId)
        .build();
  }
}
