// Copyright © Microsoft Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.indexer.azure.publish;


import com.azure.cosmos.implementation.Strings;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import com.microsoft.azure.servicebus.Message;
import org.opengroup.osdu.azure.servicebus.ITopicClientFactory;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.indexer.RecordStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.indexer.provider.interfaces.IPublisher;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequestScope
public class PublisherImpl implements IPublisher {


    @Inject
    private ITopicClientFactory topicClientFactory;

    @Inject
    private JaxRsDpsLog logger;

    @Inject
    @Named("SERVICE_BUS_TOPIC")
    private String serviceBusTopic;

    @Inject
    @Named("PUBLISH_TO_SERVICE_BUS_INDEXERSTATUS_TOPIC_ENABLED")
    private boolean shouldPublishToServiceBusTopic;

    @Override
    public void publishStatusChangedTagsToTopic(DpsHeaders headers, JobStatus indexerBatchStatus) throws Exception {
        String tenant = headers.getPartitionId();
        if (Strings.isNullOrEmpty(tenant))
            tenant = headers.getAccountId();

        Message message = new Message();

        RecordChangedMessages recordChangedMessages = getRecordChangedMessage(headers, indexerBatchStatus);
        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(recordChangedMessages);
        message.setBody(json.getBytes(StandardCharsets.UTF_8));
        message.setContentType("application/json");

        try {
            if(shouldPublishToServiceBusTopic) {
                logger.debug("Indexer publishes message " + headers.getCorrelationId());
                topicClientFactory.getClient(headers.getPartitionId(), serviceBusTopic).send(message);
            }
        }
        catch (Exception e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    private RecordChangedMessages getRecordChangedMessage(DpsHeaders headers, JobStatus indexerBatchStatus) {

        Gson gson = new GsonBuilder().create();
        Map<String, String> attributesMap = new HashMap<>();
        Type listType = new TypeToken<List<RecordStatus>>() {
        }.getType();

        JsonElement statusChangedTagsJson = gson.toJsonTree(indexerBatchStatus.getStatusesList(), listType);
        String statusChangedTagsData = (statusChangedTagsJson.toString());

        String tenant = headers.getPartitionId();
        // This code it to provide backward compatibility to slb-account-id
        if (!Strings.isNullOrEmpty(tenant)) {
            attributesMap.put(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
        } else {
            attributesMap.put(DpsHeaders.ACCOUNT_ID, headers.getPartitionIdWithFallbackToAccountId());
        }
        headers.addCorrelationIdIfMissing();
        attributesMap.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());


        RecordChangedMessages recordChangedMessages = new RecordChangedMessages();
        // statusChangedTagsData is not ByteString but String
        recordChangedMessages.setData(statusChangedTagsData);
        recordChangedMessages.setAttributes(attributesMap);




        return recordChangedMessages;
    }
}
