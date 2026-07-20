//  Copyright © Schlumberger
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

package org.opengroup.osdu.indexer.azure.di;

import org.opengroup.osdu.azure.di.RedisAzureConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized Redis configuration using @Value annotations.
 * This provides a single source of truth for all Redis-related settings.
 */
@Component
public class RedisConfig {

    @Value("${redis.port:6380}")
    private int port;

    @Value("${redis.database}")
    private int database;

    @Value("${redis.connection.timeout:15}")
    private int connectionTimeout;

    @Value("${redis.command.timeout:5}")
    private int commandTimeout;

    @Value("${redis.principal.id:#{null}}")
    private String principalId;

    @Value("${redis.hostname:#{null}}")
    private String hostname;

    @Value("${redis.index.ttl:3600}")
    private int indexRedisTtl;

    // Azure service account id_token can be requested only for 1 hr
    @Value("${redis.jwt.ttl:3540}")
    private int jwtTtl;

    @Value("${redis.schema.ttl:3600}")
    private int schemaTtl;

    @Value("${redis.records.ttl:120}")
    private int recordsTtl;

    @Value("${redis.record.change.info.ttl:3600}")
    private int recordChangeInfoTtl;

    public int getIndexRedisTtl() {
        return indexRedisTtl;
    }

    public int getJwtTtl() {
        return jwtTtl;
    }

    public int getSchemaTtl() {
        return schemaTtl;
    }

    public int getRecordsTtl() {
        return recordsTtl;
    }

    public int getRecordChangeInfoTtl() {
        return recordChangeInfoTtl;
    }

    /**
     * Creates a RedisAzureConfiguration with the specified TTL.
     *
     * @param ttl The time-to-live value in seconds
     * @return A configured RedisAzureConfiguration instance
     */
    public RedisAzureConfiguration createConfiguration(int ttl) {
        return new RedisAzureConfiguration(
            database,
            ttl,
            port,
            connectionTimeout,
            commandTimeout,
            principalId,
            hostname);
    }
}
