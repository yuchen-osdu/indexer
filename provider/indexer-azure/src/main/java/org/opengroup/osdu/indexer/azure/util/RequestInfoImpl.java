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

package org.opengroup.osdu.indexer.azure.util;

import com.google.common.base.Strings;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.search.DeploymentEnvironment;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.inject.Inject;
import java.util.Map;

import static org.opengroup.osdu.core.common.model.http.DpsHeaders.AUTHORIZATION;
import static org.opengroup.osdu.indexer.azure.util.DpsHeadersAzureQueue.INDEXER_QUEUE_KEY;


@Component
@RequestScope
public class RequestInfoImpl implements IRequestInfo {

    @Inject
    private DpsHeaders dpsHeaders;

    @Inject
    private IServiceAccountJwtClient serviceAccountJwtClient;

    @Inject
    private TenantInfo tenantInfo;

    @Value("${DEPLOYMENT_ENVIRONMENT}")
    private String DEPLOYMENT_ENVIRONMENT;


    @Override
    public DpsHeaders getHeaders() {

        return this.dpsHeaders;
    }

    @Override
    public String getPartitionId() {
        return this.dpsHeaders.getPartitionId();
    }

    @Override
    public Map<String, String> getHeadersMap() {
        return this.dpsHeaders.getHeaders();
    }

    @Override
    public Map<String, String> getHeadersMapWithDwdAuthZ() {
        return getHeadersWithDwdAuthZ().getHeaders();
    }

    @Override
    public DpsHeaders getHeadersWithDwdAuthZ() {
        this.dpsHeaders.put(AUTHORIZATION, this.checkOrGetAuthorizationHeader());
        return this.dpsHeaders;
    }

    @Override
    public boolean isCronRequest() { return false;}

    @Override
    public boolean isTaskQueueRequest() {
        if (!this.dpsHeaders.getHeaders().containsKey(INDEXER_QUEUE_KEY)) return false;

//        String queueId = this.headersInfo.getHeadersMap().get(AppEngineHeaders.TASK_QUEUE_NAME);
//        return queueId.endsWith(Constants.INDEXER_QUEUE_IDENTIFIER);
        return false;
    }

    public String checkOrGetAuthorizationHeader() {
        if (DeploymentEnvironment.valueOf(DEPLOYMENT_ENVIRONMENT) == DeploymentEnvironment.LOCAL) {
            String authHeader = this.dpsHeaders.getAuthorization();
            if (Strings.isNullOrEmpty(authHeader)) {
                throw new AppException(HttpStatus.SC_UNAUTHORIZED, "Invalid authorization header", "Authorization token cannot be empty");
            }
            return authHeader;
        } else {
            return this.serviceAccountJwtClient.getIdToken(tenantInfo.getName());
        }
    }
}
