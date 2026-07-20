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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.AliasDefinition;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.PutAliasRequest;
import co.elastic.clients.elasticsearch.indices.PutAliasResponse;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.indexer.model.IndexAliasesResult;
import org.springframework.context.annotation.Lazy;

@RunWith(MockitoJUnitRunner.class)
public class IndexAliasServiceImplTest {
    @Mock
    private ElasticIndexNameResolver elasticIndexNameResolver;
    @Mock
    @Lazy
    private JaxRsDpsLog log;
    @Mock
    private org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient requestScopedClient;
    @InjectMocks
    private IndexAliasServiceImpl sut;

    private ElasticsearchClient restHighLevelClient;
    private ElasticsearchIndicesClient indicesClient;
    private GetAliasResponse getAliasesResponse, getAliasesNotFoundResponse;


    private static String kind = "common:welldb:wellbore:1.2.0";
    private static String index = "common-welldb-wellbore-1.2.0";
    private static String alias = "a1234567890";

    @Before
    public void setup() {
        initMocks(this);
        indicesClient = mock(ElasticsearchIndicesClient.class);
        restHighLevelClient = mock(ElasticsearchClient.class);
        getAliasesResponse = mock(GetAliasResponse.class);
        getAliasesNotFoundResponse = mock(GetAliasResponse.class);

    }

    @Test
    public void createIndexAlias_test_when_index_name_is_not_alias() throws IOException {
        PutAliasResponse putAliasResponse = PutAliasResponse.of(builder -> builder.acknowledged(true));
        when(elasticIndexNameResolver.getIndexNameFromKind(any())).thenReturn(index);
        when(elasticIndexNameResolver.getIndexAliasFromKind(any())).thenReturn(alias);
        when(elasticIndexNameResolver.isIndexAliasSupported(any())).thenReturn(true);
        when(restHighLevelClient.indices()).thenReturn(indicesClient);
        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenReturn(getAliasesNotFoundResponse);
        when(getAliasesNotFoundResponse.result()).thenReturn(Collections.emptyMap());
        when(indicesClient.putAlias(any(PutAliasRequest.class))).thenReturn(putAliasResponse);

        boolean ok = sut.createIndexAlias(restHighLevelClient, kind);
        Assert.assertTrue(ok);
    }

    @Test
    public void createIndexAlias_test_when_index_name_is_alias() throws IOException {
        Map<String, AliasDefinition> aliases = Map.of(index + "_123456789", AliasDefinition.of(builder -> builder));

        Map<String, IndexAliases> result = Map.of("some_alias", IndexAliases.of(builder -> builder.aliases(aliases)));

        GetAliasResponse getAliasResponse = GetAliasResponse.of(g -> g.result(result));

        // Mock UpdateAliasesResponse
        PutAliasResponse putAliasResponse = PutAliasResponse.of(builder -> builder.acknowledged(true));

        // Mock ElasticIndexNameResolver methods
        when(elasticIndexNameResolver.getIndexNameFromKind(any())).thenReturn(index);
        when(elasticIndexNameResolver.getIndexAliasFromKind(any())).thenReturn(alias);
        when(elasticIndexNameResolver.isIndexAliasSupported(any())).thenReturn(true);

        // Mock ElasticsearchClient methods
        when(restHighLevelClient.indices()).thenReturn(indicesClient);
        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenReturn(getAliasResponse);
        when(indicesClient.putAlias(any(PutAliasRequest.class))).thenReturn(putAliasResponse);

        // Call the method to test
        boolean ok = sut.createIndexAlias(restHighLevelClient, kind);

        // Assert the result
        Assert.assertTrue(ok);
    }

    @Test
    public void createIndexAlias_test_when_updateAliases_fails() throws IOException {
        PutAliasResponse putAliasResponse = PutAliasResponse.of(builder -> builder.acknowledged(false));
        when(elasticIndexNameResolver.getIndexNameFromKind(any())).thenReturn(index);
        when(elasticIndexNameResolver.getIndexAliasFromKind(any())).thenReturn(alias);
        when(elasticIndexNameResolver.isIndexAliasSupported(any())).thenReturn(true);
        when(restHighLevelClient.indices()).thenReturn(indicesClient);
        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenReturn(getAliasesNotFoundResponse);
        when(getAliasesNotFoundResponse.result()).thenReturn(Collections.emptyMap());
        when(indicesClient.putAlias(any(PutAliasRequest.class))).thenReturn(putAliasResponse);

        boolean ok = sut.createIndexAlias(restHighLevelClient, kind);
        Assert.assertFalse(ok);
    }

