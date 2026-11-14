/*
 * Copyright 2026, The Open Group
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
package org.opengroup.osdu.common;

import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.util.ElasticUtils;
import org.opengroup.osdu.util.HTTPClient;
import org.opengroup.osdu.util.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class ReindexRecordStepsTest {
    private static final String INDEX = "test-index";
    private static final String KIND = "test:source:entity:1.0.0";
    private static final List<String> RECORD_IDS = List.of("record-1", "record-2", "record-3", "record-4", "record-5");
    private final InMemoryElasticUtils elastic = new InMemoryElasticUtils();
    private final CapturingHttpClient http = new CapturingHttpClient();
    private RecordSteps steps;

    @BeforeEach
    void setUp() throws Exception {
        steps = new RecordSteps(http, elastic);
        Field field = RecordSteps.class.getDeclaredField("ingestedRecordIds");
        field.setAccessible(true);
        field.set(steps, new ArrayList<>(RECORD_IDS));
        elastic.documents.addAll(RECORD_IDS);
    }

    @Test
    void preparationRemovesDocumentsAndVerificationRequiresTheirIds() throws Throwable {
        steps.i_prepare_missing_documents_before_reindex(INDEX);
        assertEquals(Set.of("record-4", "record-5"), elastic.documents);

        elastic.documents.addAll(List.of("wrong-1", "wrong-2", "wrong-3"));
        assertThrows(AssertionError.class, () -> steps.i_should_verify_reindexed_documents_in_index(INDEX));

        elastic.documents.clear();
        elastic.documents.addAll(RECORD_IDS);
        steps.i_should_verify_reindexed_documents_in_index(INDEX);
    }

    @Test
    void successfulNoOpResponseDoesNotPassDocumentVerification() throws Throwable {
        steps.i_prepare_missing_documents_before_reindex(INDEX);
        steps.i_trigger_reindex_for_kind_with_cursor(KIND, "");
        steps.i_should_get_successful_reindex_response();

        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> steps.i_should_verify_reindexed_documents_in_index(INDEX));
        } finally {
            Thread.interrupted();
        }
        assertEquals(2, elastic.documents.size());
    }

    @Test
    void recordIdReindexRequestsExactlyTheRemovedIds() throws Throwable {
        steps.i_prepare_missing_documents_before_reindex(INDEX);
        steps.i_trigger_reindex_for_dynamic_record_ids();
        assertTrue(http.url.endsWith("reindex/records"));
        assertEquals(JsonParser.parseString("[\"record-1\",\"record-2\",\"record-3\"]"),
            JsonParser.parseString(http.payload).getAsJsonObject().get("recordIds"));

        elastic.documents.addAll(RECORD_IDS.subList(0, 3));
        steps.i_should_verify_reindexed_documents_in_index(INDEX);
    }

    @Test
    void forceCleanMustRemoveElasticsearchOnlyMarker() throws Throwable {
        steps.i_prepare_missing_documents_before_reindex(INDEX);
        steps.i_trigger_reindex_for_kind_with_force_clean(KIND);
        assertTrue(http.url.endsWith("reindex?force_clean=true"));
        assertEquals(3, elastic.documents.size());
        String marker = elastic.documents.stream().filter(id -> !RECORD_IDS.contains(id)).findFirst().orElseThrow();

        elastic.documents.addAll(RECORD_IDS);
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> steps.i_should_verify_reindexed_documents_in_index(INDEX));
        } finally {
            Thread.interrupted();
        }

        elastic.documents.remove(marker);
        steps.i_should_verify_reindexed_documents_in_index(INDEX);
    }

    @Test
    void preparationRejectsMissingIngestion() throws Exception {
        Field field = RecordSteps.class.getDeclaredField("ingestedRecordIds");
        field.setAccessible(true);
        field.set(steps, List.of());
        assertThrows(AssertionError.class, () -> steps.i_prepare_missing_documents_before_reindex(INDEX));
    }

    private static class CapturingHttpClient extends HTTPClient {
        private String url;
        private String payload;

        @Override
        public String getAccessToken() {
            return "";
        }

        @Override
        public HttpResponse send(String method, String url, String payload, Map<String, String> headers, String token) {
            this.url = url;
            this.payload = payload;
            return new HttpResponse(url.endsWith("reindex/records") ? 202 : 200, "", Map.of(), "application/json");
        }
    }

    private static class InMemoryElasticUtils extends ElasticUtils {
        private final Set<String> documents = new HashSet<>();

        @Override
        public long fetchRecords(String index) {
            return documents.size();
        }

        @Override
        public long fetchRecordsByFieldAndFieldValue(String index, String field, String value) {
            assertEquals("id", field);
            return documents.contains(value) ? 1 : 0;
        }

        @Override
        public DeleteResponse deleteRecordsById(String index, String id) {
            return DeleteResponse.of(builder -> builder.index(index).id(id).version(1)
                .seqNo(0L).primaryTerm(1L)
                .shards(shards -> shards.total(1).successful(1).failed(0))
                .result(documents.remove(id) ? Result.Deleted : Result.NotFound));
        }

        @Override
        public int indexRecords(String index, String kind, List<Map<String, Object>> records) {
            records.forEach(record -> documents.add((String) record.get("id")));
            return records.size();
        }

        @Override
        public void refreshIndex(String index) {
        }

        @Override
        public boolean waitForIndexGreen(String index, int timeoutSeconds) {
            return true;
        }
    }
}
