/*
  Copyright 2020 Google LLC
  Copyright 2020 EPAM Systems, Inc

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.opengroup.osdu.indexer.web.util;

import static org.opengroup.osdu.core.common.model.http.DpsHeaders.AUTHORIZATION;

import java.util.Map;

import jakarta.inject.Inject;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.common.model.entitlements.AuthorizationResponse;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.search.DeploymentEnvironment;
import org.opengroup.osdu.core.common.model.search.SearchServiceRole;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.IAuthorizationService;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;

import lombok.extern.java.Log;


@Log
@Component
public class RequestInfoImpl implements IRequestInfo {

    @Inject
    private DpsHeaders dpsHeaders;

    @Inject
    private TokenProvider tokenProvider;

    @Inject
    private TenantInfo tenantInfo;

    @Inject
    private IndexerConfigurationProperties properties;

    @Inject
    private IAuthorizationService authorizationService;

    private static final String EXPECTED_CRON_HEADER_VALUE = "true";

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
        // Update DpsHeaders so that service account creds are passed down
        this.dpsHeaders.put(AUTHORIZATION, this.checkOrGetAuthorizationHeader());
        return this.dpsHeaders;
    }

    @Override
    // This function no longer used and should be removed from interface in future.
    public boolean isCronRequest() {
        return false;
    }

    @Override
    // This function no longer used and should be removed from interface in future.
    public boolean isTaskQueueRequest() {
        return false;
    }

    private boolean isCloudTaskRequest() {
        AuthorizationResponse authResponse = authorizationService.authorizeAny(dpsHeaders, SearchServiceRole.ADMIN);
        dpsHeaders.put(DpsHeaders.USER_EMAIL, authResponse.getUser());
        return true;
    }
   
    public String checkOrGetAuthorizationHeader() {
        if (properties.getDeploymentEnvironment() == DeploymentEnvironment.LOCAL) {
            String authHeader = this.dpsHeaders.getAuthorization();
            if (Strings.isNullOrEmpty(authHeader)) {
                throw new AppException(HttpStatus.SC_UNAUTHORIZED, "Invalid authorization header", "Authorization token cannot be empty");
            }
            String user = this.dpsHeaders.getUserEmail();
            if (Strings.isNullOrEmpty(user)) {
                throw new AppException(HttpStatus.SC_UNAUTHORIZED, "Invalid user header", "User header cannot be empty");
            }
            return authHeader;
        } else {
            return "Bearer " + this.tokenProvider.getIdToken();
        }
    }
}
