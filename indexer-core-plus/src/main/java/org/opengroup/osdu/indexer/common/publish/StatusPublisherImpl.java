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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import java.util.HashMap;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.oqm.core.OqmDriver;
import org.opengroup.osdu.oqm.core.model.OqmDestination;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.opengroup.osdu.oqm.core.model.OqmTopic;
import org.opengroup.osdu.indexer.indexing.config.MessagingConfigProperties;
import org.opengroup.osdu.indexer.provider.interfaces.IPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatusPublisherImpl implements IPublisher {

    private final OqmDriver driver;
    private final MessagingConfigProperties properties;
    private final JsonSerializer<JobStatus> statusJsonSerializer;
    private OqmTopic oqmTopic;
    private Gson gson;

    @PostConstruct
    public void setUp() {
        this.oqmTopic = OqmTopic.builder().name(properties.getStatusChangedTopicName()).build();
        this.gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(JobStatus.class, statusJsonSerializer)
            .create();
    }

  @Override
    public void publishStatusChangedTagsToTopic(DpsHeaders headers, JobStatus indexerBatchStatus) {
        OqmDestination oqmDestination =
            OqmDestination.builder().partitionId(headers.getPartitionId()).build();
        String json = this.gson.toJson(indexerBatchStatus);

        Map<String, String> attributes = getAttributes(headers);
        OqmMessage oqmMessage = OqmMessage.builder().data(json).attributes(attributes).build();
        driver.publish(oqmMessage, oqmTopic, oqmDestination);
    }

    private Map<String, String> getAttributes(DpsHeaders headers) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(DpsHeaders.ACCOUNT_ID, headers.getPartitionIdWithFallbackToAccountId());
        attributes.put(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
        headers.addCorrelationIdIfMissing();
        attributes.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
        return attributes;
    }
}
