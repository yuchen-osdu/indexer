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

package org.opengroup.osdu.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.opengroup.osdu.util.JsonPathMatcher.findArrayInJson;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.json.JsonpUtils;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.cucumber.datatable.DataTable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.indexer.RecordData;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.RecordAcl;
import org.opengroup.osdu.core.test.client.model.storage.RecordLegal;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.core.test.service.ServiceType;
import org.opengroup.osdu.core.test.util.polling.PollingClient;
import org.opengroup.osdu.core.test.util.polling.PollingResult;
import org.opengroup.osdu.core.test.util.TestFileUtil;
import org.opengroup.osdu.models.TestIndex;
import org.opengroup.osdu.models.TestIndexSetup;

@Getter
@Slf4j
public class RecordSteps extends TestsBase {

    private final Map<String, TestIndex> inputIndexMap = new HashMap<>();
    private boolean schemasInitialized = false;
    private final String timeStamp = String.valueOf(System.currentTimeMillis() + new Random().nextInt(10000));

    private String[] lastIngestedRecordIds;
    private final Map<String, String> resolvedCollaborationHeaders = new HashMap<>();

    public static final String DPS_HEADERS_COLLABORATION = "x-collaboration";

    public void the_schema_is_created_with_the_following_kind(DataTable dataTable) {
        List<TestIndexSetup> inputList = dataTable.asList(TestIndexSetup.class);
        for (TestIndexSetup input : inputList) {
            TestIndex testIndex = getTextIndex();
            testIndex.setIndex(generateActualName(input.getIndex(), timeStamp));
            testIndex.setKind(generateActualName(input.getKind(), timeStamp));
            testIndex.setSchemaFile(input.getSchemaFile());
            inputIndexMap.put(testIndex.getKind(), testIndex);
        }

        // One-time setup for the whole feature.
        if (!schemasInitialized) {
            inputIndexMap.values().forEach(TestIndex::setupSchema);
            schemasInitialized = true;
        }
    }

    public void i_ingest_records_with_the_for_a_given(String record, String dataGroup, String kind) {
        String actualKind = generateActualName(kind, timeStamp);
        try {
            StorageRecord[] templates = TestFileUtil.readTestDataFile(record + ".json", StorageRecord[].class);
            String createTime = java.time.Instant.now().toString();
            RecordLegal legal = toRecordLegal(generateLegalTag());
            RecordAcl acl = toRecordAcl(dataGroup);
            StorageRecord[] storageRecords = Arrays.stream(templates)
                .map(template -> new StorageRecord(
                    generateRecordId(template.id(), actualKind),
                    template.version(),
                    actualKind,
                    acl,
                    replaceValues(template.data(), timeStamp),
                    legal,
                    template.ancestry(),
                    template.tags(),
                    template.meta(),
                    template.modifyTime(),
                    template.modifyUser(),
                    createTime,
                    "TestUser"))
                .toArray(StorageRecord[]::new);
            log.info("Start ingesting records={}", new Gson().toJson(storageRecords));
            HttpResponse<CreateRecordsResponse> httpResponse = storageClient.putRecords(storageRecords);
            log.info("Response body: {}\nResponse Status code: {}", httpResponse.body(), httpResponse.statusCode());
            assertEquals(201, httpResponse.statusCode());
            lastIngestedRecordIds = httpResponse.body().recordIds();
        } catch (Exception ex) {
            throw new AssertionError(ex.getMessage(), ex);
        }
    }

    private String generateRecordId(String rawId, String kind) {
        return generateActualId(rawId, timeStamp, kind);
    }

    private static RecordLegal toRecordLegal(Legal legal) {
        return new RecordLegal(
            legal.getLegaltags().toArray(String[]::new),
            legal.getOtherRelevantDataCountries().toArray(String[]::new));
    }

    private RecordAcl toRecordAcl(String dataGroup) {
        String[] acl = {generateActualName(dataGroup, timeStamp) + "." + org.opengroup.osdu.util.Config.getEntitlementsDomain()};
        return new RecordAcl(acl, acl);
    }

