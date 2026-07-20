/*
 * Copyright 2017-2025, Amazon.com, Inc. or its affiliates
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

package org.opengroup.osdu.indexer.aws.di;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.v2.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.v2.ssm.K8sParameterNotFoundException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AWSCacheConfigurationTest {

    private final String CREDENTIAL_KEY = "token";
    private final String CREDENTIAL_VALUE = "dummy_token";

    @Mock
    K8sLocalParameterProvider provider;
    @Test
    public void test_init_without_credentials() throws JsonProcessingException, K8sParameterNotFoundException {
        when(provider.getLocalMode()).thenReturn(true);
        when(provider.getParameterAsStringOrDefault("CACHE_CLUSTER_PORT", null)).thenReturn("12345");
        when(provider.getParameterAsStringOrDefault("CACHE_CLUSTER_ENDPOINT", null)).thenReturn("localhost");
        when(provider.getCredentialsAsMap("CACHE_CLUSTER_KEY")).thenReturn(null);

        AWSCacheConfiguration cacheConfig = new AWSCacheConfiguration(provider);
        assertEquals(3600, cacheConfig.getCacheExpireTimeInSeconds());
    }

    @Test
    public void test_init_with_credentials() throws JsonProcessingException, K8sParameterNotFoundException {
        when(provider.getLocalMode()).thenReturn(true);
        when(provider.getParameterAsStringOrDefault("CACHE_CLUSTER_PORT", null)).thenReturn("12345");
        when(provider.getParameterAsStringOrDefault("CACHE_CLUSTER_ENDPOINT", null)).thenReturn("localhost");
        Map<String, String> credentials = Map.of(CREDENTIAL_KEY, CREDENTIAL_VALUE);
        when(provider.getCredentialsAsMap("CACHE_CLUSTER_KEY")).thenReturn(credentials);

        AWSCacheConfiguration cacheConfig = new AWSCacheConfiguration(provider);
        assertEquals(3600, cacheConfig.getCacheExpireTimeInSeconds());
        assertEquals(CREDENTIAL_VALUE, cacheConfig.getCacheClusterKey());
    }
}
