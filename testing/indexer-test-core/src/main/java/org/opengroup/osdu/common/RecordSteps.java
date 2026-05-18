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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.opengroup.osdu.util.Config.getEntitlementsDomain;
import static org.opengroup.osdu.util.Config.getIndexerBaseURL;
import static org.opengroup.osdu.util.Config.getStorageBaseURL;
import static org.opengroup.osdu.util.JsonPathMatcher.FindArrayInJson;

import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.json.JsonpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.cucumber.datatable.DataTable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.UpsertRecords;
import org.opengroup.osdu.models.Setup;
import org.opengroup.osdu.models.TestIndex;
import org.opengroup.osdu.models.record.RecordData;
import org.opengroup.osdu.util.ElasticUtils;
import org.opengroup.osdu.util.FileHandler;
import org.opengroup.osdu.util.HTTPClient;
import org.opengroup.osdu.util.HttpResponse;
import org.opengroup.osdu.util.PollingConfig;
import org.opengroup.osdu.util.PollingResult;
import org.opengroup.osdu.util.PollingUtils;
import org.springframework.util.CollectionUtils;

import static org.opengroup.osdu.util.HTTPClient.indentatedResponseBody;

@Log
public class RecordSteps extends TestsBase {
    private Map<String, TestIndex> inputIndexMap = new HashMap<>();
    private ObjectMapper mapper = new ObjectMapper();
    private boolean shutDownHookAdded = false;

    private static final AtomicBoolean staleIndicesCleaned = new AtomicBoolean(false);
    private static final long DEFAULT_STALE_INDEX_MAX_AGE_MS = TimeUnit.HOURS.toMillis(2);

    // Append random number to timestamp to ensure uniqueness when tests run in parallel
    private String timeStamp = System.currentTimeMillis() + String.valueOf(new java.util.Random().nextInt(10000));
    private List<Map<String, Object>> records;
    private Map<String, String> headers = httpClient.getCommonHeader();
    private List<String> ingestedRecordIds = new ArrayList<>();

    private UpsertRecords upsertedRecordsWithXcollab;
    public static final String DpsHeaders_COLLABORATION = "x-collaboration";
    public static final String X_COLLABORATION = "collaborationId";

    public RecordSteps(HTTPClient httpClient) {
        super(httpClient);
    }

    public RecordSteps(HTTPClient httpClient, ElasticUtils elasticUtils) {
        super(httpClient, elasticUtils);
    }

    /**
     * One-time cleanup of stale test indices from previous failed runs.
     * Uses AtomicBoolean to ensure it runs exactly once per JVM, even under
     * parallel scenario execution. The age threshold is configurable via the
     * system property {@code STALE_INDEX_MAX_AGE_MS} (defaults to 2 hours).
     */
    private void cleanupStaleTestIndicesOnce() {
        if (staleIndicesCleaned.compareAndSet(false, true)) {
            long maxAgeMs = Long.getLong("STALE_INDEX_MAX_AGE_MS", DEFAULT_STALE_INDEX_MAX_AGE_MS);
            elasticUtils.deleteStaleTestIndices(maxAgeMs);
        }
    }

    /******************One time cleanup for whole feature**************/
    public void tearDown() {
        for (String kind : inputIndexMap.keySet()) {
            TestIndex testIndex = inputIndexMap.get(kind);
            testIndex.cleanupIndex(kind);
            testIndex.deleteSchema(kind);
        }

        if (!CollectionUtils.isEmpty(records)) {
            cleanupRecords();
        }
    }

    protected void cleanupRecords() {
        for (Map<String, Object> testRecord : records) {
            String id = testRecord.get("id").toString();
            httpClient.send("DELETE", getStorageBaseURL() + "records/" + id, null, headers, httpClient.getAccessToken());
            log.info("Deleted the records");
        }
    }

    public void the_schema_is_created_with_the_following_kind(DataTable dataTable) {
        cleanupStaleTestIndicesOnce();

        List<Setup> inputList = dataTable.asList(Setup.class);
        for (Setup input : inputList) {
            TestIndex testIndex = getTextIndex();
            testIndex.setHttpClient(httpClient);
            testIndex.setIndex(generateActualName(input.getIndex(), timeStamp));
            testIndex.setKind(generateActualName(input.getKind(), timeStamp));
            testIndex.setSchemaFile(input.getSchemaFile());
            inputIndexMap.put(testIndex.getKind(), testIndex);
        }

        /******************One time setup for whole feature**************/
        if (!shutDownHookAdded) {
            for (String kind : inputIndexMap.keySet()) {
                TestIndex testIndex = inputIndexMap.get(kind);
                testIndex.setupSchema();
            }
        }
        addShutDownHook();
    }

    public void i_ingest_records_with_the_for_a_given(String record, String dataGroup, String kind) {

        String actualKind = generateActualName(kind, timeStamp);
        try {
            String fileContent = FileHandler.readFile(String.format("%s.%s", record, "json"));
            records = new Gson().fromJson(fileContent, new TypeToken<List<Map<String, Object>>>() {}.getType());
            String createTime = java.time.Instant.now().toString();

            // Clear any previously stored record IDs
            ingestedRecordIds.clear();

            for (Map<String, Object> testRecord : records) {
                if(testRecord.containsKey("data")) {
                    Map<String, Object> data = (Map<String, Object>)testRecord.get("data");
                    if(data != null && data.size() > 0) {
                        data = replaceValues(data, timeStamp);
                        testRecord.put("data", data);
                    }
                }

                testRecord.put("kind", actualKind);
                String recordId = generateRecordId(testRecord);
                testRecord.put("id", recordId);

                // Store the generated record ID for later use in reindex tests
                ingestedRecordIds.add(recordId);
                testRecord.put("legal", generateLegalTag());
                String[] x_acl = {generateActualName(dataGroup,timeStamp)+"."+getEntitlementsDomain()};
                Acl acl = Acl.builder().viewers(x_acl).owners(x_acl).build();
                testRecord.put("acl", acl);
                String[] kindParts = kind.split(":");
                String authority = tenantMap.get(kindParts[0]);
                String source = kindParts[1];
                testRecord.put("authority", authority);
                testRecord.put("source", source);
                testRecord.put("createUser", "TestUser");
                testRecord.put("createTime", createTime);
            }
            String payLoad = new Gson().toJson(records);
            log.log(Level.INFO, "Start ingesting records={0}", payLoad);
            HttpResponse httpResponse = httpClient.send("PUT", getStorageBaseURL() + "records", payLoad, headers, httpClient.getAccessToken());
            log.info(String.format("Response body: %s\n Correlation id: %s\nResponse Status code: %s", indentatedResponseBody(httpResponse.getEntity(String.class)), httpResponse.getHeaders().get("correlation-id"), httpResponse.getStatus()));
            assertEquals(201, httpResponse.getStatus());

            log.log(Level.INFO, "Captured " + ingestedRecordIds.size() + " record IDs for potential reindex operations");

        } catch (Exception ex) {
            throw new AssertionError(ex.getMessage());
        }
    }

