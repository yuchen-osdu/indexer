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
import org.opengroup.osdu.azure.util.AzureServicePrincipleTokenService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.inject.Inject;

@Component
@RequestScope
public class ServiceAccountJwtClientImpl implements IServiceAccountJwtClient {
    private final String BEARER = "Bearer";

    @Inject
    private ITenantFactory tenantInfoServiceProvider;

    @Inject
    private DpsHeaders dpsHeaders;

    @Inject
    private JaxRsDpsLog log;

    @Autowired
    private AzureServicePrincipleTokenService tokenService;

    @Override
    public String getIdToken(String partitionId){

        TenantInfo tenant = this.tenantInfoServiceProvider.getTenantInfo(partitionId);
        if (tenant == null) {
            this.log.error("Invalid tenant name receiving from azure");
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid tenant Name", "Invalid tenant Name from azure");
        }

        this.dpsHeaders.put(DpsHeaders.USER_EMAIL, tenant.getServiceAccount());

        String token = this.tokenService.getAuthorizationToken();
        if(!Strings.isNullOrEmpty(token) && !token.startsWith(BEARER)) {
            token = BEARER + " " + token;
        }
        return token;
    }
}
