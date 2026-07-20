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
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class QueryUtilTest {
    @Test
    public void createQueryForParentConfigs() {
        String childKind = "osdu:wks:work-product-component--WellLog:1.";
        Query actualQuery = QueryUtil.createQueryForParentConfigs(childKind);
        Query expectedQuery = getExpectedQuery("parents_configuration_query");
        Assert.assertEquals(expectedQuery.toString(), actualQuery.toString());
    }

    @Test
    public void createQueryForChildrenConfigs() {
        String parentKind = "osdu:wks:master-data--Wellbore:1.";
        Query actualQuery = QueryUtil.createQueryForChildrenConfigs(parentKind);
        Query expectedQuery = getExpectedQuery("children_configuration_query");
        Assert.assertEquals(expectedQuery.toString(), actualQuery.toString());
    }

    @Test
    public void createQueryForConfigurations() {
        String kind = "osdu:wks:work-product-component--WellLog:1.";
        Query actualQuery = QueryUtil.createQueryForConfigurations(kind);
        Query expectedQuery = getExpectedQuery("configuration_query");
        Assert.assertEquals(expectedQuery.toString(), actualQuery.toString());
    }

    @Test
    public void createSimpleTextQuery() {
        String queryString = "id:(\"id1\" OR \"id2\")";
        Query actualQuery = QueryUtil.createSimpleTextQuery(queryString);
        Query expectedQuery = getExpectedQuery("simple_query");
        Assert.assertEquals(expectedQuery.toString(), actualQuery.toString());
    }

    @Test
    public void createIdsFilter_with_emptyIdList() {
        Assert.assertEquals("", QueryUtil.createIdsFilter(new ArrayList<String>()));
    }

    @Test
    public void createIdsFilter_with_idList() {
        Assert.assertEquals("\"id1\" OR \"id2\"", QueryUtil.createIdsFilter(List.of("id1", "id2:")));
    }

    @Test
    public void createSortOptionsList() throws Exception {
        List<SortOptions> sortOptionsList =
                QueryUtil.createSortOptionsList(List.of("id", "data.text.keyword"), List.of(SortOrder.Asc, SortOrder.Desc));
        Assert.assertEquals(2, sortOptionsList.size());
        Assert.assertEquals("id", sortOptionsList.get(0).field().field());
        Assert.assertEquals(SortOrder.Asc, sortOptionsList.get(0).field().order());
        Assert.assertEquals("data.text.keyword", sortOptionsList.get(1).field().field());
        Assert.assertEquals(SortOrder.Desc, sortOptionsList.get(1).field().order());
    }

    @Test
    public void createSortOptionsList_with_empty_fields() throws Exception {
        List<SortOptions> sortOptionsList =
                QueryUtil.createSortOptionsList(new ArrayList<>(), new ArrayList<>());
        Assert.assertNull(sortOptionsList);
    }

    @Test
    public void createSortOptionsList_with_null_fields() throws Exception {
        List<SortOptions> sortOptionsList =
                QueryUtil.createSortOptionsList(null, null);
        Assert.assertNull(sortOptionsList);
    }

    @Test
    public void createSortOptionsList_throws_exception_whenUnmatched_field_order_size() {
        Assert.assertThrows(Exception.class, () -> QueryUtil.createSortOptionsList(List.of("id", "data.text.keyword"), List.of(SortOrder.Asc)));
    }

    private Query getExpectedQuery(String queryFile) {
        InputStream inStream = this.getClass().getResourceAsStream("/queryutil/" + queryFile + ".json");
        BufferedReader br = new BufferedReader(new InputStreamReader(inStream));
        Query.Builder builder = new Query.Builder();
        return builder.withJson(br).build();
    }
}
