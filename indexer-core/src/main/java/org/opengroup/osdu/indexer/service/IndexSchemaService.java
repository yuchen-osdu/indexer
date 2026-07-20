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

package org.opengroup.osdu.indexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.indexer.IndexSchema;
import org.opengroup.osdu.core.common.model.indexer.OperationType;

public interface IndexSchemaService {

    IndexSchema getIndexerInputSchema(String kind, List<String> errors) throws AppException, UnsupportedEncodingException, URISyntaxException;

    IndexSchema getIndexerInputSchema(String kind, boolean invalidateCached) throws AppException, UnsupportedEncodingException, URISyntaxException;

    void processSchemaMessages(Map<String, OperationType> schemaMsgs) throws IOException;

    void processSchemaUpsert(String kind) throws AppException;

    void processSchemaUpsertEvent(ElasticsearchClient restClient, String kind) throws IOException, ElasticsearchException, URISyntaxException;

    void syncIndexMappingWithStorageSchema(String kind) throws ElasticsearchException, IOException, AppException, URISyntaxException;

    boolean isStorageSchemaSyncRequired(String kind, boolean forceClean) throws IOException;

    void invalidateSchemaCache(String kind);
}
