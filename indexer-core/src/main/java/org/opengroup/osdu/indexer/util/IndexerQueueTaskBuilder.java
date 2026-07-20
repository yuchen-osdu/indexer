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

package org.opengroup.osdu.indexer.util;

import com.google.api.client.http.HttpMethods;
import com.google.gson.Gson;
import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.http.FetchServiceHttpRequest;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.search.CloudTaskRequest;
import org.opengroup.osdu.core.common.model.http.HttpResponse;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.http.IUrlFetchService;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.net.URISyntaxException;
import java.util.Map;
import jakarta.inject.Inject;

import static org.opengroup.osdu.core.common.Constants.REINDEX_RELATIVE_URL;
import static org.opengroup.osdu.core.common.Constants.WORKER_RELATIVE_URL;

    @Log
    @Component
    @RequestScope
    public class IndexerQueueTaskBuilder {

    @Inject
    private IUrlFetchService urlFetchService;
    @Inject
    private JaxRsDpsLog jaxRsDpsLog;
    @Inject
    private IndexerConfigurationProperties configurationProperties;

    public void createWorkerTask(String payload, DpsHeaders headers) {
        createTask(WORKER_RELATIVE_URL, payload, 0l, headers);
    }

    public void createWorkerTask(String payload, Long countdownMillis, DpsHeaders headers) {
        createTask(WORKER_RELATIVE_URL, payload, countdownMillis, headers);
    }

    public void createReIndexTask(String payload, DpsHeaders headers) {
        createTask(REINDEX_RELATIVE_URL, payload, 0l, headers);
    }

    public void createReIndexTask(String payload, Long countdownMillis, DpsHeaders headers) {
        createTask(REINDEX_RELATIVE_URL, payload, countdownMillis, headers);
    }

    private void createTask(String url, String payload, Long countdownMillis, DpsHeaders headers) {
        CloudTaskRequest cloudTaskRequest = CloudTaskRequest.builder()
                .message(payload)
                .url(url)
                .initialDelayMillis(countdownMillis)
                .build();

        FetchServiceHttpRequest request = FetchServiceHttpRequest.builder()
                .httpMethod(HttpMethods.POST)
                .url(configurationProperties.getIndexerQueueHost())
                .body(new Gson().toJson(cloudTaskRequest))
                .headers(headers)
                .build();
        try {
            HttpResponse response = this.urlFetchService.sendRequest(request);
            this.jaxRsDpsLog.info(String.format("task enqueuing response: %s", response.getResponseCode()));
        } catch (URISyntaxException e) {
            this.jaxRsDpsLog.warning(String.format("error enqueuing task message: %s | url: %s | task payload: %s", e.getMessage(), url, payload));
        }
    }
}
