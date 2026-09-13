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

package org.opengroup.osdu.util;

import lombok.experimental.UtilityClass;
import org.opengroup.osdu.core.test.config.EnvLoader;

/**
 * Indexer-specific test configuration.
 *
 * <p>Service hosts and the data-partition header are now resolved by os-core-test
 * ({@code org.opengroup.osdu.core.test.service.ServicesConfig} via {@code HOST} and
 * {@code DATA_PARTITION_ID}). This class only retains configuration that has no os-core-test
 * equivalent: the Elasticsearch connection used for index verification, the tenant partition
 * ids used for test-data name generation, and the legal/entitlements values embedded in records.
 *
 * <p>All values are read through {@link EnvLoader}, so {@code .env} files, system properties and
 * environment variables resolve with the same precedence used by the rest of the library.
 */

@UtilityClass
public class Config {

    private static final int DEFAULT_PORT = 9243;
    private static final String DEFAULT_ELASTIC_SSL_ENABLED = "true";
    private static final String DEFAULT_SECURITY_HTTPS_CERTIFICATE_TRUST = "false";
    private static final String DEFAULT_ENTITLEMENTS_DOMAIN = "group";

    // region Elasticsearch (no os-core-test equivalent)

    public static int getPort() {
        return Integer.parseInt(get("ELASTIC_PORT", String.valueOf(DEFAULT_PORT)));
    }

    public static String getUserName() {
        return get("ELASTIC_USER_NAME", "");
    }

    public static String getPassword() {
        return get("ELASTIC_PASSWORD", "");
    }

    public static String getElasticHost() {
        return get("ELASTIC_HOST", "");
    }

    public static boolean isElasticSslEnabled() {
        return Boolean.parseBoolean(get("ELASTIC_SSL_ENABLED", DEFAULT_ELASTIC_SSL_ENABLED));
    }

    public static int getElastic8Port() {
        return Integer.parseInt(get("ELASTIC_8_PORT", String.valueOf(getPort())));
    }

    public static String getElastic8UserName() {
        return get("ELASTIC_8_USER_NAME", getUserName());
    }

    public static String getElastic8Password() {
        return get("ELASTIC_8_PASSWORD", getPassword());
    }

    public static String getElastic8Host() {
        return get("ELASTIC_8_HOST", getElasticHost());
    }

    public static boolean isElastic8SslEnabled() {
        return Boolean.parseBoolean(get("ELASTIC_8_SSL_ENABLED", String.valueOf(isElasticSslEnabled())));
    }

    public static boolean isSecurityHttpsCertificateTrust() {
        return Boolean.parseBoolean(
            get("SECURITY_HTTPS_CERTIFICATE_TRUST", DEFAULT_SECURITY_HTTPS_CERTIFICATE_TRUST));
    }

    // endregion

    // region Tenant partitions and record metadata


    /**
     * Entitlements domain used when building ACL group names. Falls back to {@code GROUP_ID} (and
     * then to {@code group}) to preserve the behaviour of the removed {@code AnthosConfig} helper.
     */
    public static String getEntitlementsDomain() {
        String domain = EnvLoader.get("ENTITLEMENTS_DOMAIN");
        if (domain != null && !domain.isEmpty()) {
            return domain;
        }
        return get("GROUP_ID", DEFAULT_ENTITLEMENTS_DOMAIN);
    }

    // endregion

    private static String get(String key, String defaultValue) {
        return EnvLoader.get(key, defaultValue);
    }
}
