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

package org.opengroup.osdu.indexer.azure.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import lombok.Data;
import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.HttpResponse;
import org.opengroup.osdu.indexer.azure.config.RetryPolicyConfig;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * This class handles retry configuration logic for calls made to <prefix>/storage/v2/query/records:batch
 * to resolve intermittent CosmosDb Not found issue
 */

@Log
@Component
@Data
public class RetryPolicy {

    private JaxRsDpsLog logger;

    private RetryPolicyConfig retryPolicyConfig;

    private final String RECORD_NOT_FOUND = "notFound";

    public RetryPolicy(JaxRsDpsLog logger, RetryPolicyConfig retryPolicyConfig){
        this.logger = logger;
        this.retryPolicyConfig = retryPolicyConfig;
    }


    /**
     * @return RetryConfig with 3 attempts and 1 sec wait time
     */
    public RetryConfig retryConfig(Predicate<HttpResponse> predicate) {
        return RetryConfig.<HttpResponse>custom()
                .maxAttempts(retryPolicyConfig.getMAX_ATTEMPTS())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(retryPolicyConfig.getINITIAL_DELAY(), 2))
                .retryOnResult(predicate)
                .build();
    }

    /**
     * Unfound records get listed under a JsonArray "notFound" in the http json response
     *
     * @param response
     * @return if there are elements in "notFound" returns true, else false
     */
    public boolean batchRetryPolicy(HttpResponse response) {
        if (retryOnEmptyResponse(response)) return false;

        if (defaultResponseRetry(response)) return true;

        if (response.getResponseCode() == 429) {
            logger.debug("Storage batch API 429 retry");
            return true;
        }

        JsonObject jsonObject = new JsonParser().parse(response.getBody()).getAsJsonObject();
        JsonElement notFoundElement = (JsonArray) jsonObject.get(RECORD_NOT_FOUND);
        if (notFoundElement == null ||
                !notFoundElement.isJsonArray() ||
                notFoundElement.getAsJsonArray().size() == 0 ||
                notFoundElement.getAsJsonArray().isJsonNull()) {
            return false;
        }
        log.info("Storage batch API retry");
        return true;
    }

    public boolean schemaRetryPolicy(HttpResponse response) {
        if (retryOnEmptyResponse(response)) return false;

        if (defaultResponseRetry(response)) {
            log.info("Schema API retry");
            return true;
        }
        
        return false;
    }

    public boolean defaultRetryPolicy(HttpResponse response) {
        if (retryOnEmptyResponse(response)) return false;

        return defaultResponseRetry(response);
    }

    private boolean retryOnEmptyResponse(HttpResponse response) {
        return response == null || response.getBody().isEmpty();
    }

    private boolean defaultResponseRetry(HttpResponse response) {
        if (response.getResponseCode() <= 501) return false;

        log.info(String.format("Default retry, response code: %s", response.getResponseCode()));
        return true;
    }
}
