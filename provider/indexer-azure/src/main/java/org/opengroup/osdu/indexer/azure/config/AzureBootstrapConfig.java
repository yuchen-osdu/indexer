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

package org.opengroup.osdu.indexer.azure.config;

import com.azure.security.keyvault.secrets.SecretClient;
import org.opengroup.osdu.azure.KeyVaultFacade;
import org.opengroup.osdu.core.common.entitlements.EntitlementsAPIConfig;
import org.opengroup.osdu.core.common.entitlements.EntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.http.json.HttpResponseBodyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import jakarta.inject.Named;

@Component
public class AzureBootstrapConfig {

    @Value("${azure.keyvault.url}")
    private String keyVaultURL;

    @Value("${azure.servicebus.topic-name}")
    private String serviceBusTopicName;

    @Value("${azure.servicebus.reindex.topic-name}")
    private String serviceBusReindexTopicName;

    @Value("${publish.to.azure.servicebus.topic.enabled}")
    private boolean shouldPublishToServiceBusTopic;

    @Value("${ELASTIC_CACHE_EXPIRATION}")
    private Integer elasticCacheExpiration;

    @Value("${MAX_CACHE_VALUE_SIZE}")
    private Integer maxCacheValueSize;

    @Bean
    @Named("KEY_VAULT_URL")
    public String getKeyVaultURL() {
        return keyVaultURL;
    }

    @Bean
    @Named("SERVICE_BUS_TOPIC")
    public String serviceBusTopicName() {
        return serviceBusTopicName;
    }

    @Bean
    @Named("SERVICE_BUS_REINDEX_TOPIC")
    public String serviceBusReindexTopicName() {
        return serviceBusReindexTopicName;
    }

    @Bean
    @Named("PUBLISH_TO_SERVICE_BUS_INDEXERSTATUS_TOPIC_ENABLED")
    public boolean shouldPublishToServiceBusTopic() { return shouldPublishToServiceBusTopic;}

    @Bean
    @Named("ELASTIC_CACHE_EXPIRATION")
    public Integer getElasticCacheExpiration() {
        return elasticCacheExpiration;
    }

    @Bean
    @Named("MAX_CACHE_VALUE_SIZE")
    public Integer getMaxCacheValueSize() {
        return maxCacheValueSize;
    }

    @Bean
    @Named("AUTH_CLIENT_ID")
    public String authClientID(final SecretClient sc) {
        return KeyVaultFacade.getSecretWithValidation(sc, "app-dev-sp-username");
    }

    @Bean
    @Named("AUTH_CLIENT_SECRET")
    public String authClientSecret(final SecretClient sc) {
        return KeyVaultFacade.getSecretWithValidation(sc, "app-dev-sp-password");
    }

    @Bean
    @Named("AUTH_URL")
    public String authURL(final SecretClient sc) {
        String urlFormat = "https://login.microsoftonline.com/%s/oauth2/token/";
        String tenant = KeyVaultFacade.getSecretWithValidation(sc, "app-dev-sp-tenant-id");
        return String.format(urlFormat, tenant);
    }

}
