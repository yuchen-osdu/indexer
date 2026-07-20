// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.indexer.logging;

import com.google.common.base.Strings;
import org.opengroup.osdu.core.common.logging.audit.AuditAction;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload.AuditPayloadBuilder;
import org.opengroup.osdu.core.common.logging.audit.AuditStatus;

import java.util.List;

public class AuditEvents {

    private static final String UNKNOWN = "unknown";
    private static final String UNKNOWN_IP = "0.0.0.0";

    private static final String INDEX_CREATE_RECORD_ACTION_ID = "IN001";
    private static final String INDEX_CREATE_RECORDS_SUCCESS = "Successfully created record in index";
    private static final String INDEX_CREATE_RECORDS_PARTIAL_SUCCESS = "Partially successful in creating record in index";
    private static final String INDEX_CREATE_RECORDS_FAILURE = "Failed creating record in index";

    private static final String INDEX_UPDATE_RECORD_ACTION_ID = "IN002";
    private static final String INDEX_UPDATE_RECORDS_SUCCESS = "Successfully updated record in index";
    private static final String INDEX_UPDATE_RECORDS_PARTIAL_SUCCESS = "Partially successful in updating record in index";
    private static final String INDEX_UPDATE_RECORDS_FAILURE = "Failed updating record in index";

    private static final String INDEX_DELETE_RECORD_ACTION_ID = "IN003";
    private static final String INDEX_DELETE_RECORDS_SUCCESS = "Successfully deleted record in index";
    private static final String INDEX_DELETE_RECORDS_FAILURE = "Failed deleting record in index";

    private static final String INDEX_PURGE_RECORD_ACTION_ID = "IN004";

    private static final String INDEX_STARTED_ACTION_ID = "IN006";
    private static final String INDEX_STARTED_OPERATION = "Indexing started";

    private static final String REINDEX_KIND_ACTION_ID = "IN007";
    private static final String REINDEX_KIND_OPERATION = "Reindex kind";

    private static final String COPY_INDEX_ACTION_ID = "IN008";
    private static final String COPY_INDEX_OPERATION = "Copy index";

    private static final String GET_TASK_STATUS_ACTION_ID = "IN009";
    private static final String GET_TASK_STATUS_OPERATION = "Get task status";

    private static final String RUN_JOB_ACTION_ID = "IN010";
    private static final String RUN_JOB_MESSAGE_SUCCESS = "Index clean-up status job run success";

    private static final String INDEX_MAPPING_UPDATE_ACTION_ID = "IN0011";
    private static final String INDEX_MAPPING_UPDATE_SUCCESS = "Successfully upserted index mapping";
    private static final String INDEX_MAPPING_UPDATE_FAILURE = "Failed upserting index mapping";

    private static final String CONFIGURE_PARTITION_ACTION_ID = "IN0012";
    private static final String CONFIGURE_PARTITION_OPERATION = "Data partition cluster configuration update";

    private static final String INDEX_DELETE_ACTION_ID = "IN0013";
    private static final String INDEX_DELETE_SUCCESS = "Successfully deleted index";
    private static final String INDEX_DELETE_FAILURE = "Failed deleting index";

    private static final String REINDEX_RECORDS_ACTION_ID = "IN0014";
    private static final String REINDEX_RECORDS_OPERATION = "Reindex records";

    private final String user;
    private final String userIpAddress;
    private final String userAgent;
    private final String userAuthorizedGroupName;

    public AuditEvents(String user, String userIpAddress, String userAgent, String userAuthorizedGroupName) {
        this.user = Strings.isNullOrEmpty(user) ? UNKNOWN : user;
        this.userIpAddress = Strings.isNullOrEmpty(userIpAddress) ? UNKNOWN_IP : userIpAddress;
        this.userAgent = Strings.isNullOrEmpty(userAgent) ? UNKNOWN : userAgent;
        this.userAuthorizedGroupName = Strings.isNullOrEmpty(userAuthorizedGroupName) ? UNKNOWN : userAuthorizedGroupName;
    }

    private AuditPayloadBuilder createAuditPayloadBuilder(List<String> requiredGroupsForAction, AuditStatus status, String actionId) {
        return AuditPayload.builder()
                .status(status)
                .user(this.user)
                .actionId(actionId)
                .requiredGroupsForAction(requiredGroupsForAction)
                .userIpAddress(this.userIpAddress)
                .userAgent(this.userAgent)
                .userAuthorizedGroupName(this.userAuthorizedGroupName);
    }

