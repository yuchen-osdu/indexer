/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.indexer.indexing.scope;

import static org.springframework.context.annotation.ScopedProxyMode.TARGET_CLASS;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.indexer.indexing.config.ScopeModifierPostProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Primary
@Component
@Scope(value = ScopeModifierPostProcessor.SCOPE_THREAD, proxyMode = TARGET_CLASS)
@RequiredArgsConstructor
public class ThreadDpsHeaders extends DpsHeaders {

    private final TokenProvider tokenProvider;

    public void setThreadContext(Map<String, String> headers) {
        this.put(DpsHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getIdToken());
        this.addFromMap(headers);
    }
}
