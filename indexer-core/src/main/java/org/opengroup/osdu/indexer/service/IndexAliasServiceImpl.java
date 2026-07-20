/*
 * Copyright © Schlumberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.indexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.PutAliasRequest;
import co.elastic.clients.elasticsearch.indices.PutAliasResponse;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import com.google.api.client.util.Strings;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.indexer.model.IndexAliasesResult;
import org.opengroup.osdu.indexer.util.ElasticClientHandler;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;
import org.springframework.stereotype.Component;

@Component
public class IndexAliasServiceImpl implements IndexAliasService{
    private static final String KIND_COMPLETE_VERSION_PATTERN = "[\\w-\\.\\*]+:[\\w-\\.\\*]+:[\\w-\\.\\*]+:(\\d+\\.\\d+\\.\\d+)$";

    @Inject
    private ElasticIndexNameResolver elasticIndexNameResolver;
    @Inject
    private ElasticClientHandler elasticClientHandler;
    @Inject
    private JaxRsDpsLog jaxRsDpsLog;
    @Inject
    private RequestScopedElasticsearchClient requestScopedClient;

    @Override
    public IndexAliasesResult createIndexAliasesForAll() {
        IndexAliasesResult result = new IndexAliasesResult();
        try{
            ElasticsearchClient restClient = this.requestScopedClient.getClient();
            List<String> allKinds = getAllKinds(restClient);
            Set<String> allExistingAliases = getAllExistingAliases(restClient);
            for (String kind : allKinds) {
                String alias = elasticIndexNameResolver.getIndexAliasFromKind(kind);
                String indexName = elasticIndexNameResolver.getIndexNameFromKind(kind);
                if(allExistingAliases.contains(alias)) {
                    result.getIndicesWithAliases().add(indexName);
                }
                else {
                    if(createIndexAlias(restClient, kind)) {
                        result.getIndicesWithAliases().add(indexName);
                    }
                    else {
                        result.getIndicesWithoutAliases().add(indexName);
                    }
                }
            }
        }
        catch (Exception e) {
            jaxRsDpsLog.error("elastic search request failed", e);
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "elastic search cannot respond", "an unknown error has occurred.", e);
        }

        return result;
    }

    @Override
    public boolean createIndexAlias(ElasticsearchClient restClient, String kind) {
        if(!elasticIndexNameResolver.isIndexAliasSupported(kind)) {
            return false;
        }

        try {
            // To create an alias for an index, the index name must the concrete index name, not alias
            String actualIndexName = resolveConcreteIndexName(restClient, kind);
            if(Strings.isNullOrEmpty(actualIndexName))
                return false;

            Map<String, String> indexAliasMap = new HashMap<>();
            indexAliasMap.put(actualIndexName, elasticIndexNameResolver.getIndexAliasFromKind(kind));
            String kindWithMajorVersion = getKindWithMajorVersion(kind);
            if(elasticIndexNameResolver.isIndexAliasSupported(kindWithMajorVersion)) {
                String index = elasticIndexNameResolver.getIndexNameFromKind(kindWithMajorVersion);
                String alias = elasticIndexNameResolver.getIndexAliasFromKind(kindWithMajorVersion);
                indexAliasMap.put(index, alias);
            }

            boolean ok = true;
            for (Map.Entry<String, String> entry: indexAliasMap.entrySet()) {
                PutAliasRequest.Builder putAliasRequest = new PutAliasRequest.Builder();
                putAliasRequest.index(entry.getKey());
                putAliasRequest.name(entry.getValue());
                PutAliasResponse putAliasResponse = restClient.indices().putAlias(putAliasRequest.build());
                // Bitwise AND assignment is used to return ok status only if previous operations are ok
                ok &= putAliasResponse.acknowledged();
            }
            return ok;
        }
        catch(Exception e) {
            jaxRsDpsLog.error(String.format("Fail to create index alias for kind '%s'", kind), e);
        }

        return false;
    }

    private Set<String> getAllExistingAliases(ElasticsearchClient elasticsearchClient) throws IOException {
        GetAliasRequest request = new GetAliasRequest.Builder().build();
        GetAliasResponse response = elasticsearchClient.indices().getAlias(request);

        Set<String> allAliases = new HashSet<>();
        response.result().values().forEach(aliasMap ->
            allAliases.addAll(aliasMap.aliases().keySet())
        );

        return allAliases;
    }

    private String getKindWithMajorVersion(String kind) {
        // If kind is common:welldb:wellbore:1.2.0, then kind with major version is common:welldb:wellbore:1.*.*
        int idx = kind.lastIndexOf(":");
        String version = kind.substring(idx+1);
        if(version.indexOf(".") > 0) {
            String kindWithoutVersion = kind.substring(0, idx);
            String majorVersion = version.substring(0, version.indexOf("."));
            return String.format("%s:%s.*.*", kindWithoutVersion, majorVersion);
        }
        return null;
    }

    private String resolveConcreteIndexName(ElasticsearchClient client, String kind) throws IOException {
        String index = elasticIndexNameResolver.getIndexNameFromKind(kind);
        if (!isCompleteVersionKind(kind)) {
            return index;
        }
        GetAliasRequest request = new GetAliasRequest.Builder().name(index).build();

        try {
            GetAliasResponse response = client.indices().getAlias(request);
            if(response.result().isEmpty()){
                /* index resolved from kind is actual concrete index
                 * Example:
                 * {
                 *   "opendes-wke-well-1.0.7": {
                 *       "aliases": {}
                 *   }
                 * }
                 */
                return index;
            }
            /* index resolved from kind is NOT actual create index. It is just an alias
             * The concrete index name in this example is "opendes-osdudemo-wellbore-1.0.0_1649167113090"
             * Example:
             * {
             *   "opendes-osdudemo-wellbore-1.0.0_1649167113090": {
             *       "aliases": {
             *           "opendes-osdudemo-wellbore-1.0.0": {}
             *       }
             *    }
             * }
             */
            for (Map.Entry<String, IndexAliases> entry: response.result().entrySet()){
                String actualIndex = entry.getKey();
                List<String> aliaseNames = entry.getValue().aliases().keySet().stream().toList();
                if(aliaseNames.contains(index)){
                    return actualIndex;
                }
            }
            return index;
        }catch (ElasticsearchException e){
            if(e.status() == HttpStatus.SC_NOT_FOUND){
                jaxRsDpsLog.debug("Alias not found.", e.getMessage());
            }else {
                jaxRsDpsLog.error("Unexpected error response from Elasticsearch.", e);
            }
            return index;
        }
    }

    private boolean isCompleteVersionKind(String kind) {
        return !Strings.isNullOrEmpty(kind) && kind.matches(KIND_COMPLETE_VERSION_PATTERN);
    }

    private List<String> getAllKinds(ElasticsearchClient client) throws IOException {
        // Create a TermsAggregation
        TermsAggregation termsAggregation = TermsAggregation.of(t -> t.field("kind").size(10000));

        // Create a SearchRequest with the aggregation
        SearchRequest searchRequest = SearchRequest.of(s -> s
            .index("_all")
            .aggregations("kinds", a -> a.terms(termsAggregation))
        );

        // Execute the search request
        SearchResponse<Void> searchResponse = client.search(searchRequest, Void.class);
        // Extract the aggregation result
        StringTermsAggregate kindsAggregation = searchResponse.aggregations().get("kinds").sterms();

        return kindsAggregation.buckets().array().stream()
            .map(bucket -> bucket.key().stringValue())
            .toList();
    }
}