    public void i_should_get_the_documents_for_the_in_the_Elastic_Search(int expectedCount, String index) throws Throwable {
        index = generateActualName(index, timeStamp);
        long numOfIndexedDocuments = getRecordsInIndex(index, expectedCount);
        assertEquals(expectedCount, numOfIndexedDocuments);
    }

    public void i_should_not_get_any_documents_for_the_index_in_the_Elastic_Search(String index) throws Throwable {
        index = generateActualName(index, timeStamp);
        getRecordsInIndex(index, 0);
    }

    public void i_should_get_the_elastic_for_the_tenant_testindex_timestamp_well_in_the_Elastic_Search(String expectedMapping, String kind, String index) throws Throwable {
        index = generateActualName(index, timeStamp);
        Map<String, IndexMappingRecord> elasticMapping = elasticClient.getMapping(index);
        assertNotNull(elasticMapping);

        String[] kindParts = kind.split(":");
        String authority = tenantMap.get(kindParts[0]);
        String source = kindParts[1];
        expectedMapping = expectedMapping.replace("<authority-id>", authority).replace("<source-id>", source);

        // ES may return the mapping under a physical index name that differs from the alias
        IndexMappingRecord typeMapping = elasticMapping.get(index);
        if (typeMapping == null && !elasticMapping.isEmpty()) {
            typeMapping = elasticMapping.values().iterator().next();
        }
        assertNotNull(typeMapping, "No mapping found for index: " + index + " (keys: " + elasticMapping.keySet() + ")");

        StringBuilder collector = new StringBuilder();
        TypeMapping mappings = typeMapping.mappings();
        JsonpUtils.toString(mappings, collector);
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> mapping = new Gson().fromJson(collector.toString(), type);

        assertNotNull(mapping);
        assertTrue(areJsonEqual(expectedMapping, mapping.toString()));
    }

    public void i_can_validate_indexed_attributes(String index, String kind) {
        String authority = tenantMap.get(kind.substring(0, kind.indexOf(":")));
        index = generateActualName(index, timeStamp);
        createIndex(index);
        List<Map<String, Object>> hits = elasticClient.fetchRecordsByAttribute(index, "authority", authority);

        assertFalse(hits.isEmpty());
        for (Map<String, Object> result : hits) {
            assertTrue(result.containsKey("authority"));
            assertEquals(authority, result.get("authority"));
            assertTrue(result.containsKey("source"));
            assertTrue(result.containsKey("createUser"));
            assertTrue(result.containsKey("createTime"));
        }
    }