    @Test
    public void createIndexAlias_test_when_updateAliases_throws_exception() throws IOException {
        when(elasticIndexNameResolver.getIndexNameFromKind(any())).thenReturn(index);
        when(elasticIndexNameResolver.getIndexAliasFromKind(any())).thenReturn(alias);
        when(elasticIndexNameResolver.isIndexAliasSupported(any())).thenReturn(true);
        when(restHighLevelClient.indices()).thenReturn(indicesClient);
        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenReturn(getAliasesNotFoundResponse);
        when(getAliasesNotFoundResponse.result()).thenReturn(Collections.emptyMap());
        when(indicesClient.putAlias(any(PutAliasRequest.class))).thenThrow(IOException.class);

        boolean ok = sut.createIndexAlias(restHighLevelClient, kind);
        Assert.assertFalse(ok);
    }

    @Test
    public void createIndexAliasesForAll_test() throws IOException {
        String unsupportedKind = "common:welldb:wellbore:1";
        String unsupportedIndex = unsupportedKind.replace(":", "-");

        SearchResponse searchResponse = mock(SearchResponse.class);
        Aggregate aggregate = mock(Aggregate.class);
        StringTermsAggregate sterms = mock(StringTermsAggregate.class);

        StringTermsBucket termsBucket = StringTermsBucket.of(builder -> builder.key(kind).docCount(2));
        StringTermsBucket termsBucket2 = StringTermsBucket.of(builder -> builder.key(unsupportedKind).docCount(1));

        Buckets<StringTermsBucket> stringTermsBuckets = Buckets.of(
            builder -> builder.array(
                List.of(termsBucket, termsBucket, termsBucket2))
        );

        PutAliasResponse putAliasResponse = PutAliasResponse.of(builder -> builder.acknowledged(true));
        when(elasticIndexNameResolver.getIndexNameFromKind(any()))
            .thenAnswer(invocation -> {
                String argument = invocation.getArgument(0);
                return argument.replace(":", "-");
            });
        when(elasticIndexNameResolver.getIndexAliasFromKind(any())).thenReturn(alias);
        when(elasticIndexNameResolver.isIndexAliasSupported(any()))
            .thenAnswer(invocation -> {
                String argument = invocation.getArgument(0);
                return !unsupportedKind.equals(argument);
            });
        when(requestScopedClient.getClient()).thenReturn(restHighLevelClient);
        when(restHighLevelClient.indices()).thenReturn(indicesClient);
        when(restHighLevelClient.search(any(SearchRequest.class), any(Class.class))).thenReturn(searchResponse);
        when(searchResponse.aggregations()).thenReturn(Map.of("kinds", aggregate));
        when(aggregate.sterms()).thenReturn(sterms);
        when(sterms.buckets()).thenReturn(stringTermsBuckets);

        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenReturn(getAliasesNotFoundResponse);
        when(getAliasesNotFoundResponse.result()).thenReturn(Collections.emptyMap());
        when(indicesClient.putAlias(any(PutAliasRequest.class))).thenReturn(putAliasResponse);

        IndexAliasesResult result = sut.createIndexAliasesForAll();
        Assert.assertEquals(2, result.getIndicesWithAliases().size());
        Assert.assertEquals(index, result.getIndicesWithAliases().get(0));
        Assert.assertEquals(1, result.getIndicesWithoutAliases().size());
        Assert.assertEquals(unsupportedIndex, result.getIndicesWithoutAliases().get(0));
    }

    @Test
    public void createIndexAlias_test_when_updateAliases_partiallyFails() throws IOException {
        String unsupportedKind = "common:welldb:wellbore:1.*.*";
        String unsupportedIndex = unsupportedKind.replace(":", "-");


        when(elasticIndexNameResolver.getIndexNameFromKind(kind)).thenReturn(index);
        when(elasticIndexNameResolver.getIndexAliasFromKind(kind)).thenReturn(alias);

        when(elasticIndexNameResolver.getIndexNameFromKind(unsupportedKind)).thenReturn(unsupportedIndex);
        when(elasticIndexNameResolver.getIndexAliasFromKind(unsupportedKind)).thenReturn(alias);

        when(elasticIndexNameResolver.isIndexAliasSupported(any())).thenReturn(true);
        when(restHighLevelClient.indices()).thenReturn(indicesClient);
        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenReturn(getAliasesNotFoundResponse);
        when(getAliasesNotFoundResponse.result()).thenReturn(Collections.emptyMap());
        // at least one not ok response should lead to a return of false
        PutAliasResponse failResponse = PutAliasResponse.of(builder -> builder.acknowledged(false));
        PutAliasResponse okResponse = PutAliasResponse.of(builder -> builder.acknowledged(true));

        when(indicesClient.putAlias(any(PutAliasRequest.class))).thenReturn(failResponse, okResponse);

        boolean ok = sut.createIndexAlias(restHighLevelClient, kind);
        verify(log, never()).error(any(), any(Exception.class));
        Assert.assertFalse(ok);
    }
}
