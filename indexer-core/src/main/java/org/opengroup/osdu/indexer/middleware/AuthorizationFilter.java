/*
 * Copyright 2017-2025, The Open Group
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

package org.opengroup.osdu.indexer.middleware;

import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.entitlements.AuthorizationResponse;
import org.opengroup.osdu.core.common.provider.interfaces.IAuthorizationService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import jakarta.inject.Inject;

@Log
@Component("authorizationFilter")
@RequestScope
public class AuthorizationFilter {

    @Inject
    private IAuthorizationService authorizationService;

    private DpsHeaders headers;

    @Inject
    AuthorizationFilter(DpsHeaders headers) {
        this.headers = headers;
    }

    public boolean hasPermission(String... requiredRoles) {
        AuthorizationResponse authResponse = authorizationService.authorizeAny(headers, requiredRoles);
        headers.put(DpsHeaders.USER_EMAIL, authResponse.getUser());
        headers.put(DpsHeaders.USER_AUTHORIZED_GROUP_NAME, authResponse.getUserAuthorizedGroupName());
        return true;
    }
}
