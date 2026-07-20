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
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.json.JsonpUtils;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.core.common.search.Preconditions;
import org.opengroup.osdu.indexer.util.ElasticClientHandler;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

@Service
@RequestScope
public abstract class MappingServiceImpl implements IMappingService {

    private static final Time REQUEST_TIMEOUT = Time.of(builder -> builder.time("1m"));

    @Autowired
    private IndicesService indicesService;
    @Autowired
    private ElasticClientHandler elasticClientHandler;
    @Autowired
    private ElasticIndexNameResolver elasticIndexNameResolver;
    @Autowired
    private RequestScopedElasticsearchClient requestScopedClient;

    static {
        // default is 10k chars, which makes big mapping responses truncated
        JsonpUtils.maxToStringLength(Integer.MAX_VALUE);
    }
    /*
     * Get index schema
     *
     * @param index Index name
     * @param requestHeaders Incoming request headers
     * @throws Exception Throws exception if elastic cannot find index.
     * */
    @Override
    public String getIndexSchema(String index) throws Exception {
        ElasticsearchClient client = this.requestScopedClient.getClient();
        return this.getIndexMapping(client, index);
    }

    /**
     * Gets elastic mapping for index
     *
     * @param client Elasticsearch client
     * @param index  Index name
     * @return mapping Index mapping
     * @throws Exception Throws exception if elastic cannot find index.
     */
    public String getIndexMapping(ElasticsearchClient client, String index) throws Exception {

        Preconditions.checkArgument(client, Objects::nonNull, "client cannot be null");
        Preconditions.checkArgument(index, Objects::nonNull, "index cannot be null");

        // check if index exist
        boolean indexExist = indicesService.isIndexExist(client, index);
        if (!indexExist) {
            throw new AppException(HttpStatus.SC_NOT_FOUND, "Kind not found", String.format("Kind %s not found", this.elasticIndexNameResolver.getKindFromIndexName(index)));
        }

        try {
            GetMappingRequest.Builder getMappingBuilder = new GetMappingRequest.Builder();
            getMappingBuilder.index(index);
            getMappingBuilder.masterTimeout(REQUEST_TIMEOUT);
            GetMappingResponse mappingResponse = client.indices().getMapping(getMappingBuilder.build());
            Map<String, IndexMappingRecord> mappingRecordMap = mappingResponse.result();
            if (mappingRecordMap.isEmpty()) {
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unknown error", String.format("Error retrieving mapping for kind %s", this.elasticIndexNameResolver.getKindFromIndexName(index)));
            }
            Optional<IndexMappingRecord> mappingRecord = mappingRecordMap.values().stream().findFirst();
            StringBuilder collector = new StringBuilder();
            mappingRecord.ifPresent(indexMappingRecord -> {
                TypeMapping mappings = indexMappingRecord.mappings();
                JsonpUtils.toString(mappings, collector);
            });
            return collector.toString();
        } catch (IOException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unknown error", String.format("Error retrieving mapping for kind %s", this.elasticIndexNameResolver.getKindFromIndexName(index)), e);
        }
    }
}