    public String generateActualName(String rawName){
        for (Map.Entry<String, String> tenant : tenantMap.entrySet()) {
            rawName = rawName.replaceAll(tenant.getKey(), tenant.getValue());
        }
        return rawName.replaceAll("<timestamp>", timeStamp);
    }

    protected String generateRecordId(Map<String, Object> testRecord) {
        return generateActualId(testRecord.get("id").toString(), timeStamp, testRecord.get("kind").toString());
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
        Map<String, IndexMappingRecord> elasticMapping = elasticUtils.getMapping(index);
        assertNotNull(elasticMapping);

        String[] kindParts = kind.split(":");
        String authority = tenantMap.get(kindParts[0]);
        String source = kindParts[1];
        expectedMapping = expectedMapping.replaceAll("<authority-id>", authority).replaceAll("<source-id>", source);

        // Handle both alias and physical index scenarios
        IndexMappingRecord typeMapping = elasticMapping.get(index);

        // If the index is an alias, the mapping will be returned with the physical index name as key
        // So if we don't find it with the alias name, try to get the first (and likely only) entry
        if (typeMapping == null && !elasticMapping.isEmpty()) {
            // Get the first entry from the mapping (should be the physical index)
            typeMapping = elasticMapping.values().iterator().next();
        }

        assertNotNull(typeMapping, "Could not retrieve mapping for index/alias: " + index);

        StringBuilder collector = new StringBuilder();
        TypeMapping mappings = typeMapping.mappings();
        JsonpUtils.toString(mappings, collector);
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> mapping = new Gson().fromJson(collector.toString(), type);

        assertNotNull(mapping);
        assertTrue(areJsonEqual(expectedMapping, mapping.toString()));
    }

