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

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryUtilEscapeTest {

    @Test
    void testEscapePhrase_normalInput() {
        assertEquals("tenant:test:record:1.0.0", QueryUtil.escapePhrase("tenant:test:record:1.0.0"));
    }

    @Test
    void testEscapePhrase_nullInput() {
        assertNull(QueryUtil.escapePhrase(null));
    }

    @Test
    void testEscapePhrase_neutralizesQuotesAndBackslashes() {
        assertEquals("id\\\" OR \\\"*:*", QueryUtil.escapePhrase("id\" OR \"*:*"));
        assertEquals("id\\\\part", QueryUtil.escapePhrase("id\\part"));
        assertEquals("id\\\\\\\"payload", QueryUtil.escapePhrase("id\\\"payload"));
    }

    @Test
    void testCreateIdsFilter_normalIds() {
        assertEquals("\"id1\" OR \"id2\"", QueryUtil.createIdsFilter(List.of("id1", "id2:")));
    }

    @Test
    void testCreateIdsFilter_maliciousIdsNeutralized() {
        List<String> maliciousIds = List.of(
                "malicious\" OR \"*:*",
                "path\\segment",
                "attack\\\" OR 1=1"
        );
        String filter = QueryUtil.createIdsFilter(maliciousIds);
        assertEquals(
                "\"malicious\\\" OR \\\"*:*\" OR \"path\\\\segment\" OR \"attack\\\\\\\" OR 1=1\"",
                filter
        );
    }

    @Test
    void testCreateQueryForConfigurations_escapesQuotesAndBackslashes() {
        String maliciousKind = "kind\" OR \"*:*";
        Query query = QueryUtil.createQueryForConfigurations(maliciousKind);
        String queryString = query.toString();
        assertTrue(queryString.contains("kind\\\\\\\" OR \\\\\\\"*:*"));
    }

    @Test
    void testCreateQueryForParentConfigs_escapesQuotesAndBackslashes() {
        String maliciousKind = "kind\" OR \"*:*";
        Query query = QueryUtil.createQueryForParentConfigs(maliciousKind);
        String queryString = query.toString();
        assertTrue(queryString.contains("kind\\\\\\\" OR \\\\\\\"*:*"));
    }

    @Test
    void testCreateQueryForChildrenConfigs_escapesQuotesAndBackslashes() {
        String maliciousKind = "kind\" OR \"*:*";
        Query query = QueryUtil.createQueryForChildrenConfigs(maliciousKind);
        String queryString = query.toString();
        assertTrue(queryString.contains("kind\\\\\\\" OR \\\\\\\"*:*"));
    }
}