    public void iShouldGetTheNumberDocumentsForTheIndexInTheElasticSearchWithOutSkippedAttribute(int expectedCount, String index, String skippedAttributes) throws Throwable {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedCount, idx,
            String.format("exist-query excluding %s", skippedAttributes),
            () -> { try { return elasticClient.fetchRecordsByExistQuery(idx, skippedAttributes); } catch (Exception e) { return -1L; } });
    }

    public void iShouldBeAbleToSearchRecordByTagKeyAndTagValue(String index, String tagKey, String tagValue, int expectedNumber) {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedNumber, idx,
            String.format("tag query %s=%s", tagKey, tagValue),
            () -> { try { return elasticClient.fetchRecordsByTags(idx, tagKey, tagValue); } catch (Exception e) { return -1L; } });
    }

    public void iShouldCleanupIndicesOfExtendedKinds(String extendedKinds) throws Throwable {
        String[] kinds = extendedKinds.split(",");
        for (String kind : kinds) {
            String actualKind = this.generateActualName(kind.trim(), timeStamp);
            TestIndex testIndex = this.getInputIndexMap().get(actualKind);
            testIndex.cleanupIndex(actualKind);
        }
    }

    public void iShouldBeAbleToSearchRecordByFieldAndFieldValue(String index, String fieldKey, String fieldValue, int expectedNumber) {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedNumber, idx,
            String.format("field query %s=%s", fieldKey, fieldValue),
            () -> { try { return elasticClient.fetchRecordsByFieldAndFieldValue(idx, fieldKey, fieldValue); } catch (Exception e) { return -1L; } });
    }

    public void i_should_get_the_documents_for_the_in_the_Elastic_Search_by_geoQuery(
            int expectedNumber, String index, Double topLatitude, Double topLongitude, Double bottomLatitude, Double bottomLongitude, String field) throws Throwable {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedNumber, idx,
            String.format("geo bounding-box query on %s", field),
            () -> { try { return elasticClient.fetchRecordsByGeoWithinQuery(idx, field, topLatitude, topLongitude, bottomLatitude, bottomLongitude); } catch (Exception e) { return -1L; } });
    }

    public void i_should_get_the_documents_for_the_in_the_Elastic_Search_by_AsIngestedCoordinates(
            int expectedNumber, String index, Double topPointX, Double bottomPointX, String pointX, Double topPointY, Double bottomPointY, String pointY) throws Throwable {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedNumber, idx,
            String.format("AsIngestedCoordinates bounding-box on (%s,%s)", pointX, pointY),
            () -> { try { return elasticClient.fetchRecordsByAsIngestedCoordinates(idx, pointX, topPointX, bottomPointX, pointY, topPointY, bottomPointY); } catch (Exception e) { return -1L; } });
    }

    public void i_should_get_the_documents_for_the_in_the_Elastic_Search_by_nestedQuery(
            int expectedNumber, String index, String path, String firstNestedField, String firstNestedValue, String secondNestedField, String secondNestedValue)
            throws Throwable {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedNumber, idx,
            String.format("nested query on %s (%s=%s, %s=%s)", path, firstNestedField, firstNestedValue, secondNestedField, secondNestedValue),
            () -> { try { return elasticClient.fetchRecordsByNestedQuery(idx, path, firstNestedField, firstNestedValue, secondNestedField, secondNestedValue); } catch (Exception e) { return -1L; } });
    }

    public void i_should_be_able_search_documents_for_the_by_flattened_inner_properties(int expectedCount, String index, String flattenedField,
                                                                                        String flattenedFieldValue) throws Throwable {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedCount, idx,
            String.format("flattened inner-property query %s=%s", flattenedField, flattenedFieldValue),
            () -> { try { return elasticClient.fetchRecordsWithFlattenedFieldsQuery(idx, flattenedField, flattenedFieldValue); } catch (Exception e) { return -1L; } });
    }

    public void i_should_get_object_in_search_response_without_hints_in_schema(String objectField, String index, String recordFile, String acl, String kind)
            throws Throwable {
        index = generateActualName(index, timeStamp);
        createIndex(index);

        StorageRecord[] fileRecords = TestFileUtil.readTestDataFile(recordFile + ".json", StorageRecord[].class);
        Map<String, Object> expectedData = fileRecords[0].data();

        RecordData actualRecordData = elasticClient.fetchDataFromObjectsArrayRecords(index);
        assertEquals(expectedData.get(objectField), actualRecordData.getData().get(objectField));
    }

    public void i_should_get_object_in_search_response(String innerField, String index) throws Throwable {
        index = generateActualName(index, timeStamp);
        createIndex(index);
        RecordData actualRecordData = elasticClient.fetchDataFromObjectsArrayRecords(index);
        assertTrue(actualRecordData.getData().containsKey(innerField));
    }

    public void i_should_get_string_array_in_search_response(String index, String field, String fieldValue, String arrayField, String desiredArrayValue)
            throws Throwable {
        index = generateActualName(index, timeStamp);
        createIndex(index);
        final List<Map<String, Object>> elasticRecordData = elasticClient.fetchRecordsByAttribute(index, field, fieldValue);
        assertEquals(1, elasticRecordData.size());
        final List<String> stringList = Arrays.asList(arrayField.split("\\."));
        final Map<String, Object> jsonRecord = elasticRecordData.get(0);
        Object arrayValue = findArrayInJson(jsonRecord, stringList);
        assertTrue(arrayValue instanceof List<?>, "Expected array at path: " + arrayField);
        String actualArrayValue = ((List<?>) arrayValue).stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        assertEquals(desiredArrayValue, actualArrayValue);
    }

    public void i_create_index_with_mapping_file_for_a_given_kind(String mappingFile, String index, String kind) throws Throwable {
        String actualKind = generateActualName(kind, timeStamp);
        TestIndex testIndex = getInputIndexMap().get(actualKind);
        testIndex.setMappingFile(mappingFile);
        this.getInputIndexMap().put(actualKind, testIndex);
        testIndex.addIndex();
    }

    public void i_ingest_records_with_xcollab_value_included_with_the_with_for_a_given(String xCollab,
                                                                                       String record,
                                                                                       String dataGroup,
                                                                                       String kind) {
        String actualKind = generateActualName(kind, timeStamp);
        xCollab = resolveCollaborationHeader(xCollab);
        try {
            StorageRecord[] templates = TestFileUtil.readTestDataFile(record + ".json", StorageRecord[].class);
            String createTime = java.time.Instant.now().toString();
            RecordLegal legal = toRecordLegal(generateLegalTag());
            RecordAcl acl = toRecordAcl(dataGroup);
            StorageRecord[] storageRecords = Arrays.stream(templates)
                .map(template -> new StorageRecord(
                    generateRecordId(template.id(), actualKind),
                    template.version(),
                    actualKind,
                    acl,
                    replaceValues(template.data(), timeStamp),
                    legal,
                    template.ancestry(),
                    template.tags(),
                    template.meta(),
                    template.modifyTime(),
                    template.modifyUser(),
                    createTime,
                    "TestUser"))
                .toArray(StorageRecord[]::new);
            log.info("Start ingesting records with x-collab={}", new Gson().toJson(storageRecords));

            HttpResponse<CreateRecordsResponse> httpResponse =
                storageClient.putRecords(storageRecords, Map.of(DPS_HEADERS_COLLABORATION, xCollab));
            log.info("Response body: {}\nResponse Status code: {}", httpResponse.body(), httpResponse.statusCode());
            assertEquals(201, httpResponse.statusCode());

            lastIngestedRecordIds = httpResponse.body().recordIds();
            assertNotNull(lastIngestedRecordIds);
            assertTrue(lastIngestedRecordIds.length > 0);
            Arrays.stream(lastIngestedRecordIds).forEach(id -> log.info("Ingested record with x-collab: {}", id));

        } catch (Exception ex) {
            throw new AssertionError(ex.getMessage(), ex);
        }
    }

    protected void i_should_get_the_documents_with_xcollab_value_included_for_the_in_the_Elastic_Search(
            int expectedNumber, String xcollab, String index) {

        index = generateActualName(index, timeStamp);
        xcollab = resolveCollaborationHeader(xcollab);

        String id = Arrays.stream(lastIngestedRecordIds).findAny()
            .orElseThrow(() -> new AssertionError("No record IDs from previous ingest step"));
        log.info("Looking for record with x-collab in Elastic, id={}", id);

        CollaborationContextFactory collaborationContextFactory = new CollaborationContextFactory();
        Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(xcollab);
        String collaborationId = collaborationContext.orElseThrow().getId();

        final String idx = index;
        SearchResponse<Record> searchResponse = null;
        for (int attempt = 0; attempt < PollingClient.DEFAULT_MAX_ATTEMPTS && searchResponse == null; attempt++) {
            try {
                Thread.sleep(PollingClient.DEFAULT_INTERVAL_SECONDS * 1000L);
                SearchResponse<Record> resp = elasticClient.fetchRecordsByIdAndMustHaveXcollab(idx, id, collaborationId);
                if (resp.hits().total().value() > 0) {
                    searchResponse = resp;
                } else if (attempt % 3 == 0) {
                    try { elasticClient.refreshIndex(idx); } catch (Exception e) { /* ignore */ }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("xcollab polling attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }
        if (searchResponse == null) {
            searchResponse = elasticClient.fetchRecordsByIdAndMustHaveXcollab(index, id, collaborationId);
        }

        log.info("xcollab feature: searchResponse={}", searchResponse);
        assertEquals(expectedNumber, searchResponse.hits().total().value());

        List<Hit<Record>> hits = searchResponse.hits().hits();
        String elasticId = hits.stream()
            .findAny()
            .orElseThrow(() -> new AssertionError("No Elasticsearch hits returned for x-collaboration query"))
            .id();
        DeleteResponse deleteResponse = elasticClient.deleteRecordsById(index, elasticId);
        log.info("Deleted record from Elasticsearch, index={}, id={}", index, elasticId);
        assertEquals(Result.Deleted, deleteResponse.result());
    }

    // Each collaboration test run needs a fresh UUID so Storage does not attach the request to stale
    // server-side WIP state from a previous run, but ingest and verification must still use the same
    // resolved header value within the scenario.
    // Examples:
    // "id=<uuid>,application=pws" -> "id=123e4567-e89b-12d3-a456-426614174000,application=pws"
    // "id=<uuid>,application=app-<timestamp>" -> "id=123e4567-...,application=app-1781678406809"
    private String resolveCollaborationHeader(String rawXCollab) {
        return resolvedCollaborationHeaders.computeIfAbsent(rawXCollab, key ->
            generateActualName(key, timeStamp).replace("<uuid>", UUID.randomUUID().toString()));
    }

    private Map<String, Object> replaceValues(Map<String, Object> data, String timeStamp) {
        if (data == null) return null;
        data.replaceAll((key, value) -> replaceValue(value, timeStamp));
        return data;
    }

    private List<Object> replaceValues(List<?> values, String timeStamp) {
        List<Object> replacedValues = new ArrayList<>();
        for (Object value : values) {
            replacedValues.add(replaceValue(value, timeStamp));
        }
        return replacedValues;
    }

    private Map<String, Object> replaceMapValues(Map<?, ?> values, String timeStamp) {
        Map<String, Object> replacedValues = new HashMap<>();
        values.forEach((key, value) -> replacedValues.put(String.valueOf(key), replaceValue(value, timeStamp)));
        return replacedValues;
    }

    private Object replaceValue(Object value, String timeStamp) {
        if (value instanceof String rawValue) {
            for (Map.Entry<String, String> tenant : tenantMap.entrySet()) {
                rawValue = rawValue.replace(tenant.getKey() + ":", tenant.getValue() + ":");
            }
            return rawValue.replace("<timestamp>", timeStamp);
        } else if (value instanceof List<?> values) {
            return replaceValues(values, timeStamp);
        } else if (value instanceof Map<?, ?> values) {
            return replaceMapValues(values, timeStamp);
        }
        return value;
    }

    private PollingClient pollingClient() {
        return PollingClient.builder().elasticClient(elasticClient).build();
    }

    private long createIndex(String index) {
        PollingResult<Long> result = pollingClient().pollForDocuments(index);
        if (!result.isSuccess()) {
            fail(String.format("Index '%s' not created: %s", index, result.getFailureReason()));
        }
        return result.getValue();
    }

    private long getRecordsInIndex(String index, int expectedCount) {
        PollingResult<Long> result = pollingClient().pollForExpectedCount(index, expectedCount);
        if (!result.isSuccess()) {
            fail(String.format("Expected %d documents in index '%s', but polling failed: %s", expectedCount, index, result.getFailureReason()));
        }
        return result.getValue();
    }

    private void awaitIndexCreation(String index) {
        elasticClient.awaitIndexCreation(index);
    }

    /**
     * Polls a query function until it returns the expected document count, then asserts on it.
     * On timeout, fails with a message that includes the index, query description, expected vs.
     * last-seen count, attempts, and elapsed wait time so the JUnit failure tells you exactly
     * what timed out.
     */
    private void assertQueryReturns(int expectedCount, String index, String queryDescription,
                                    Supplier<Long> queryCount) {
        awaitIndexCreation(index);
        PollingResult<Long> result = pollingClient().pollForQueryResultCount(index, expectedCount, queryCount);
        if (!result.isSuccess()) {
            fail(String.format(
                "Expected %d documents from %s on '%s' but got %s after %.1fs in %d attempts",
                expectedCount, queryDescription, index,
                result.getValue(), result.getTotalWaitTimeSeconds(), result.getAttempts()));
        }
        assertEquals(expectedCount, result.getValue().longValue());
    }

    private boolean areJsonEqual(String firstJson, String secondJson) {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> firstMap = gson.fromJson(firstJson, mapType);
        Map<String, Object> secondMap = gson.fromJson(secondJson, mapType);

        MapDifference<String, Object> result = Maps.difference(firstMap, secondMap);
        boolean equal = result.entriesDiffering().isEmpty();
        if (!equal) {
            log.info("difference: {}", result.entriesDiffering());
        }
        return equal;
    }

    // ============ ALIAS-SPECIFIC METHODS ============

    public void i_verify_alias_exists_and_points_to_physical_index(String aliasName, String physicalIndexName) {
        String actualAlias = generateActualName(aliasName, timeStamp);
        String expectedPhysicalIndex = generateActualName(physicalIndexName, timeStamp);

        awaitCondition(
            String.format("alias '%s' to exist and point to physical index '%s'", actualAlias, expectedPhysicalIndex),
            () -> elasticClient.aliasExists(actualAlias)
                && expectedPhysicalIndex.equals(elasticClient.getPhysicalIndexFromAlias(actualAlias)));

        log.info("Verified alias '{}' exists and points to physical index '{}'", actualAlias, expectedPhysicalIndex);
    }

    public void i_delete_index_via_service_endpoint(String kind) {
        String actualKind = generateActualName(kind, timeStamp);
        indexerClient.deleteIndex(actualKind);
        log.info("Deleted index via indexer service for kind '{}'", actualKind);
    }

    public void i_verify_physical_index_exists(String physicalIndexName) {
        String actualPhysicalIndex = generateActualName(physicalIndexName, timeStamp);
        awaitCondition(String.format("physical index '%s' to exist", actualPhysicalIndex),
            () -> elasticClient.physicalIndexExists(actualPhysicalIndex));
        log.info("Verified physical index exists: {}", actualPhysicalIndex);
    }

    public void i_create_physical_index(String indexName) {
        String actualIndexName = generateActualName(indexName, timeStamp);
        elasticClient.createIndex(actualIndexName, "{}");
        log.info("Created physical index: {}", actualIndexName);
    }

    public void i_verify_physical_index_does_not_exist(String physicalIndexName) {
        String actualPhysicalIndex = generateActualName(physicalIndexName, timeStamp);
        awaitCondition(String.format("physical index '%s' to be deleted", actualPhysicalIndex),
            () -> !elasticClient.physicalIndexExists(actualPhysicalIndex));
        log.info("Verified physical index does not exist: {}", actualPhysicalIndex);
    }

    public void i_verify_alias_does_not_exist(String aliasName) {
        String actualAlias = generateActualName(aliasName, timeStamp);
        awaitCondition(String.format("alias '%s' to be deleted", actualAlias),
            () -> !elasticClient.aliasExists(actualAlias));
        log.info("Verified alias does not exist: {}", actualAlias);
    }

    // ============ SCHEMA MERGE METHODS ============

    public void i_verify_mapping_merged_in_physical_index(String physicalIndexName) {
        String actualIndex = generateActualName(physicalIndexName, timeStamp);

        assertTrue(elasticClient.physicalIndexExists(actualIndex),
            "Physical index should still exist after merge: " + actualIndex);

        PollingResult<Long> result = pollingClient().pollForDocuments(actualIndex);
        if (!result.isSuccess()) {
            fail(String.format("Documents should still be available after mapping merge on '%s': %s",
                actualIndex, result.getFailureReason()));
        }
        assertTrue(result.getValue() > 0, "Documents should still exist after mapping merge");

        log.info("Verified mapping merged in physical index '{}' with {} documents", actualIndex, result.getValue());
    }

    public void i_verify_fields_present_in_mapping(String newFields, String physicalIndexName) {
        String actualIndex = generateActualName(physicalIndexName, timeStamp);
        String[] fieldNames = newFields.split(",");

        awaitCondition(String.format("fields '%s' to be present in the mapping of '%s'", newFields, actualIndex),
            () -> elasticClient.checkMappingFieldsExist(actualIndex, fieldNames));

        log.info("Verified fields are present in the mapping: {}", newFields);
    }

    public void i_create_physical_index_with_initial_mapping(String indexName) {
        String actualIndexName = generateActualName(indexName, timeStamp);

        Map<String, Object> properties = new HashMap<>();
        properties.put("id", Map.of("type", "keyword"));
        properties.put("kind", Map.of("type", "keyword"));
        properties.put("version", Map.of("type", "long"));
        properties.put("acl", Map.of("properties", Map.of(
            "viewers", Map.of("type", "keyword"),
            "owners", Map.of("type", "keyword"))));

        Map<String, Object> dataProperties = new HashMap<>();
        dataProperties.put("TestField1", textWithKeyword());
        dataProperties.put("TestField2", Map.of("type", "integer"));
        dataProperties.put("Location", Map.of("type", "geo_point"));
        dataProperties.put("WellName", textWithKeyword());
        dataProperties.put("Status", textWithKeyword());
        dataProperties.put("CreatedDate", Map.of("type", "date"));
        dataProperties.put("Score", Map.of("type", "integer"));
        properties.put("data", Map.of("properties", dataProperties));

        String mappingJson = new Gson().toJson(Map.of("mappings", Map.of("properties", properties)));
        elasticClient.createIndex(actualIndexName, mappingJson);

        log.info("Created physical index with initial mapping: {}", actualIndexName);
    }

    private static Map<String, Object> textWithKeyword() {
        return Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword")));
    }

    /**
     * Polls {@code condition} until it holds, using the same attempt/interval budget as
     * {@link PollingClient}. Elasticsearch state (alias creation, index deletion, mapping merge)
     * is applied asynchronously, so a single check right after the triggering call is flaky.
     */
    private void awaitCondition(String description, BooleanSupplier condition) {
        for (int attempt = 0; attempt < PollingClient.DEFAULT_MAX_ATTEMPTS; attempt++) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (Exception e) {
                log.warn("Attempt {} while waiting for {} failed: {}", attempt + 1, description, e.getMessage());
            }
            try {
                Thread.sleep(PollingClient.DEFAULT_INTERVAL_SECONDS * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail(String.format("Timed out after %d attempts waiting for %s",
            PollingClient.DEFAULT_MAX_ATTEMPTS, description));
    }


    // ============ REINDEX V1 METHODS ============

    private int lastReindexStatusCode;
    private long documentCountBeforeReindex;
    private String reindexIndex;
    private String forceCleanMarkerId;

    public void i_prepare_missing_documents_before_reindex(String index) {
        assertNotNull(lastIngestedRecordIds, "Records must be ingested before preparing reindex");
        assertTrue(lastIngestedRecordIds.length > 0, "No ingested record IDs available for reindex");
        reindexIndex = generateActualName(index, timeStamp);
        documentCountBeforeReindex = getRecordsInIndex(reindexIndex, lastIngestedRecordIds.length);
        for (String id : lastIngestedRecordIds) {
            assertEquals(Result.Deleted, elasticClient.deleteRecordsById(reindexIndex, id).result(),
                "Expected an indexed document to be removed before reindex: " + id);
        }
        elasticClient.refreshIndex(reindexIndex);
        getRecordsInIndex(reindexIndex, 0);
        log.info("Removed {} Elasticsearch documents from '{}', retaining them in Storage",
            documentCountBeforeReindex, reindexIndex);
    }

    public void i_trigger_reindex_for_kind_with_cursor(String kind, String cursor) {
        triggerReindex(generateActualName(kind, timeStamp), cursor, Map.of());
    }

    public void i_trigger_reindex_for_kind_with_force_clean(String kind) {
        String actualKind = generateActualName(kind, timeStamp);
        assertNotNull(reindexIndex, "Missing documents must be prepared before force-clean reindex");
        forceCleanMarkerId = actualKind.split(":")[0] + ":reindex-marker:" + UUID.randomUUID();
        elasticClient.indexRecords(reindexIndex, actualKind, List.of(
            new HashMap<>(Map.of("id", forceCleanMarkerId, "kind", actualKind))));
        elasticClient.refreshIndex(reindexIndex);
        getRecordsInIndex(reindexIndex, 1);
        triggerReindex(actualKind, "", Map.of("force_clean", "true"));
    }

    public void i_trigger_reindex_for_invalid_kind(String invalidKind, String cursor) {
        try {
            triggerReindex(invalidKind, cursor, Map.of());
        } catch (ClientException e) {
            if (e.getStatusCode() != 400) {
                throw e;
            }
            lastReindexStatusCode = e.getStatusCode();
            log.info("Expected invalid-kind reindex response: {}", e.getMessage());
        }
    }

    public void i_should_get_successful_reindex_response() {
        assertTrue(lastReindexStatusCode == 200 || lastReindexStatusCode == 202,
            "Expected a successful reindex response (200 or 202), but got: " + lastReindexStatusCode);
    }

    public void i_should_verify_reindexed_documents_in_index(String index) {
        String actualIndex = generateActualName(index, timeStamp);
        assertEquals(reindexIndex, actualIndex, "Verify the index prepared for reindex");
        assertTrue(documentCountBeforeReindex > 0, "A non-empty baseline is required for reindex");
        getRecordsInIndex(actualIndex, Math.toIntExact(documentCountBeforeReindex));
        for (String id : lastIngestedRecordIds) {
            assertEquals(1, elasticClient.fetchRecordsByFieldAndFieldValue(actualIndex, "id", id),
                "Reindex must restore the removed document: " + id);
        }
        if (forceCleanMarkerId != null) {
            assertEquals(0, elasticClient.fetchRecordsByFieldAndFieldValue(actualIndex, "id", forceCleanMarkerId),
                "Force-clean must remove the Elasticsearch-only marker document");
        }
    }

    public void i_should_get_reindex_error_response_with_status_code(int expectedStatusCode) {
        assertEquals(expectedStatusCode, lastReindexStatusCode, "Unexpected reindex error status code");
    }

    private void triggerReindex(String kind, String cursor, Map<String, String> queryParams) {
        Map<String, String> payload = new HashMap<>();
        payload.put("kind", kind);
        payload.put("cursor", cursor);
        String body = new Gson().toJson(payload);
        HttpResponse<?> response;
        if (queryParams.isEmpty()) {
            response = indexerClient.reindex(body);
        } else {
            // IndexerClient.reindex(body, Map) treats the map as headers, so query parameters go in the path.
            String query = queryParams.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
            try {
                response = indexerClient.post(DEFAULT_USER, ServiceType.INDEXER_V2, "reindex?" + query, body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        lastReindexStatusCode = response.statusCode();
        log.info("Reindex of kind='{}' with params={} returned status={}", kind, queryParams, lastReindexStatusCode);
    }

}
