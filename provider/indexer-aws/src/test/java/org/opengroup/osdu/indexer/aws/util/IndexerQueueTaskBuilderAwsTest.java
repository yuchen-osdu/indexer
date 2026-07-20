/**
* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*      http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.opengroup.osdu.indexer.aws.util;

import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.v2.sqs.AmazonSQSConfig;
import org.opengroup.osdu.core.aws.v2.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.v2.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.indexer.model.Constants;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import org.opengroup.osdu.indexer.model.XcollaborationHolder;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IndexerQueueTaskBuilderAwsTest {

    private String payload = "{ \"messageId\" : \"messageId\", \"publishTime\" : \"publishTime\", \"data\" : \"data\", \"attributes\" : { \"attribute\" : \"attribute\" } }";
    private String payload_ancestry_kinds = "{ \"messageId\" : \"messageId\", \"publishTime\" : \"publishTime\", \"data\" : \"data\", \"attributes\" : { \"attribute\" : \"attribute\", \"ancestry_kinds\" : \"ancestry_kinds\" } }";
    private String payload_retry = "{ \"messageId\" : \"messageId\", \"publishTime\" : \"publishTime\", \"data\" : \"data\", \"attributes\" : { \"attribute\" : \"attribute\" , \"retry\" : \"11\" } }";
    private static final int INITIAL_RETRY_DELAY_SECONDS = 5;
    private final String retryString = "retry";
    private final Long countDownMillis = 123456L;
    private final String storage_sqs_url = "storage_sqs_url";
    private final String deadletter_queue_sqs_url = "deadletter_queue_sqs_url";

    @InjectMocks
    IndexerQueueTaskBuilderAws builder;

    @Mock
    SqsClient sqsClient;

    @Mock
    Gson gson;

    @Mock
    XcollaborationHolder xCollaborationHolder;

    String xCollabHeaderValue = "id=96d5550e-2b5e-4b84-825c-646339ee5fc7,application=pws";
    String xCollabFieldValue = "96d5550e-2b5e-4b84-825c-646339ee5fc7";

    @Test
    public void createWorkerTaskTest_with_out_retryString() throws K8sParameterNotFoundException{

        Gson realGson = new Gson();

        DpsHeaders headers = new DpsHeaders();

        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put(DpsHeaders.ACCOUNT_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                .build());
        messageAttributes.put(DpsHeaders.DATA_PARTITION_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                .build());
        headers.addCorrelationIdIfMissing();
        messageAttributes.put(DpsHeaders.CORRELATION_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getCorrelationId())
                .build());
        messageAttributes.put(DpsHeaders.USER_EMAIL, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getUserEmail())
                .build());
        messageAttributes.put(DpsHeaders.AUTHORIZATION, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getAuthorization())
                .build());
        messageAttributes.put(retryString, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(String.valueOf(1))
                .build());

        RecordChangedMessages message = realGson.fromJson(payload, RecordChangedMessages.class);

        when(gson.fromJson(payload, RecordChangedMessages.class)).thenReturn(message);

        SendMessageRequest sendMessageRequest = SendMessageRequest.builder().queueUrl(null).messageBody(message.getData()).delaySeconds(INITIAL_RETRY_DELAY_SECONDS).messageAttributes(messageAttributes).build();

        builder.createWorkerTask(payload, headers);

        builder.createWorkerTask(payload, countDownMillis, headers);

        Mockito.verify(sqsClient, times(2)).sendMessage(Mockito.eq(sendMessageRequest));

    }

    @Test
    public void createWorkerTaskTest_with_retryString() throws K8sParameterNotFoundException{

        Gson realGson = new Gson();

        DpsHeaders headers = new DpsHeaders();

        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put(DpsHeaders.ACCOUNT_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                .build());
        messageAttributes.put(DpsHeaders.DATA_PARTITION_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                .build());
        headers.addCorrelationIdIfMissing();
        messageAttributes.put(DpsHeaders.CORRELATION_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getCorrelationId())
                .build());
        messageAttributes.put(DpsHeaders.USER_EMAIL, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getUserEmail())
                .build());
        messageAttributes.put(DpsHeaders.AUTHORIZATION, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getAuthorization())
                .build());
        messageAttributes.put(retryString, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(String.valueOf(1))
                .build());

        RecordChangedMessages message = realGson.fromJson(payload_retry, RecordChangedMessages.class);

        when(gson.fromJson(payload, RecordChangedMessages.class)).thenReturn(message);

        SendMessageRequest sendMessageRequest = SendMessageRequest.builder().queueUrl(null).messageBody(message.getData()).build();

        builder.createWorkerTask(payload, headers);

        builder.createWorkerTask(payload, countDownMillis, headers);

        Mockito.verify(sqsClient, times(2)).sendMessage(Mockito.eq(sendMessageRequest));

    }

    @Test
    public void createWorkerTaskTest_with_ancestry_kinds() throws K8sParameterNotFoundException{

        Gson realGson = new Gson();

        DpsHeaders headers = new DpsHeaders();

        RecordChangedMessages message = realGson.fromJson(payload_ancestry_kinds, RecordChangedMessages.class);

        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put(DpsHeaders.ACCOUNT_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                .build());
        messageAttributes.put(DpsHeaders.DATA_PARTITION_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                .build());
        headers.addCorrelationIdIfMissing();
        messageAttributes.put(DpsHeaders.CORRELATION_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getCorrelationId())
                .build());
        messageAttributes.put(DpsHeaders.USER_EMAIL, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getUserEmail())
                .build());
        messageAttributes.put(DpsHeaders.AUTHORIZATION, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getAuthorization())
                .build());
        messageAttributes.put(retryString, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(String.valueOf(1))
                .build());
        messageAttributes.put(Constants.ANCESTRY_KINDS, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(message.getAttributes().get(Constants.ANCESTRY_KINDS))
                .build());

      

        when(gson.fromJson(payload, RecordChangedMessages.class)).thenReturn(message);

        int delay = Math.max(INITIAL_RETRY_DELAY_SECONDS, Constants.CHASING_MESSAGE_DELAY_SECONDS);

        SendMessageRequest sendMessageRequest = SendMessageRequest.builder().queueUrl(null).messageBody(message.getData()).delaySeconds(delay).messageAttributes(messageAttributes).build();

        builder.createWorkerTask(payload, headers);

        builder.createWorkerTask(payload, countDownMillis, headers);

        Mockito.verify(sqsClient, times(2)).sendMessage(Mockito.eq(sendMessageRequest));

    }

    @Test
    public void createReIndexTaskTest() throws K8sParameterNotFoundException{

        DpsHeaders headers = new DpsHeaders();

        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put(DpsHeaders.ACCOUNT_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                .build());
        messageAttributes.put(DpsHeaders.DATA_PARTITION_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                .build());
        headers.addCorrelationIdIfMissing();
        messageAttributes.put(DpsHeaders.CORRELATION_ID, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getCorrelationId())
                .build());
        messageAttributes.put(DpsHeaders.USER_EMAIL, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getUserEmail())
                .build());
        messageAttributes.put(DpsHeaders.AUTHORIZATION, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(headers.getAuthorization())
                .build());
        messageAttributes.put("ReIndexCursor", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue("True")
                .build());

        SendMessageRequest sendMessageRequest = SendMessageRequest.builder().queueUrl(null).messageBody(payload).messageAttributes(messageAttributes).build();

        builder.createReIndexTask(payload, headers);

        builder.createReIndexTask(payload, countDownMillis, headers);

        Mockito.verify(sqsClient, times(2)).sendMessage(Mockito.eq(sendMessageRequest));

    }

    @Test
    public void getWaitTimeExpTest() throws K8sParameterNotFoundException{
        
        int zero_wait_time = IndexerQueueTaskBuilderAws.getWaitTimeExp(0);

        assertEquals(0, zero_wait_time);

        int non_zero_wait_time = IndexerQueueTaskBuilderAws.getWaitTimeExp(4);

        assertEquals(64, non_zero_wait_time);
    }

    @Test
    public void go_through_init_StorageQueue() throws Exception  {

        try (MockedConstruction<K8sLocalParameterProvider> provider = Mockito.mockConstruction(K8sLocalParameterProvider.class, (mock, context) -> {
                                                                                                                when(mock.getParameterAsString(eq("STORAGE_V2_SQS_URL"))).thenReturn(storage_sqs_url);
                                                                                                                when(mock.getParameterAsString("INDEXER_DEADLETTER_QUEUE_SQS_URL")).thenReturn(deadletter_queue_sqs_url);
                                                                                                            })) {

            try (MockedConstruction<AmazonSQSConfig> config = Mockito.mockConstruction(AmazonSQSConfig.class, (mock1, context) -> {
                                                                                                                when(mock1.AmazonSQS()).thenReturn(sqsClient);
                                                                                                            })) {

                builder.init();

                Gson realGson = new Gson();

                DpsHeaders headers = new DpsHeaders();

                Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
                messageAttributes.put(DpsHeaders.ACCOUNT_ID, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                        .build());
                messageAttributes.put(DpsHeaders.DATA_PARTITION_ID, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                        .build());
                headers.addCorrelationIdIfMissing();
                messageAttributes.put(DpsHeaders.CORRELATION_ID, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getCorrelationId())
                        .build());
                messageAttributes.put(DpsHeaders.USER_EMAIL, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getUserEmail())
                        .build());
                messageAttributes.put(DpsHeaders.AUTHORIZATION, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getAuthorization())
                        .build());
                messageAttributes.put(retryString, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(String.valueOf(1))
                        .build());

                when(xCollaborationHolder.isFeatureEnabledAndHeaderExists()).thenReturn(false);

                RecordChangedMessages message = realGson.fromJson(payload, RecordChangedMessages.class);

                SendMessageRequest sendMessageRequest = SendMessageRequest.builder().queueUrl(storage_sqs_url).messageBody(message.getData()).delaySeconds(INITIAL_RETRY_DELAY_SECONDS).messageAttributes(messageAttributes).build();

                builder.createWorkerTask(payload, headers);

                builder.createWorkerTask(payload, countDownMillis, headers);

                Mockito.verify(sqsClient, times(2)).sendMessage(Mockito.eq(sendMessageRequest));

            }
        }

    }

    @Test
    public void go_through_init_StorageQueue_with_x_collab() throws Exception  {

        try (MockedConstruction<K8sLocalParameterProvider> provider = Mockito.mockConstruction(K8sLocalParameterProvider.class, (mock, context) -> {
            when(mock.getParameterAsString(eq("STORAGE_V2_SQS_URL"))).thenReturn(storage_sqs_url);
            when(mock.getParameterAsString("INDEXER_DEADLETTER_QUEUE_SQS_URL")).thenReturn(deadletter_queue_sqs_url);
        })) {

            try (MockedConstruction<AmazonSQSConfig> config = Mockito.mockConstruction(AmazonSQSConfig.class, (mock1, context) -> {
                when(mock1.AmazonSQS()).thenReturn(sqsClient);
            })) {

                builder.init();

                Gson realGson = new Gson();

                DpsHeaders headers = new DpsHeaders();

                Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
                messageAttributes.put(DpsHeaders.ACCOUNT_ID, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                        .build());
                messageAttributes.put(DpsHeaders.DATA_PARTITION_ID, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                        .build());
                headers.addCorrelationIdIfMissing();
                messageAttributes.put(DpsHeaders.CORRELATION_ID, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getCorrelationId())
                        .build());
                messageAttributes.put(DpsHeaders.USER_EMAIL, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getUserEmail())
                        .build());
                messageAttributes.put(DpsHeaders.AUTHORIZATION, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getAuthorization())
                        .build());
                messageAttributes.put(retryString, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(String.valueOf(1))
                        .build());
                messageAttributes.put(XcollaborationHolder.X_COLLABORATION, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(xCollabFieldValue)
                        .build());

                headers.getHeaders().put(DpsHeaders.COLLABORATION, xCollabHeaderValue);

                xCollaborationHolder.setxCollaborationHeader(xCollabHeaderValue);
                when(xCollaborationHolder.isFeatureEnabledAndHeaderExists()).thenReturn(true);
                CollaborationContext build = CollaborationContext.builder()
                    .id(UUID.fromString(xCollabFieldValue))
                    .build();
                Optional<CollaborationContext> collaborationContext = Optional.of(build);
                when(xCollaborationHolder.getCollaborationContext()).thenReturn(collaborationContext);
                XcollaborationHolder xcollaborationHolder = new XcollaborationHolder();
                xcollaborationHolder.setCollaborationContext(collaborationContext);

                RecordChangedMessages message = realGson.fromJson(payload, RecordChangedMessages.class);

                SendMessageRequest sendMessageRequest = SendMessageRequest.builder().queueUrl(storage_sqs_url).messageBody(message.getData()).delaySeconds(INITIAL_RETRY_DELAY_SECONDS).messageAttributes(messageAttributes).build();

                builder.createWorkerTask(payload, headers);

                builder.createWorkerTask(payload, countDownMillis, headers);

                Mockito.verify(sqsClient, times(2)).sendMessage(Mockito.eq(sendMessageRequest));

            }
        }

    }

    @Test
    public void go_through_init_DLQ() throws Exception  {

        try (MockedConstruction<K8sLocalParameterProvider> provider = Mockito.mockConstruction(K8sLocalParameterProvider.class, (mock, context) -> {
                                                                                                                when(mock.getParameterAsString(eq("STORAGE_SQS_URL"))).thenReturn(storage_sqs_url);
                                                                                                                when(mock.getParameterAsString("INDEXER_DEADLETTER_QUEUE_SQS_URL")).thenReturn(deadletter_queue_sqs_url);
                                                                                                            })) {

            try (MockedConstruction<AmazonSQSConfig> config = Mockito.mockConstruction(AmazonSQSConfig.class, (mock1, context) -> {
                                                                                                                when(mock1.AmazonSQS()).thenReturn(sqsClient);
                                                                                                            })) {

                builder.init();

                Gson realGson = new Gson();

                DpsHeaders headers = new DpsHeaders();

                Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
                messageAttributes.put(DpsHeaders.ACCOUNT_ID, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                        .build());
                messageAttributes.put(DpsHeaders.DATA_PARTITION_ID, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getPartitionIdWithFallbackToAccountId())
                        .build());
                headers.addCorrelationIdIfMissing();
                messageAttributes.put(DpsHeaders.CORRELATION_ID, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getCorrelationId())
                        .build());
                messageAttributes.put(DpsHeaders.USER_EMAIL, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getUserEmail())
                        .build());
                messageAttributes.put(DpsHeaders.AUTHORIZATION, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(headers.getAuthorization())
                        .build());
                messageAttributes.put(retryString, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(String.valueOf(1))
                        .build());

                RecordChangedMessages message = realGson.fromJson(payload_retry, RecordChangedMessages.class);

                SendMessageRequest sendMessageRequest = SendMessageRequest.builder().queueUrl(deadletter_queue_sqs_url).messageBody(message.getData()).build();

                builder.createWorkerTask(payload_retry, headers);

                builder.createWorkerTask(payload_retry, countDownMillis, headers);

                Mockito.verify(sqsClient, times(2)).sendMessage(Mockito.eq(sendMessageRequest));
           

            }

        }

    }

}
