/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.ibm.publish;


import com.google.common.base.Strings;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.indexer.RecordStatus;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.core.ibm.messagebus.IMessageFactory;
import org.opengroup.osdu.indexer.provider.interfaces.IPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

@Component
@RequestScope
public class PublisherImpl implements IPublisher {

	@Inject
	IMessageFactory mq;
	
    @Inject
    private JaxRsDpsLog logger;


    @Override
    public void publishStatusChangedTagsToTopic(DpsHeaders headers, JobStatus indexerBatchStatus) throws Exception {

        String tenant = headers.getPartitionId();
        if (Strings.isNullOrEmpty(tenant))
            tenant = headers.getAccountId();
        
        Map<String, String> message = new HashMap<>();
        message.put(tenant, headers.getPartitionIdWithFallbackToAccountId());
        headers.addCorrelationIdIfMissing();
        message.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
    
        RecordChangedMessages recordChangedMessages = getRecordChangedMessage(headers, indexerBatchStatus);
        message.put("data", recordChangedMessages.toString());

        try {
            logger.info("Indexer publishes message " + headers.getCorrelationId());
            mq.sendMessage(IMessageFactory.INDEXER_QUEUE_NAME, message.toString());
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private RecordChangedMessages getRecordChangedMessage(DpsHeaders headers, JobStatus indexerBatchStatus) {

        Gson gson = new GsonBuilder().create();
        Map<String, String> attributesMap = new HashMap<>();
        Type listType = new TypeToken<List<RecordStatus>>() {}.getType();

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
