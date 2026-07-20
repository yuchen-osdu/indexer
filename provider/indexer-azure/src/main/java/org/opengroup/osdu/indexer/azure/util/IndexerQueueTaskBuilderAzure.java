// Copyright © Azure
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

package org.opengroup.osdu.indexer.azure.util;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.TopicClient;
import lombok.extern.java.Log;
import org.apache.commons.collections.CollectionUtils;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.azure.servicebus.ITopicClientFactory;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.indexer.RecordQueryResponse;
import org.opengroup.osdu.core.common.model.indexer.RecordReindexRequest;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.indexer.azure.di.PublisherConfig;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.opengroup.osdu.indexer.model.Constants;
import org.opengroup.osdu.indexer.service.StorageService;
import org.opengroup.osdu.indexer.util.IndexerQueueTaskBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.opengroup.osdu.core.common.model.http.DpsHeaders.AUTHORIZATION;

@Log
@Component
@RequestScope
@Primary
public class IndexerQueueTaskBuilderAzure extends IndexerQueueTaskBuilder {
    private Gson gson = new Gson();

    @Autowired
    private ITopicClientFactory topicClientFactory;

    @Inject
    private IndexerConfigurationProperties configurationProperties;

    @Inject
    private JaxRsDpsLog logger;

    @Inject
    @Named("SERVICE_BUS_REINDEX_TOPIC")
    private String serviceBusReindexTopicName;

    @Inject
    private StorageService storageService;

    @Inject
    private RequestInfoImpl requestInfo;
    @Autowired
    private PublisherConfig publisherConfig;

    @Override
    public void createWorkerTask(String payload, DpsHeaders headers) {
        createWorkerTasks(payload, headers);
    }

    @Override
    public void createWorkerTask(String payload, Long countdownMillis, DpsHeaders headers) {
        createWorkerTasks(payload, headers);
    }

    @Override
    public void createReIndexTask(String payload, DpsHeaders headers) {
        publishAllRecordsToServiceBus(payload, headers);
    }

    @Override
    public void createReIndexTask(String payload, Long countdownMillis, DpsHeaders headers) {
        publishAllRecordsToServiceBus(payload, headers);
    }

    private void createWorkerTasks(String payload, DpsHeaders headers) {
        RecordChangedMessages receivedPayload = gson.fromJson(payload, RecordChangedMessages.class);
        List<RecordInfo> recordInfos = parseRecordsAsJSON(receivedPayload.getData());
        if(!CollectionUtils.isEmpty(recordInfos)) {
            Map<String, String> attributes = (receivedPayload.getAttributes() != null)
                    ? receivedPayload.getAttributes()
                    : new HashMap<>();
            createTasks(recordInfos, attributes, headers);
        }
    }

    private void publishAllRecordsToServiceBus(String payload, DpsHeaders headers) {
        // fetch all the remaining records
        // This logic is temporary and would be updated to call the storage service async.
        // Currently, the storage client can't be called out of request scope hence making the
        // storage calls sync here
        RecordReindexRequest recordReindexRequest = gson.fromJson(payload, RecordReindexRequest.class);
        final String recordKind = recordReindexRequest.getKind();
        RecordQueryResponse recordQueryResponse = null;

        try {
            do {
                headers.put(AUTHORIZATION, this.requestInfo.checkOrGetAuthorizationHeader());

                if (recordQueryResponse != null) {
                    recordReindexRequest = RecordReindexRequest.builder().cursor(recordQueryResponse.getCursor()).kind(recordKind).build();
                }
                recordQueryResponse = this.storageService.getRecordsByKind(recordReindexRequest);
                if (recordQueryResponse.getResults() != null && recordQueryResponse.getResults().size() != 0) {
                    List<String> recordIds = recordQueryResponse.getResults();
                    List<RecordInfo> recordInfos = recordIds.stream()
                            .map(record -> RecordInfo.builder().id(record).kind(recordKind).op(OperationType.create.name()).build()).collect(Collectors.toList());
                    createTasks(recordInfos, new HashMap<>(), headers);
                }
            } while (!Strings.isNullOrEmpty(recordQueryResponse.getCursor()) && recordQueryResponse.getResults().size() == configurationProperties.getStorageRecordsByKindBatchSize());

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unknown error", "An unknown error has occurred.", e);
        }
    }

    private void createTasks(List<RecordInfo> recordInfos, Map<String, String> attributes, DpsHeaders headers) {
        headers.addCorrelationIdIfMissing();
        List<List<RecordInfo>> batch = Lists.partition(recordInfos, publisherConfig.getPubSubBatchSize());
        for (List<RecordInfo> recordsBatch : batch) {
            createTask(recordsBatch, attributes, headers);
        }
    }

    private void createTask(List<RecordInfo> recordInfos, Map<String, String> attributes, DpsHeaders headers) {
        Message message = new Message();

        // properties
        Map<String, Object> properties = new HashMap<>();
        properties.put(DpsHeaders.ACCOUNT_ID, headers.getPartitionIdWithFallbackToAccountId());
        properties.put(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
        properties.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
        message.setProperties(properties);

        // add all to body {"message": {"data":[], "id":...}}
        JsonObject jo = new JsonObject();
        jo.add("data", gson.toJsonTree(recordInfos));
        jo.addProperty(DpsHeaders.ACCOUNT_ID, headers.getPartitionIdWithFallbackToAccountId());
        jo.addProperty(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
        jo.addProperty(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
        // Append the ancestry kinds used to prevent circular chasing
        boolean isChasingMessage = false;
        if(attributes.containsKey(Constants.ANCESTRY_KINDS)) {
            isChasingMessage = true;
            jo.addProperty(Constants.ANCESTRY_KINDS, attributes.get(Constants.ANCESTRY_KINDS));
        }
        JsonObject jomsg = new JsonObject();
        jomsg.add("message", jo);

        message.setBody(jomsg.toString().getBytes(StandardCharsets.UTF_8));
        message.setContentType("application/json");

        try {
            long startTime = System.currentTimeMillis();
            TopicClient topicClient = topicClientFactory.getClient(headers.getPartitionId(), serviceBusReindexTopicName);
            if(isChasingMessage) {
                Instant enqueueTimeUtc = Clock.systemUTC().instant().plus(Constants.CHASING_MESSAGE_DELAY_SECONDS, SECONDS);
                topicClient.scheduleMessageAsync(message, enqueueTimeUtc);
            }
            else {
                topicClient.send(message);
            }
            long stopTime = System.currentTimeMillis();
            logger.info(String.format("Indexer publishes message to Service Bus, messageId: %s | time taken to send message: %d milliseconds ", message.getMessageId(), stopTime - startTime));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private List<RecordInfo> parseRecordsAsJSON(String inputPayload) {
        Type type = new TypeToken<List<RecordInfo>>() {}.getType();
        List<RecordInfo> recordInfoList = gson.fromJson(inputPayload, type);
        return recordInfoList;
    }
}