    public AuditPayload getIndexCreateRecordSuccessEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_CREATE_RECORD.getRequiredGroups(), AuditStatus.SUCCESS, INDEX_CREATE_RECORD_ACTION_ID)
                .action(AuditAction.CREATE)
                .message(INDEX_CREATE_RECORDS_SUCCESS)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexCreateRecordPartialSuccessEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_CREATE_RECORD.getRequiredGroups(), AuditStatus.PARTIAL_SUCCESS, INDEX_CREATE_RECORD_ACTION_ID)
                .action(AuditAction.CREATE)
                .message(INDEX_CREATE_RECORDS_PARTIAL_SUCCESS)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexCreateRecordFailEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_CREATE_RECORD.getRequiredGroups(), AuditStatus.FAILURE, INDEX_CREATE_RECORD_ACTION_ID)
                .action(AuditAction.CREATE)
                .message(INDEX_CREATE_RECORDS_FAILURE)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexUpdateRecordSuccessEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_UPDATE_RECORD.getRequiredGroups(), AuditStatus.SUCCESS, INDEX_UPDATE_RECORD_ACTION_ID)
                .action(AuditAction.UPDATE)
                .message(INDEX_UPDATE_RECORDS_SUCCESS)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexUpdateRecordPartialSuccessEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_UPDATE_RECORD.getRequiredGroups(), AuditStatus.PARTIAL_SUCCESS, INDEX_UPDATE_RECORD_ACTION_ID)
                .action(AuditAction.UPDATE)
                .message(INDEX_UPDATE_RECORDS_PARTIAL_SUCCESS)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexUpdateRecordFailEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_UPDATE_RECORD.getRequiredGroups(), AuditStatus.FAILURE, INDEX_UPDATE_RECORD_ACTION_ID)
                .action(AuditAction.UPDATE)
                .message(INDEX_UPDATE_RECORDS_FAILURE)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexDeleteRecordSuccessEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_DELETE_RECORD.getRequiredGroups(), AuditStatus.SUCCESS, INDEX_DELETE_RECORD_ACTION_ID)
                .action(AuditAction.DELETE)
                .message(INDEX_DELETE_RECORDS_SUCCESS)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexDeleteRecordFailEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_DELETE_RECORD.getRequiredGroups(), AuditStatus.FAILURE, INDEX_DELETE_RECORD_ACTION_ID)
                .action(AuditAction.DELETE)
                .message(INDEX_DELETE_RECORDS_FAILURE)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexDeleteFailEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.DELETE_INDEX.getRequiredGroups(), AuditStatus.FAILURE, INDEX_DELETE_ACTION_ID)
                .action(AuditAction.DELETE)
                .message(INDEX_DELETE_FAILURE)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexDeleteSuccessEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.DELETE_INDEX.getRequiredGroups(), AuditStatus.SUCCESS, INDEX_DELETE_ACTION_ID)
                .action(AuditAction.DELETE)
                .message(INDEX_DELETE_SUCCESS)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexPurgeRecordSuccessEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_PURGE_RECORD.getRequiredGroups(), AuditStatus.SUCCESS, INDEX_PURGE_RECORD_ACTION_ID)
                .action(AuditAction.DELETE)
                .message(INDEX_DELETE_RECORDS_SUCCESS)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexPurgeRecordFailEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_PURGE_RECORD.getRequiredGroups(), AuditStatus.FAILURE, INDEX_PURGE_RECORD_ACTION_ID)
                .action(AuditAction.DELETE)
                .message(INDEX_DELETE_RECORDS_FAILURE)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_STARTED.getRequiredGroups(), AuditStatus.SUCCESS, INDEX_STARTED_ACTION_ID)
                .action(AuditAction.CREATE)
                .message(INDEX_STARTED_OPERATION)
                .resources(resources)
                .build();
    }

    public AuditPayload getReindexEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.REINDEX_KIND.getRequiredGroups(), AuditStatus.SUCCESS, REINDEX_KIND_ACTION_ID)
                .action(AuditAction.CREATE)
                .message(REINDEX_KIND_OPERATION)
                .resources(resources)
                .build();
    }

    public AuditPayload getReindexRecordsEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.REINDEX_RECORDS.getRequiredGroups(), AuditStatus.SUCCESS, REINDEX_RECORDS_ACTION_ID)
                .action(AuditAction.CREATE)
                .message(REINDEX_RECORDS_OPERATION)
                .resources(resources)
                .build();
    }

    public AuditPayload getCopyIndexEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.COPY_INDEX.getRequiredGroups(), AuditStatus.SUCCESS, COPY_INDEX_ACTION_ID)
                .action(AuditAction.CREATE)
                .message(COPY_INDEX_OPERATION)
                .resources(resources)
                .build();
    }

    public AuditPayload getTaskStatusEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.GET_TASK_STATUS.getRequiredGroups(), AuditStatus.SUCCESS, GET_TASK_STATUS_ACTION_ID)
                .action(AuditAction.READ)
                .message(GET_TASK_STATUS_OPERATION)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexCleanUpJobRunEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.INDEX_CLEANUP_JOB_RUN.getRequiredGroups(), AuditStatus.SUCCESS, RUN_JOB_ACTION_ID)
                .action(AuditAction.JOB_RUN)
                .message(RUN_JOB_MESSAGE_SUCCESS)
                .resources(resources)
                .build();
    }

    public AuditPayload getConfigurePartitionEvent(List<String> resources) {
        return createAuditPayloadBuilder(AuditOperation.CONFIGURE_PARTITION.getRequiredGroups(), AuditStatus.SUCCESS, CONFIGURE_PARTITION_ACTION_ID)
                .action(AuditAction.UPDATE)
                .message(CONFIGURE_PARTITION_OPERATION)
                .resources(resources)
                .build();
    }

    public AuditPayload getIndexMappingUpsertEvent(List<String> resources, boolean isSuccess) {
        if (isSuccess) {
            return createAuditPayloadBuilder(AuditOperation.INDEX_MAPPING_UPSERT.getRequiredGroups(), AuditStatus.SUCCESS, INDEX_MAPPING_UPDATE_ACTION_ID)
                    .action(AuditAction.UPDATE)
                    .message(INDEX_MAPPING_UPDATE_SUCCESS)
                    .resources(resources)
                    .build();
        } else {
            return createAuditPayloadBuilder(AuditOperation.INDEX_MAPPING_UPSERT.getRequiredGroups(), AuditStatus.FAILURE, INDEX_MAPPING_UPDATE_ACTION_ID)
                    .action(AuditAction.UPDATE)
                    .message(INDEX_MAPPING_UPDATE_FAILURE)
                    .resources(resources)
                    .build();
        }
    }
}