    public void i_can_validate_indexed_attributes(String index, String kind) throws Throwable {
        String authority = tenantMap.get(kind.substring(0, kind.indexOf(":")));
        index = generateActualName(index, timeStamp);
        long numOfIndexedDocuments = createIndex(index);
        List<Map<String, Object>> hits = elasticUtils.fetchRecordsByAttribute(index, "authority", authority);

        assertTrue(hits.size() > 0);
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
            () -> { try { return elasticUtils.fetchRecordsByExistQuery(idx, skippedAttributes); } catch (Exception e) { return -1L; } });
    }

    public void iShouldBeAbleToSearchRecordByTagKeyAndTagValue(String index, String tagKey, String tagValue, int expectedNumber) throws Throwable {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedNumber, idx,
            String.format("tag query %s=%s", tagKey, tagValue),
            () -> { try { return elasticUtils.fetchRecordsByTags(idx, tagKey, tagValue); } catch (Exception e) { return -1L; } });
    }

    public void iShouldCleanupIndicesOfExtendedKinds(String extendedKinds) throws Throwable {
        String[] kinds = extendedKinds.split(",");
        for(String kind : kinds) {
            String actualKind = this.generateActualName(kind.trim(), timeStamp);
            TestIndex testIndex = this.getInputIndexMap().get(actualKind);
            testIndex.cleanupIndex(actualKind);
        }
    }

    public void iShouldBeAbleToSearchRecordByFieldAndFieldValue(String index, String fieldKey, String fieldValue, int expectedNumber) throws Throwable {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedNumber, idx,
            String.format("field query %s=%s", fieldKey, fieldValue),
            () -> { try { return elasticUtils.fetchRecordsByFieldAndFieldValue(idx, fieldKey, fieldValue); } catch (Exception e) { return -1L; } });
    }

    /**
     * Asserts that the specified field in the given ES index has the expected mapping type.
     * Supports dot-separated field paths (e.g., "data.IsActive").
     */
    public void verifyElasticFieldType(String index, String fieldPath, String expectedType) {
        index = generateActualName(index, timeStamp);
        String actualType = elasticUtils.getFieldType(index, fieldPath);
        assertNotNull(actualType,
            "Field '" + fieldPath + "' not found in ES index '" + index + "'");
        assertEquals(expectedType, actualType,
            "Field '" + fieldPath + "' in index '" + index +
            "' expected ES type '" + expectedType + "' but found '" + actualType + "'");
    }

    /**
     * Asserts that a TermQuery with a native boolean value finds the expected number of documents.
     * This distinguishes boolean-indexed fields from text fields: TermQuery(booleanValue=true)
     * matches boolean-typed fields, but does NOT match text fields storing the string "true".
     */
    public void iShouldBeAbleToFindRecordsByBooleanFieldValue(String index, String fieldKey, boolean booleanValue, int expectedNumber) {
        index = generateActualName(index, timeStamp);
        long actualCount = elasticUtils.fetchRecordsByBooleanFieldValue(index, fieldKey, booleanValue);
        assertEquals(expectedNumber, actualCount,
            "Expected " + expectedNumber + " documents matching boolean TermQuery [" +
            fieldKey + "=" + booleanValue + "] in index '" + index + "' but found " + actualCount);
    }

    public void i_should_get_the_documents_for_the_in_the_Elastic_Search_by_geoQuery (
            int expectedNumber, String index, Double topLatitude, Double topLongitude, Double bottomLatitude, Double bottomLongitude, String field) throws Throwable {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedNumber, idx,
            String.format("geo bounding-box query on %s", field),
            () -> { try { return elasticUtils.fetchRecordsByGeoWithinQuery(idx, field, topLatitude, topLongitude, bottomLatitude, bottomLongitude); } catch (Exception e) { return -1L; } });
    }

    public void i_should_get_the_documents_for_the_in_the_Elastic_Search_by_AsIngestedCoordinates(
            int expectedNumber, String index, Double topPointX, Double bottomPointX, String pointX, Double topPointY, Double bottomPointY, String pointY) throws Throwable {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedNumber, idx,
            String.format("AsIngestedCoordinates bounding-box on (%s,%s)", pointX, pointY),
            () -> { try { return elasticUtils.fetchRecordsByAsIngestedCoordinates(idx, pointX, topPointX, bottomPointX, pointY, topPointY, bottomPointY); } catch (Exception e) { return -1L; } });
    }

    public void i_should_get_the_documents_for_the_in_the_Elastic_Search_by_nestedQuery(
        int expectedNumber, String index, String path, String firstNestedField, String firstNestedValue, String secondNestedField, String secondNestedValue)
        throws Throwable {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedNumber, idx,
            String.format("nested query on %s (%s=%s, %s=%s)", path, firstNestedField, firstNestedValue, secondNestedField, secondNestedValue),
            () -> { try { return elasticUtils.fetchRecordsByNestedQuery(idx, path, firstNestedField, firstNestedValue, secondNestedField, secondNestedValue); } catch (Exception e) { return -1L; } });
    }

    public void i_should_be_able_search_documents_for_the_by_flattened_inner_properties(int expectedCount, String index, String flattenedField,
        String flattenedFieldValue) throws Throwable {
        final String idx = generateActualName(index, timeStamp);
        assertQueryReturns(expectedCount, idx,
            String.format("flattened inner-property query %s=%s", flattenedField, flattenedFieldValue),
            () -> { try { return elasticUtils.fetchRecordsWithFlattenedFieldsQuery(idx, flattenedField, flattenedFieldValue); } catch (Exception e) { return -1L; } });
    }

    public void i_should_get_object_in_search_response_without_hints_in_schema(String objectField, String index, String recordFile, String acl, String kind)
        throws Throwable {
        index = generateActualName(index, timeStamp);

        createIndex(index);

        String expectedRecord = FileHandler.readFile(String.format("%s.%s", recordFile, "json"));

        RecordData[] fileRecordData = mapper.readValue(expectedRecord, RecordData[].class);
        RecordData expectedRecordData = fileRecordData[0];

        RecordData actualRecordData = elasticUtils.fetchDataFromObjectsArrayRecords(index);

        assertEquals(expectedRecordData.getData().get(objectField),actualRecordData.getData().get(objectField));
    }

    public void i_should_get_object_in_search_response(String innerField, String index)
            throws Throwable {
        index = generateActualName(index, timeStamp);

        createIndex(index);

        RecordData actualRecordData = elasticUtils.fetchDataFromObjectsArrayRecords(index);

        assertTrue(actualRecordData.getData().containsKey(innerField));
    }

    public void i_should_get_string_array_in_search_response(String index, String field, String fieldValue, String arrayField, String desiredArrayValue)
            throws Throwable {
        // Ensure the index exists and wait for records to be indexed
        index = generateActualName(index, timeStamp);
        createIndex(index);

        final List<Map<String, Object>> elasticRecordData =  elasticUtils.fetchRecordsByAttribute(index, field, fieldValue);
        assertEquals(1, elasticRecordData.size());
        final List<String> stringList = Arrays.asList(arrayField.split("\\."));
        final Map<String, Object> jsonRecord = elasticRecordData.get(0);
        assertEquals(String.join(",", (ArrayList<String>) FindArrayInJson(jsonRecord, stringList)), desiredArrayValue);
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
        try {
            String fileContent = FileHandler.readFile(String.format("%s.%s", record, "json"));
            records = new Gson().fromJson(fileContent, new TypeToken<List<Map<String, Object>>>() {
            }.getType());
            String createTime = java.time.Instant.now().toString();

            for (Map<String, Object> testRecord : records) {
                if (testRecord.containsKey("data")) {
                    Map<String, Object> data = (Map<String, Object>) testRecord.get("data");
                    if (data != null && data.size() > 0) {
                        data = replaceValues(data, timeStamp);
                        testRecord.put("data", data);
                    }
                }

                testRecord.put("kind", actualKind);
                testRecord.put("id", generateRecordId(testRecord));
                testRecord.put("legal", generateLegalTag());
                String[] x_acl = {generateActualName(dataGroup, timeStamp) + "." + getEntitlementsDomain()};
                Acl acl = Acl.builder().viewers(x_acl).owners(x_acl).build();
                testRecord.put("acl", acl);
                String[] kindParts = kind.split(":");
                String authority = tenantMap.get(kindParts[0]);
                String source = kindParts[1];
                testRecord.put("authority", authority);
                testRecord.put("source", source);
                testRecord.put("createUser", "TestUser");
                testRecord.put("createTime", createTime);
            }

            // put record in WIP by the use of x-collaboration header
            Map<String, String> headerXcollab = httpClient.getCommonHeader();
            headerXcollab.put(DpsHeaders_COLLABORATION, xCollab);

            String payLoad = new Gson().toJson(records);
            log.log(Level.INFO, "Start ingesting records={0}", payLoad);
            HttpResponse httpResponse = httpClient.send("PUT",
                getStorageBaseURL() + "records",
                payLoad,
                headerXcollab,
                httpClient.getAccessToken());

            String responseEntity = httpResponse.getEntity(String.class);
            log.info(String.format("Response body with xcollab: %s\n Correlation id: %s\nResponse Status code: %s",
                indentatedResponseBody(responseEntity),
                httpResponse.getHeaders().get("correlation-id"),
                httpResponse.getStatus()));
            assertEquals(201, httpResponse.getStatus());

            // remember record id for future tests
            upsertedRecordsWithXcollab = mapper.readValue(responseEntity, UpsertRecords.class);
            upsertedRecordsWithXcollab.getRecordIds()
                .forEach(recordId -> log.info("Record ids with xcollab : " + recordId));

            Optional<String> recordWithXcollab = upsertedRecordsWithXcollab.getRecordIds().stream().findAny();
            assertTrue(recordWithXcollab.isPresent());

        } catch (Exception ex) {
            throw new AssertionError(ex.getMessage());
        }
    }

    protected void i_should_get_the_documents_with_xcollab_value_included_for_the_in_the_Elastic_Search(
        int expectedNumber, String xcollab, String index) throws Exception {

        index = generateActualName(index, timeStamp);
        // upsertedRecordsWithoutXcollab should have id received from previous steps
        String id = upsertedRecordsWithXcollab.getRecordIds().stream().findAny().get();
        log.log(Level.INFO, String.format("Try to find in Elastic a record with X collab with id : %s ", id));

        SearchResponse<Record> searchResponse = null;
        // should wait while Storage will publish Record into queue,
        // then Index-queue should read message and pass it with http request to Indexer
        // Indexer then will index the record into Elastic
        CollaborationContextFactory collaborationContextFactory = new CollaborationContextFactory();
        Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(xcollab);
        String collaborationId = collaborationContext.orElseThrow().getId();

        for (int i = 0; i < 20; i++) {
            searchResponse = elasticUtils.fetchRecordsByIdAndMustHaveXcollab(index, id, collaborationId);
            if (searchResponse.hits().total().value() == 0) {
                log.log(Level.INFO, String.format("No records found with in index: %s, id: %s, collaborationId: %s,"
                    + " will try to wait up to 3 seconds.", index, id, collaborationId));
                TimeUnit.SECONDS.sleep(3);
            } else {
                break;
            }
        }

        log.log(Level.INFO,
            String.format("xcollab feature: print searchResponse while being get a record by id and x-collab : %s",
                searchResponse));
        assertEquals(expectedNumber, searchResponse.hits().total().value());

        // delete test record in namespace
        List<Hit<Record>> hits = searchResponse.hits().hits();

        String elasticId = hits.stream().findAny().get().id();
        DeleteResponse deleteResponse = elasticUtils.deleteRecordsById(index, elasticId);
        log.log(Level.INFO, String.format("Deleting record from Elasticsearch in index: %s with id: %s", index, elasticId));
        assertEquals(deleteResponse.result(), Result.Deleted);
    }

    private Map<String, Object> replaceValues(Map<String, Object> data, String timeStamp) {
        for(String key : data.keySet()) {
            Object value = data.get(key);
            Object replacedValue = replaceValue(value, timeStamp);
            data.put(key, replacedValue);
        }
        return data;
    }

    private List<Object> replaceValues(List<Object> values, String timeStamp) {
        List<Object> replacedValues = new ArrayList<>();
        for(Object value : values) {
            Object replacedValue = replaceValue(value, timeStamp);
            replacedValues.add(replacedValue);
        }

        return replacedValues;
    }

    private Object replaceValue(Object value, String timeStamp) {
        Object replacedValue = value;

        if(value instanceof String) {
            String rawValue = (String) value;
            for (Map.Entry<String, String> tenant : tenantMap.entrySet()) {
                rawValue = rawValue.replaceAll(tenant.getKey() + ":", tenant.getValue() + ":");
            }
            replacedValue = rawValue.replaceAll("<timestamp>", timeStamp);
        }
        else if(value instanceof List) {
            replacedValue = replaceValues((List)value, timeStamp);
        }
        else if(value instanceof Map) {
            replacedValue = replaceValues((Map<String, Object>) value, timeStamp);
        }

        return replacedValue;
    }


    private long createIndex(String index) throws InterruptedException, IOException {
        // Use PollingUtils for document polling
        PollingResult<Long> result = PollingUtils.pollForDocuments(
            index,
            PollingConfig.documentPolling(),
            elasticUtils
        );

        if (result.isSuccess()) {
            log.info(String.format("Index: %s | Documents: %d | Total wait: %.1fs",
                index, result.getValue(), result.getTotalWaitTimeSeconds()));
            return result.getValue();
        } else {
            fail(result.getFailureReason());
            return 0; // This line will never be reached due to fail()
        }
    }

    private long getRecordsInIndex(String index, int expectedCount) throws InterruptedException, IOException {
        // Use PollingUtils for polling with expected count
        PollingResult<Long> result = PollingUtils.pollForExpectedCount(
            index,
            expectedCount,
            PollingConfig.documentPollingWithExpectedCount(),
            elasticUtils
        );

        if (result.isSuccess()) {
            log.info(String.format("Index: %s | Expected: %d | Found: %d | Total wait: %.1fs",
                index, expectedCount, result.getValue(), result.getTotalWaitTimeSeconds()));
            return result.getValue();
        } else {
            fail(String.format("Expected %d documents in index '%s' but got different count after %.1f seconds",
                expectedCount, index, result.getTotalWaitTimeSeconds()));
            return 0; // This line will never be reached due to fail()
        }
    }

    /**
     * Polls a query function until it returns the expected document count, then asserts on it.
     * On timeout, fails with a message that includes the index, query description, expected vs.
     * last-seen count, attempts, and elapsed wait time so the JUnit failure tells you exactly
     * what timed out.
     */
    private void assertQueryReturns(int expectedCount, String index, String queryDescription,
                                    Supplier<Long> queryCount) throws InterruptedException {
        PollingResult<Long> result = PollingUtils.pollForQueryResultCount(
            index, expectedCount, queryCount, elasticUtils);
        if (!result.isSuccess()) {
            fail(String.format(
                "Expected %d documents from %s on '%s' but got %s after %.1fs in %d attempts",
                expectedCount, queryDescription, index,
                result.getValue(), result.getTotalWaitTimeSeconds(), result.getAttempts()));
        }
        assertEquals(expectedCount, result.getValue().longValue());
    }

    private Boolean areJsonEqual(String firstJson, String secondJson) {
        // Strip surrounding quotes if present (from feature file formatting)
        if (firstJson.startsWith("\"") && firstJson.endsWith("\"")) {
            firstJson = firstJson.substring(1, firstJson.length() - 1);
        }
        if (secondJson.startsWith("\"") && secondJson.endsWith("\"")) {
            secondJson = secondJson.substring(1, secondJson.length() - 1);
        }

        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> firstMap = gson.fromJson(firstJson, mapType);
        Map<String, Object> secondMap = gson.fromJson(secondJson, mapType);

        MapDifference<String, Object> result = Maps.difference(firstMap, secondMap);
        if (result != null && result.entriesDiffering().isEmpty()) return true;
        log.info(String.format("difference: %s", result.entriesDiffering()));
        return false;
    }

    @Override
    protected String getApi() {
        return null;
    }

    @Override
    protected String getHttpMethod() {
        return null;
    }

    public Map<String, TestIndex> getInputIndexMap() {
        return inputIndexMap;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    protected void addShutDownHook() {
        if (!shutDownHookAdded) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::tearDown));
            shutDownHookAdded = true;
        }
    }

    // ============ REINDEX IMPLEMENTATION METHODS ============

    private String reindexTaskId;
    private int lastResponseStatusCode;
    private String lastResponseBody;
    private long documentCountBeforeReindex = 0;

    public void i_capture_document_count_before_reindex(String index) throws Throwable {
        String actualIndex = generateActualName(index, timeStamp);
        log.log(Level.INFO, "Capturing document count before reindex for index: " + actualIndex);
        try {
            // Wait for indexing to complete before capturing count
            documentCountBeforeReindex = createIndex(actualIndex);
            log.log(Level.INFO, "Document count before reindex: " + documentCountBeforeReindex);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to capture document count before reindex: " + e.getMessage());
            documentCountBeforeReindex = 0;
        }
    }

    public void i_trigger_reindex_for_kind_with_cursor(String kind, String cursor) throws Throwable {
        String actualKind = generateActualName(kind, timeStamp);
        String url = getIndexerBaseURL() + "reindex";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("kind", actualKind);
        requestBody.put("cursor", cursor);

        String jsonPayload = new Gson().toJson(requestBody);
        Map<String, String> headers = httpClient.getCommonHeader();

        log.log(Level.INFO, "Reindex URL: " + url);
        log.log(Level.INFO, "Original kind: " + kind);
        log.log(Level.INFO, "Actual kind with timestamp: " + actualKind);
        log.log(Level.INFO, "Reindex payload: " + jsonPayload);

        HttpResponse response = httpClient.send("POST", url, jsonPayload, headers, httpClient.getAccessToken());
        lastResponseStatusCode = response.getStatus();
        lastResponseBody = response.getEntity(String.class);

        log.log(Level.INFO, "Reindex response status: " + lastResponseStatusCode);
        log.log(Level.INFO, "Reindex response body: " + lastResponseBody);

        // Store task ID if present in response
        if (response.getStatus() == 200 && lastResponseBody != null && !lastResponseBody.trim().isEmpty()) {
            reindexTaskId = lastResponseBody.trim();
            log.log(Level.INFO, "Stored reindex task ID: " + reindexTaskId);
        } else {
            log.log(Level.WARNING, "No task ID received from reindex response. Status: " + lastResponseStatusCode + ", Body: " + lastResponseBody);
        }
    }

    public void i_trigger_reindex_for_kind_with_force_clean(String kind) throws Throwable {
        String actualKind = generateActualName(kind, timeStamp);
        String url = getIndexerBaseURL() + "reindex?force_clean=true";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("kind", actualKind);
        requestBody.put("cursor", "");

        String jsonPayload = new Gson().toJson(requestBody);
        Map<String, String> headers = httpClient.getCommonHeader();

        log.log(Level.INFO, "Force clean reindex URL: " + url);
        log.log(Level.INFO, "Original kind: " + kind);
        log.log(Level.INFO, "Actual kind with timestamp: " + actualKind);
        log.log(Level.INFO, "Force clean reindex payload: " + jsonPayload);

        try {
            HttpResponse response = httpClient.send("POST", url, jsonPayload, headers, httpClient.getAccessToken());
            lastResponseStatusCode = response.getStatus();
            lastResponseBody = response.getEntity(String.class);

            log.log(Level.INFO, "Force clean reindex response status: " + lastResponseStatusCode);
            log.log(Level.INFO, "Force clean reindex response body: " + lastResponseBody);

            if (response.getStatus() == 200 && lastResponseBody != null && !lastResponseBody.trim().isEmpty()) {
                reindexTaskId = lastResponseBody.trim();
                log.log(Level.INFO, "Stored force clean reindex task ID: " + reindexTaskId);
            } else {
                log.log(Level.WARNING, "No task ID received from force clean reindex response. Status: " + lastResponseStatusCode + ", Body: " + lastResponseBody);
            }
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Error during force clean reindex: " + ex.getMessage(), ex);
            throw new AssertionError(ex.getMessage());
        }
    }

    public void i_trigger_reindex_for_dynamic_record_ids() throws Throwable {
        if (ingestedRecordIds.isEmpty()) {
            throw new AssertionError("No record IDs available for reindex. Please ensure records were ingested first.");
        }

        // Use the first few record IDs for testing (limit to 2-3 to keep test manageable)
        int maxRecords = Math.min(3, ingestedRecordIds.size());
        List<String> recordIdsForReindex = ingestedRecordIds.subList(0, maxRecords);
        String recordIdsString = String.join(",", recordIdsForReindex);

        log.log(Level.INFO, "Using dynamic record IDs for reindex: " + recordIdsString);

        // Call the existing reindex method with the dynamic IDs
        i_trigger_reindex_for_record_ids(recordIdsString);
    }

    public void i_trigger_reindex_for_record_ids(String recordIds) throws Throwable {
        // Use the correct endpoint for reindexing specific record IDs
        String url = getIndexerBaseURL() + "reindex/records";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("recordIds", Arrays.asList(recordIds.split(",")));

        String jsonPayload = new Gson().toJson(requestBody);
        Map<String, String> headers = httpClient.getCommonHeader();

        log.log(Level.INFO, "Reindex records URL: " + url);
        log.log(Level.INFO, "Record IDs: " + recordIds);
        log.log(Level.INFO, "Reindex records payload: " + jsonPayload);

        try {
            HttpResponse response = httpClient.send("POST", url, jsonPayload, headers, httpClient.getAccessToken());
            lastResponseStatusCode = response.getStatus();
            lastResponseBody = response.getEntity(String.class);

            log.log(Level.INFO, "Reindex records response status: " + lastResponseStatusCode);
            log.log(Level.INFO, "Reindex records response body: " + lastResponseBody);

            // The API returns 202 (Accepted) for successful reindex requests
            if (response.getStatus() == 202) {
                reindexTaskId = "reindex-records-task-" + System.currentTimeMillis();
                log.log(Level.INFO, "Stored reindex records task ID: " + reindexTaskId);
            } else {
                log.log(Level.WARNING, "Unexpected response status for reindex records: " + lastResponseStatusCode);
            }
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Error during record IDs reindex: " + ex.getMessage(), ex);
            throw new AssertionError(ex.getMessage());
        }
    }

    public void i_trigger_reindex_for_invalid_kind(String invalidKind, String cursor) throws Throwable {
        String url = getIndexerBaseURL() + "reindex";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("kind", invalidKind);
        requestBody.put("cursor", cursor);

        String jsonPayload = new Gson().toJson(requestBody);
        Map<String, String> headers = httpClient.getCommonHeader();

        HttpResponse response = httpClient.send("POST", url, jsonPayload, headers, httpClient.getAccessToken());
        lastResponseStatusCode = response.getStatus();
        lastResponseBody = response.getEntity(String.class);

        log.log(Level.INFO, "Invalid kind reindex response status: " + lastResponseStatusCode);
        log.log(Level.INFO, "Invalid kind reindex response body: " + lastResponseBody);
    }

    public void i_should_get_successful_reindex_response() throws Throwable {
        // Accept both 200 (OK) for kind-based reindex and 202 (Accepted) for records reindex
        boolean isSuccessful = (lastResponseStatusCode == 200 || lastResponseStatusCode == 202);
        assertTrue(isSuccessful, "Expected successful reindex response (200 or 202), but got: " + lastResponseStatusCode);
        log.log(Level.INFO, "Received successful reindex response with status: " + lastResponseStatusCode);
    }

    public void i_should_verify_reindexed_documents_in_index(String index) throws Throwable {
        String actualIndex = generateActualName(index, timeStamp);
        log.log(Level.INFO, "Verifying reindexed documents in index: " + actualIndex);

        // Wait longer for reindexing to complete (reindex operations can take time)
        log.log(Level.INFO, "Waiting for reindex operation to complete...");
        TimeUnit.SECONDS.sleep(10);

        // Get document count after reindex with retry logic
        long documentCountAfterReindex = 0;
        int maxRetries = 5;
        for (int retry = 0; retry < maxRetries; retry++) {
            try {
                documentCountAfterReindex = elasticUtils.fetchRecords(actualIndex);
                log.log(Level.INFO, "Document count after reindex (attempt " + (retry + 1) + "): " + documentCountAfterReindex);
                if (documentCountAfterReindex > 0) {
                    break; // Found documents, exit retry loop
                }
                if (retry < maxRetries - 1) {
                    log.log(Level.INFO, "No documents found, waiting 40 seconds before retry...");
                    TimeUnit.SECONDS.sleep(40);
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to fetch documents from index " + actualIndex + " (attempt " + (retry + 1) + "): " + e.getMessage());
                if (retry == maxRetries - 1) {
                    throw new AssertionError("Failed to fetch documents from index after " + maxRetries + " attempts: " + e.getMessage());
                }
                TimeUnit.SECONDS.sleep(2);
            }
        }
        log.log(Level.INFO, "Final document count after reindex: " + documentCountAfterReindex);
        log.log(Level.INFO, "Document count before reindex: " + documentCountBeforeReindex);

        // Verify documents exist in the index
        if (documentCountAfterReindex == 0) {
            log.log(Level.SEVERE, "No documents found in index " + actualIndex + " after reindex");
            throw new AssertionError("No documents found in index after reindex. Index: " + actualIndex +
                                   ", Expected: " + documentCountBeforeReindex + ", Actual: " + documentCountAfterReindex);
        }

        // If we captured count before reindex, verify they match
        if (documentCountBeforeReindex > 0) {
            if (documentCountAfterReindex != documentCountBeforeReindex) {
                log.log(Level.WARNING, "Document count mismatch after reindex. Before: " + documentCountBeforeReindex +
                                     ", After: " + documentCountAfterReindex);
            }
            assertEquals(documentCountBeforeReindex, documentCountAfterReindex,
                        "Document count should match before and after reindex");
        } else {
            // Just verify documents exist
            assertTrue(documentCountAfterReindex > 0, "Expected documents in index after reindex. Index: " + actualIndex +
                      ", Document count: " + documentCountAfterReindex);
        }
        log.log(Level.INFO, "Successfully verified " + documentCountAfterReindex + " documents in index " + actualIndex);
    }

    public void i_should_get_error_response_with_status_code(int expectedStatusCode) throws Throwable {
        assertEquals(expectedStatusCode, lastResponseStatusCode, "Expected error status code");
    }

    public void i_should_get_error_message_for_invalid_kind() throws Throwable {
        log.log(Level.INFO, "Validating error message for invalid kind. Response body: " + lastResponseBody);
        log.log(Level.INFO, "Response status code: " + lastResponseStatusCode);

        assertTrue(lastResponseBody != null && !lastResponseBody.trim().isEmpty(), "Expected error response body to be present");

        String responseBodyLower = lastResponseBody.toLowerCase();
        boolean hasValidErrorMessage =
            responseBodyLower.contains("invalid") ||
            responseBodyLower.contains("bad request") ||
            responseBodyLower.contains("validation") ||
            responseBodyLower.contains("kind") ||
            responseBodyLower.contains("format") ||
            responseBodyLower.contains("error") ||
            responseBodyLower.contains("malformed") ||
            responseBodyLower.contains("schema") ||
            responseBodyLower.contains("not found") ||
            responseBodyLower.contains("400");

        assertTrue(hasValidErrorMessage, "Expected error message about invalid kind. Actual response: " + lastResponseBody);
    }

    public void i_should_get_error_message_for_parsing_error() throws Throwable {
        log.log(Level.INFO, "Validating error message for parsing error. Response body: " + lastResponseBody);
        log.log(Level.INFO, "Response status code: " + lastResponseStatusCode);

        assertTrue(lastResponseBody != null && !lastResponseBody.trim().isEmpty(), "Expected error response body to be present");

        String responseBodyLower = lastResponseBody.toLowerCase();
        boolean hasValidParsingError =
            responseBodyLower.contains("parsing") ||
            responseBodyLower.contains("malformed") ||
            responseBodyLower.contains("invalid json") ||
            responseBodyLower.contains("json") ||
            responseBodyLower.contains("syntax") ||
            responseBodyLower.contains("bad request") ||
            responseBodyLower.contains("400") ||
            responseBodyLower.contains("error");

        assertTrue(hasValidParsingError, "Expected parsing error message. Actual response: " + lastResponseBody);
    }

    public void i_should_get_successful_reindex_response_for_next_batch() throws Throwable {
        assertEquals(200, lastResponseStatusCode, "Expected successful reindex response for next batch");
        log.log(Level.INFO, "Successfully received reindex response for next batch");
    }

    public void i_should_verify_all_reindexed_documents_in_index(String index) throws Throwable {
        // This is similar to regular verification but for multi-batch scenarios
        i_should_verify_reindexed_documents_in_index(index);
        log.log(Level.INFO, "Successfully verified all reindexed documents from multiple batches in index: " + index);
    }

    // ============ ALIAS-SPECIFIC METHODS ============

    public void i_verify_alias_exists_and_points_to_physical_index(String aliasName, String physicalIndexName) throws InterruptedException {
        String actualAliasName = generateActualName(aliasName);
        String expectedPhysicalIndex = generateActualName(physicalIndexName);

        // Poll for alias to be ready and pointing to expected physical index
        PollingResult<Boolean> result = PollingUtils.pollForAliasReady(
            actualAliasName,
            expectedPhysicalIndex,
            PollingConfig.aliasCreation(),
            elasticUtils
        );

        // Assert the polling was successful
        assertTrue(result.isSuccess(),
            String.format("Alias %s should exist and point to %s. %s",
                actualAliasName, expectedPhysicalIndex, result.getFailureReason()));

        log.info("Verified alias " + actualAliasName + " exists and points to physical index " + expectedPhysicalIndex);
    }

    public void i_delete_index_via_service_endpoint(String kind) {
        String actualKind = generateActualName(kind);
        indexerClientUtil.deleteIndex(actualKind);
        log.info("Deleted index via indexer service for kind: " + actualKind);
    }

    public void i_verify_physical_index_exists(String physicalIndexName) throws InterruptedException {
        String actualPhysicalIndex = generateActualName(physicalIndexName);

        // Poll for physical index to be ready
        PollingResult<Boolean> result = PollingUtils.pollForPhysicalIndexReady(
            actualPhysicalIndex,
            PollingConfig.indexCreation(),
            elasticUtils
        );

        // Assert the polling was successful
        assertTrue(result.isSuccess(),
            String.format("Physical index %s should exist. %s",
                actualPhysicalIndex, result.getFailureReason()));

        log.info("Verified physical index exists: " + actualPhysicalIndex);
    }

    public void i_create_physical_index(String indexName) {
        String actualIndexName = generateActualName(indexName);
        // Create a simple physical index without alias for backward compatibility testing
        String mapping = "{}";  // Basic empty mapping
        elasticUtils.createIndex(actualIndexName, mapping);
        log.info("Created physical index: " + actualIndexName);
    }

    public void i_verify_physical_index_does_not_exist(String physicalIndexName) throws InterruptedException {
        String actualPhysicalIndex = generateActualName(physicalIndexName);

        // Poll to verify physical index does NOT exist (deletion has propagated)
        PollingResult<Boolean> result = PollingUtils.pollWithRetry(
            PollingConfig.indexDeletion(),
            () -> !elasticUtils.physicalIndexExists(actualPhysicalIndex),
            notExists -> notExists != null && notExists,
            null
        );

        // Assert the polling was successful (index does not exist)
        assertTrue(result.isSuccess(),
            String.format("Physical index %s should not exist. %s",
                actualPhysicalIndex, result.getFailureReason()));

        log.info("Verified physical index does not exist: " + actualPhysicalIndex);
    }

    public void i_verify_alias_does_not_exist(String aliasName) throws InterruptedException {
        String actualAliasName = generateActualName(aliasName);

        // Poll to verify alias does NOT exist (deletion has propagated)
        PollingResult<Boolean> result = PollingUtils.pollWithRetry(
            PollingConfig.aliasCreation(),
            () -> !elasticUtils.aliasExists(actualAliasName),
            notExists -> notExists != null && notExists,
            null
        );

        // Assert the polling was successful (alias does not exist)
        assertTrue(result.isSuccess(),
            String.format("Alias %s should not exist. %s",
                actualAliasName, result.getFailureReason()));

        log.info("Verified alias does not exist: " + actualAliasName);
    }

    public void i_verify_mapping_merged_in_physical_index(String physicalIndexName) throws Exception {
        String actualIndex = generateActualName(physicalIndexName);

        // Verify the physical index still exists (wasn't recreated)
        assertTrue(elasticUtils.physicalIndexExists(actualIndex),
            "Physical index should still exist after merge: " + actualIndex);

        // Poll for documents to be ready, to verify no data loss
        PollingResult<Long> result = PollingUtils.pollForDocuments(
            actualIndex,
            PollingConfig.documentPolling(),
            elasticUtils
        );

        assertTrue(result.isSuccess(),
            String.format("Documents should be available after mapping merge. %s", result.getFailureReason()));
        assertTrue(result.getValue() > 0,
            "Documents should still exist after mapping merge");

        log.info("Verified mapping merged in physical index: " + actualIndex + " with " + result.getValue() + " documents");
    }

    public void i_verify_fields_present_in_mapping(String newFields, String physicalIndexName) throws Exception {
        String actualPhysicalIndexName = generateActualName(physicalIndexName);

        // Parse comma-separated field names
        String[] fieldNames = newFields.split(",");

        // Poll for the fields to appear in the mapping (handles eventual consistency)
        PollingResult<Boolean> result = PollingUtils.pollForMappingFields(
            actualPhysicalIndexName,
            fieldNames,
            PollingConfig.mappingFieldsVerification(),
            elasticUtils
        );

        // Assert the polling was successful (all fields are present)
        assertTrue(result.isSuccess(),
            String.format("Fields '%s' should be present in mapping for index %s. %s",
                newFields, actualPhysicalIndexName, result.getFailureReason()));

        log.info("Verified fields are present in the mapping: " + newFields);
    }

    public void i_create_physical_index_with_initial_mapping(String indexName) throws Exception {
        String actualIndexName = generateActualName(indexName);

        // Create initial mapping with basic OSDU fields and original test fields
        Map<String, Object> mapping = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();

        // Add basic OSDU fields
        properties.put("id", Map.of("type", "keyword"));
        properties.put("kind", Map.of("type", "keyword"));
        properties.put("version", Map.of("type", "long"));
        properties.put("acl", Map.of("properties", Map.of(
            "viewers", Map.of("type", "keyword"),
            "owners", Map.of("type", "keyword")
        )));

        // Add original test data fields from v1 schema
        Map<String, Object> dataProperties = new HashMap<>();
        dataProperties.put("TestField1", Map.of("type", "text",
            "fields", Map.of("keyword", Map.of("type", "keyword"))));
        dataProperties.put("TestField2", Map.of("type", "integer"));
        dataProperties.put("Location", Map.of("type", "geo_point"));
        dataProperties.put("WellName", Map.of("type", "text",
            "fields", Map.of("keyword", Map.of("type", "keyword"))));
        dataProperties.put("Status", Map.of("type", "text",
            "fields", Map.of("keyword", Map.of("type", "keyword"))));
        dataProperties.put("CreatedDate", Map.of("type", "date"));
        dataProperties.put("Score", Map.of("type", "integer"));

        properties.put("data", Map.of("properties", dataProperties));

        mapping.put("properties", properties);

        // Create the physical index with this mapping
        ObjectMapper objectMapper = new ObjectMapper();
        String mappingJson = objectMapper.writeValueAsString(Map.of("mappings", mapping));
        elasticUtils.createIndex(actualIndexName, mappingJson);

        log.info("Created physical index with initial mapping: " + actualIndexName);
    }

    /**
     * Pre-creates an Elasticsearch physical index and alias simulating the brownfield
     * state that triggers Bug AB#68601.
     *
     * The key is mapping IsActive as {type: "text", copy_to: ["bagOfWords"]} where
     * bagOfWords is type "text". This reflects what releases/25.7.12 (mapBooleanToString
     * FF=OFF) actually created: boolean schema fields were indexed as ES text.
     * The AB#59574 special case in MappingCheckService preserves this text mapping
     * (schema says boolean, ES has text → no alias switch). When m25-master (FF=ON)
     * processes the record it sends a native JSON boolean; ES then tries to copy it to
     * the text-typed bagOfWords field and rejects with:
     *   "failed to parse [bagOfWords]: expected text or object, but got VALUE_BOOLEAN"
     *
     * @return the physical index name (with -r1 suffix) for cleanup registration
     */
    public String i_create_physical_index_with_brownfield_boolean_mapping(String indexName) throws Exception {
        String aliasName = generateActualName(indexName, timeStamp);
        String physicalIndexName = aliasName + "-r1";

        Map<String, Object> isActiveMapping = new HashMap<>();
        isActiveMapping.put("type", "text");
        isActiveMapping.put("copy_to", List.of("bagOfWords"));

        Map<String, Object> dataProperties = new HashMap<>();
        // Use the exact schema field name "IsActive" (capital I) — the OSDU indexer writes
        // bulk documents using the schema field name as-is (no lowercase normalization).
        dataProperties.put("IsActive", isActiveMapping);

        Map<String, Object> properties = new HashMap<>();
        properties.put("id", Map.of("type", "keyword"));
        properties.put("kind", Map.of("type", "keyword"));
        // bagOfWords must match the real OSDU index structure: type:text + fields.autocomplete:completion.
        // Plain "type:text" silently coerces booleans; the completion sub-field is what causes
        // ES to reject with VALUE_BOOLEAN when a native boolean is copied here.
        properties.put("bagOfWords", Map.of(
            "type", "text",
            "store", true,
            "fields", Map.of("autocomplete", Map.of("type", "completion"))));
        properties.put("data", Map.of("properties", dataProperties));

        Map<String, Object> mappingBody = new HashMap<>();
        mappingBody.put("mappings", Map.of("properties", properties));

        String mappingJson = new ObjectMapper().writeValueAsString(mappingBody);
        elasticUtils.createIndex(physicalIndexName, mappingJson);
        log.info("Created brownfield physical index '" + physicalIndexName
            + "' with IsActive:text+copy_to:bagOfWords mapping");

        elasticUtils.addAlias(physicalIndexName, aliasName);
        log.info("Created alias '" + aliasName + "' → '" + physicalIndexName + "'");

        return physicalIndexName;
    }

}
