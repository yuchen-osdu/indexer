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

package org.opengroup.osdu.util;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

import static org.opengroup.osdu.util.HTTPClient.indentatedResponseBody;

/**
 * Azure-specific HTTP client with retry logic for server errors.
 */
@Slf4j
@ToString
public class AzureHTTPClient extends HTTPClient {

    private static String token = null;
    private static final int MAX_RETRY = 3;
    private static final int RETRY_DELAY_MS = 5000;

    @Override
    public synchronized String getAccessToken() {
        if (token == null) {
            try {
                token = "Bearer " + JwtTokenUtil.getAccessToken();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return token;
    }

    /**
     * Sends an HTTP request with retry logic for server errors.
     * Retries up to MAX_RETRY times if a 5xx response is received.
     */
    @Override
    public HttpResponse send(String httpMethod, String url, String payLoad, Map<String, String> headers, String token) {
        System.out.println("in Azure send method");
        log.info("waiting on response in azure send");

        HttpResponse response = null;
        int count = 1;

        while (count <= MAX_RETRY) {
            try {
                // Ensure correlation-id is set
                if (headers != null) {
                    headers.put("correlation-id", headers.getOrDefault("correlation-id", UUID.randomUUID().toString()));
                }

                log.info(String.format("Request method: %s\nHeaders: %s\nRequest Body: %s",
                        httpMethod, headers, indentatedResponseBody(payLoad)));
                log.info(String.format("Attempt: #%s/%s, CorrelationId: %s",
                        count, MAX_RETRY, headers != null ? headers.get("correlation-id") : "N/A"));

                // Use parent class to make the actual request
                response = super.send(httpMethod, url, payLoad, headers, token);

                // Check for server errors and retry
                if (response.getStatusInfo().getFamily() == HttpResponse.StatusFamily.SERVER_ERROR) {
                    log.warn(String.format("Server error (status %d), retrying...", response.getStatus()));
                    count++;
                    Thread.sleep(RETRY_DELAY_MS);
                    continue;
                } else {
                    break;
                }
            } catch (AssertionError ae) {
                // Re-throw assertion errors from parent
                log.error("Exception While Making Request: ", ae);
                count++;
                if (count > MAX_RETRY) {
                    throw ae;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception ex) {
                log.error("Exception While Making Request: ", ex);
                count++;
                if (count > MAX_RETRY) {
                    throw new AssertionError("Error: Send request error", ex);
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                if (response != null) {
                    log.info(String.format("This is the response received: %s\nHeaders: %s\nStatus code: %s",
                            response, response.getHeaders(), response.getStatus()));
                }
            }
        }
        return response;
    }
}
