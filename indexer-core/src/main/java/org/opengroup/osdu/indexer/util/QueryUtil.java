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

package org.opengroup.osdu.indexer.util;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.FieldType;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class QueryUtil {
    private static final String CONFIGURATIONS_PATH = "data.Configurations";
    private static final String CONFIGURATIONS_PATHS_PATH = "data.Configurations.Paths";
    private static final String RELATIONSHIP_DIRECTION_FIELD = "data.Configurations.Paths.RelatedObjectsSpec.RelationshipDirection";
    private static final String RELATED_OBJECT_KIND_FIELD = "data.Configurations.Paths.RelatedObjectsSpec.RelatedObjectKind";

    public static Query createQueryForParentConfigs(String childKind) {
        String queryString =
                String.format("%s:ParentToChildren AND %s:\"%s\"", RELATIONSHIP_DIRECTION_FIELD, RELATED_OBJECT_KIND_FIELD, childKind);
        return createQueryForRelatedConfig(queryString);
    }

    public static Query createQueryForChildrenConfigs(String parentKind) {
        String queryString =
                String.format("%s:ChildToParent AND %s:\"%s\"", RELATIONSHIP_DIRECTION_FIELD, RELATED_OBJECT_KIND_FIELD, parentKind);
        return createQueryForRelatedConfig(queryString);
    }

    public static Query createQueryForConfigurations(String kind) {
        Query singleQuery = createSimpleTextQuery(String.format("data.Code:\"%s\"", kind));
        String nestedQueryString = String.format("%s:\"%s\"", RELATED_OBJECT_KIND_FIELD, kind);
        Query nestedQuery = createQueryForRelatedConfig(nestedQueryString);

        BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
        boolQueryBuilder.boost(1.0F);
        boolQueryBuilder.should(singleQuery);
        boolQueryBuilder.should(nestedQuery);
        return new Query.Builder().bool(boolQueryBuilder.build()).build();
    }

    public static Query createSimpleTextQuery(String queryString) {
        QueryStringQuery.Builder queryStringQueryBuilder = getQueryStringQueryBuilder(queryString);
        Query.Builder queryBuilder = (Query.Builder) new Query.Builder().queryString(queryStringQueryBuilder.build());
        return queryBuilder.build();
    }

    public static String createIdsFilter(List<String> ids) {
        StringBuilder idsBuilder = new StringBuilder();
        for (String id : ids) {
            if (!idsBuilder.isEmpty()) {
                idsBuilder.append(" OR ");
            }
            idsBuilder.append("\"");
            idsBuilder.append(PropertyUtil.removeIdPostfix(id));
            idsBuilder.append("\"");
        }
        return idsBuilder.toString();
    }

    public static List<SortOptions> createSortOptionsList(List<String> fields, List<SortOrder> orders) throws Exception {
        if(fields == null || fields.isEmpty() || orders == null ||  orders.isEmpty()) {
            return null;
        }
        if(fields.size() != orders.size()) {
            throw new Exception("The size of the fields is not matched to the size of the orders");
        }

        List<SortOptions> sortOptionsList = new ArrayList<>();
        for(int i = 0; i < fields.size(); i++) {
            // If field is a text property under data block, make sure that the field is ended with ".keyword"
            // e.g. "data.Code.keyword instead of "data.Code"
            String field = fields.get(i);
            SortOrder order = orders.get(i);
            SortOptions sortOptions = new SortOptions.Builder()
                    .field(f ->f.field(field)
                                .order(order)
                                .missing("_last")
                                .unmappedType(FieldType.Keyword))
                    .build();
            sortOptionsList.add(sortOptions);
        }

        return sortOptionsList;
    }

    private static QueryStringQuery.Builder getQueryStringQueryBuilder(String queryString) {
        if(StringUtils.isBlank(queryString)) {
            queryString = "*";
        }
        return new QueryStringQuery.Builder()
                .query(queryString)
                .fields(new ArrayList<>())
                .type(TextQueryType.BestFields)
                .defaultOperator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.Or)
                .maxDeterminizedStates(10000)
                .allowLeadingWildcard(false)
                .enablePositionIncrements(true)
                .fuzziness("AUTO")
                .fuzzyPrefixLength(0)
                .fuzzyMaxExpansions(50)
                .phraseSlop(0.0)
                .escape(false)
                .autoGenerateSynonymsPhraseQuery(true)
                .fuzzyTranspositions(true)
                .boost(1.0f);
    }

    private static Query createQueryForRelatedConfig(String queryString) {
        Query simpleTextQuery = createSimpleTextQuery(queryString);
        Query.Builder innerNestedBuilder = (Query.Builder) new Query.Builder().nested(
                n ->n.path(CONFIGURATIONS_PATHS_PATH)
                        .query(simpleTextQuery)
                        .boost(1.0f)
                        .ignoreUnmapped(true)
                        .scoreMode(ChildScoreMode.Avg));
        Query.Builder nestedBuilder = (Query.Builder) new Query.Builder().nested(
                n ->n.path(CONFIGURATIONS_PATH)
                        .query(innerNestedBuilder.build())
                        .boost(1.0f)
                        .ignoreUnmapped(true)
                        .scoreMode(ChildScoreMode.Avg));
        return nestedBuilder.build();
    }
}
