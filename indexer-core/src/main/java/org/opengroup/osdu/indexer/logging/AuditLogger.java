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

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.opengroup.osdu.core.common.util.IpAddressUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;

@Component
@RequestScope
@RequiredArgsConstructor
public class AuditLogger {

    private final JaxRsDpsLog logger;
    private final DpsHeaders headers;
    //The presence of HttpServletRequest for audit events is not guaranteed if the events are processed outside the request scope
    private final ObjectProvider<HttpServletRequest> requestProvider;

    private AuditEvents events = null;

    private AuditEvents getAuditEvents() {
        if (this.events == null) {
            String user = headers.getUserEmail();
            String userAuthorizedGroupName = headers.getUserAuthorizedGroupName();
            HttpServletRequest request = requestProvider.getIfAvailable();
            if (request != null) {
                String userIpAddress = IpAddressUtil.getClientIpAddress(request);
                String userAgent = request.getHeader("User-Agent");
                this.events = new AuditEvents(user, userIpAddress, userAgent, userAuthorizedGroupName);
            } else {
                this.events = new AuditEvents(user, null, null, userAuthorizedGroupName);
            }
        }
        return this.events;
    }

    public void indexCreateRecordSuccess(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexCreateRecordSuccessEvent(resources));
    }

    public void indexCreateRecordPartialSuccess(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexCreateRecordPartialSuccessEvent(resources));
    }

    public void indexCreateRecordFail(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexCreateRecordFailEvent(resources));
    }

    public void indexUpdateRecordSuccess(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexUpdateRecordSuccessEvent(resources));
    }

    public void indexUpdateRecordPartialSuccess(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexUpdateRecordPartialSuccessEvent(resources));
    }

    public void indexUpdateRecordFail(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexUpdateRecordFailEvent(resources));
    }

    public void indexDeleteRecordSuccess(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexDeleteRecordSuccessEvent(resources));
    }

    public void indexDeleteRecordFail(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexDeleteRecordFailEvent(resources));
    }

    public void indexDeleteSuccess(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexDeleteSuccessEvent(resources));
    }

    public void indexDeleteFail(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexDeleteFailEvent(resources));
    }

    public void indexPurgeRecordSuccess(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexPurgeRecordSuccessEvent(resources));
    }

    public void indexPurgeRecordFail(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexPurgeRecordFailEvent(resources));
    }

    public void indexStarted(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexEvent(resources));
    }

    public void getReindex(List<String> resources) {
        this.writeLog(this.getAuditEvents().getReindexEvent(resources));
    }

    public void getReindexRecords(List<String> resources) {
        this.writeLog(this.getAuditEvents().getReindexRecordsEvent(resources));
    }

    public void copyIndex(List<String> resources) {
        this.writeLog(this.getAuditEvents().getCopyIndexEvent(resources));
    }

    public void getTaskStatus(List<String> resources) {
        this.writeLog(this.getAuditEvents().getTaskStatusEvent(resources));
    }

    public void getIndexCleanUpJobRun(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexCleanUpJobRunEvent(resources));
    }

    public void indexMappingUpsertSuccess(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexMappingUpsertEvent(resources, true));
    }

    public void indexMappingUpsertFail(List<String> resources) {
        this.writeLog(this.getAuditEvents().getIndexMappingUpsertEvent(resources, false));
    }

    public void getConfigurePartition(List<String> resources) {
        this.writeLog(this.getAuditEvents().getConfigurePartitionEvent(resources));
    }

    private void writeLog(AuditPayload log) {
        this.logger.audit(log);
    }
}
