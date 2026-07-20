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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.http.FetchServiceHttpRequest;
import org.opengroup.osdu.core.common.http.UrlFetchServiceImpl;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.HttpResponse;
import org.opengroup.osdu.indexer.azure.config.RetryPolicyConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class UrlFetchServiceAzureImplTest {

    @Mock
    private JaxRsDpsLog logger;
    @Mock
    private UrlFetchServiceImpl urlFetchService;
    @InjectMocks
    private FetchServiceHttpRequest httpRequest;
    @InjectMocks
    private HttpResponse response;
    @Mock
    private RetryPolicy retryPolicy;
    @InjectMocks
    private UrlFetchServiceAzureImpl urlFetchServiceAzure;

    private static RetryPolicyConfig retryPolicyConfig;

    private static final String JSON1 = "{\n" +
            "    \"records\": [\n" +
            "        {\n" +
            "            \"data\": {\n" +
            "                \"Spuddate\": \"atspud\",\n" +
            "                \"UWI\": \"atuwi\",\n" +
            "                \"latitude\": \"latitude\",\n" +
            "                \"longitude\": \"longitude\"\n" +
            "            },\n" +
            "            \"meta\": null,\n" +
            "            \"id\": \"demo\",\n" +
            "            \"version\": demo,\n" +
            "            \"kind\": \"demo\",\n" +
            "            \"acl\": {\n" +
            "                \"viewers\": [\n" +
            "                    \"demo\"\n" +
            "                ],\n" +
            "                \"owners\": [\n" +
            "                    \"demo\"\n" +
            "                ]\n" +
            "            },\n" +
            "            \"legal\": {\n" +
            "                \"legaltags\": [\n" +
            "                    \"opendes-test-tag\"\n" +
            "                ],\n" +
            "                \"otherRelevantDataCountries\": [\n" +
            "                    \"BR\"\n" +
            "                ],\n" +
            "                \"status\": \"compliant\"\n" +
            "            },\n" +
            "            \"createUser\": \"demo\",\n" +
            "            \"createTime\": \"demo\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"notFound\": [\n" +
            "        \"demo\"\n" +
            "    ],\n" +
            "    \"conversionStatuses\": []\n" +
            "}";

    private static final String JSON2 = "{\n" +
            " \"records\" :[],\n" +
            " \"conversionStatuses\":[]\n" +
            "}";

    private static final String BATCH_API_URL = "https://demo/api/storage/v2/query/records:batch";
    private static final String STORAGE_API_URL = "https://demo/api/storage/v2/schemas";
    private static final String SCHEMA_API_URL = "https://demo/api/schema-service/v1/schema/osdu:file:gom:1.0.0";

    @BeforeClass
    public static void setup() {
        retryPolicyConfig = new RetryPolicyConfig();
        retryPolicyConfig.setINITIAL_DELAY(1000);
        retryPolicyConfig.setMAX_ATTEMPTS(3);
    }

    @Test
    public void shouldRetry_ForJSON1_when_storageQueryRecordCallIsMade() throws Exception {
        response.setBody(JSON1);
        httpRequest.setUrl(BATCH_API_URL);
        when(this.retryPolicy.retryConfig(any())).thenReturn(new RetryPolicy(this.logger, this.retryPolicyConfig).retryConfig(response -> this.retryPolicy.batchRetryPolicy(response)));
        when(urlFetchService.sendRequest(httpRequest)).thenReturn(response);

        urlFetchServiceAzure.sendRequest(httpRequest);

        verify(urlFetchService, atMost(4)).sendRequest(httpRequest);
    }

    @Test
    public void shouldRetry_ForJSON1_when_schemaRecordCallIsMade() throws Exception {
        response.setBody(JSON1);
        httpRequest.setUrl(SCHEMA_API_URL);
        when(this.retryPolicy.retryConfig(any())).thenReturn(new RetryPolicy(this.logger, this.retryPolicyConfig).retryConfig(response -> this.retryPolicy.schemaRetryPolicy(response)));
        when(urlFetchService.sendRequest(httpRequest)).thenReturn(response);

        urlFetchServiceAzure.sendRequest(httpRequest);

        verify(urlFetchService, atMost(4)).sendRequest(httpRequest);
    }

    @Test
    public void shouldRetry_when_anyOtherCallIsMade() throws Exception {
        response.setBody(JSON2);
        httpRequest.setUrl(STORAGE_API_URL);
        when(this.retryPolicy.retryConfig(any())).thenReturn(new RetryPolicy(this.logger, this.retryPolicyConfig).retryConfig(response -> this.retryPolicy.defaultRetryPolicy(response)));
        when(urlFetchService.sendRequest(httpRequest)).thenReturn(response);

        urlFetchServiceAzure.sendRequest(httpRequest);

        verify(urlFetchService, atMost(4)).sendRequest(httpRequest);
    }
}
