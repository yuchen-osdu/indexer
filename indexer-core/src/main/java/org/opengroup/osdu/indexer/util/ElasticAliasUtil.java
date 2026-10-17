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

package org.opengroup.osdu.indexer.util;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for managing Elasticsearch aliases and versioned indexes.
 * Provides support for the alias-based architecture where physical indexes
 * have revision suffixes (e.g., -r1, -r2) and logical names are aliases.
 */
@Component
public class ElasticAliasUtil {

    @Autowired
    private JaxRsDpsLog log;

    /**
     * Check if a given name is an alias.
     */
    public boolean isAlias(ElasticsearchClient client, String name) throws IOException {
        try {
            GetAliasRequest request = GetAliasRequest.of(builder ->
                builder.name(name));
            GetAliasResponse response = client.indices().getAlias(request);

            for (IndexAliases indexAliases : response.result().values()) {
                if (indexAliases.aliases().containsKey(name)) {
                    return true;
                }
            }
            return false;
        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_NOT_FOUND) {
                return false;
            }
            log.error(String.format("Error checking alias status for '%s': %s", name, e.getMessage()), e);
            throw e;
        }
    }

    /**
     * Resolve an alias to its physical index(es).
     */
    public List<String> resolveAliasToPhysicalIndexes(ElasticsearchClient client, String alias) throws IOException {
        List<String> physicalIndexes = new ArrayList<>();

        try {
            GetAliasRequest request = GetAliasRequest.of(builder ->
                builder.name(alias));
            GetAliasResponse response = client.indices().getAlias(request);

            for (Map.Entry<String, IndexAliases> entry : response.result().entrySet()) {
                physicalIndexes.add(entry.getKey());
            }
        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_NOT_FOUND) {
                return physicalIndexes;
            }
            log.error(String.format("Error resolving alias '%s' to physical indexes: %s", alias, e.getMessage()), e);
            throw e;
        }

        return physicalIndexes;
    }

    /**
     * Create an alias pointing to a physical index.
     */
    public boolean createAlias(ElasticsearchClient client, String aliasName, String physicalIndexName)
            throws IOException {
        if (aliasName == null || aliasName.isEmpty()) {
            log.warning("Cannot create alias: alias name is null or empty");
            return false;
        }
        if (physicalIndexName == null || physicalIndexName.isEmpty()) {
            log.warning("Cannot create alias: physical index name is null or empty");
            return false;
        }

        try {
            PutAliasRequest request = PutAliasRequest.of(builder ->
                builder.index(physicalIndexName)
                       .name(aliasName));

            PutAliasResponse response = client.indices().putAlias(request);

            if (response.acknowledged()) {
                log.info(String.format("Created alias %s pointing to %s", aliasName, physicalIndexName));
                return true;
            } else {
                log.warning(String.format("Failed to create alias %s", aliasName));
                return false;
            }
        } catch (ElasticsearchException e) {
            log.error(String.format("Error creating alias %s: %s", aliasName, e.getMessage()), e);
            throw e;
        }
    }

    /**
     * Get the physical index name for a new index creation.
     * Always returns versioned name with -r1 suffix for new indexes.
     */
    public String getPhysicalIndexNameForCreation(String indexName) {
        return indexName + "-r1";
    }
}
