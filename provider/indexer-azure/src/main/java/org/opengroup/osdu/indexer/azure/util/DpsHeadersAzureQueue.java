//  Copyright © Microsoft Corporation
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package org.opengroup.osdu.indexer.azure.util;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.inject.Inject;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
@Primary
public class DpsHeadersAzureQueue extends DpsHeaders {

    //ToDo this should be moved to Azure client-lib
    public static final String INDEXER_QUEUE_KEY = "x-functions-key";

    @Inject
    public DpsHeadersAzureQueue(HttpServletRequest request) {

        Map<String, String> headers = Collections
                .list(request.getHeaderNames())
                .stream()
                .collect(Collectors.toMap(h -> h, request::getHeader));

        // TODO: Figure out if this header is actually required to be set for anything
        headers.put(INDEXER_QUEUE_KEY, "NOT_USED");

        this.addFromMap(headers);

        // Add Correlation ID if missing
        this.addCorrelationIdIfMissing();
    }
}
