/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.indexer.service;

import static java.util.Collections.singletonList;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.indexer.SchemaInfo;
import org.opengroup.osdu.core.common.model.indexer.SchemaOperationType;
import org.opengroup.osdu.indexer.config.SchemaEventsListenerConfiguration;
import org.opengroup.osdu.indexer.logging.AuditLogger;
import org.opengroup.osdu.indexer.util.ElasticClientHandler;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;
import org.springframework.stereotype.Component;

@Component
public class SchemaEventsProcessorImpl implements SchemaEventsProcessor {

    @Inject
    private SchemaEventsListenerConfiguration schemaEventsListenerConfiguration;
    @Inject
    private ElasticClientHandler elasticClientHandler;
    @Inject
    private IndexSchemaService indexSchemaService;
    @Inject
    private AuditLogger auditLogger;
    @Inject
    private RequestScopedElasticsearchClient requestScopedClient;

    @Override
    public void processSchemaMessages(List<SchemaInfo> schemaInfos) throws IOException {
        Map<String, SchemaOperationType> messages = new HashMap<>();

        if (schemaEventsListenerConfiguration.isListenCreateEvent()) {
            Map<String, SchemaOperationType> createSchemaMessages = SchemaInfo.getCreateSchemaEvents(schemaInfos);
            if (!createSchemaMessages.isEmpty()) {
                messages.putAll(createSchemaMessages);
            }
        }

        if (schemaEventsListenerConfiguration.isListenUpdateEvent()) {
            Map<String, SchemaOperationType> updateSchemaMessages = SchemaInfo.getUpdateSchemaEvents(schemaInfos);
            if (!updateSchemaMessages.isEmpty()) {
                messages.putAll(updateSchemaMessages);
            }
        }

        if (messages.isEmpty()) {
            return;
        }

        ElasticsearchClient restClient = this.requestScopedClient.getClient();
        messages.forEach((key, value) -> {
            try {
                this.indexSchemaService.processSchemaUpsertEvent(restClient, key);
                this.auditLogger.indexMappingUpsertSuccess(singletonList(key));
            } catch (IOException | ElasticsearchException | URISyntaxException e) {
                this.auditLogger.indexMappingUpsertFail(singletonList(key));
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "unable to process schema upsert event",
                    e.getMessage(), e);
            }
        });

    }
}
