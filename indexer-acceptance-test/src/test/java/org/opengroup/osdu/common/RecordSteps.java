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
import static org.opengroup.osdu.util.Config.getStorageBaseURL;
import static org.opengroup.osdu.util.HTTPClient.indentatedResponseBody;
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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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

@Log
public class RecordSteps extends TestsBase {
    private Map<String, TestIndex> inputIndexMap = new HashMap<>();
    private ObjectMapper mapper = new ObjectMapper();
    private boolean shutDownHookAdded = false;

    private String timeStamp = String.valueOf(System.currentTimeMillis() + new Random().nextInt(10000));
    private List<Map<String, Object>> records;
    private Map<String, String> headers = httpClient.getCommonHeader();
    private final Map<String, String> resolvedCollaborationHeaders = new HashMap<>();

    private UpsertRecords upsertedRecordsWithXcollab;
    public static final String DpsHeaders_COLLABORATION = "x-collaboration";
    public static final String X_COLLABORATION = "collaborationId";

    public RecordSteps(HTTPClient httpClient) {
        super(httpClient);
    }

    public RecordSteps(HTTPClient httpClient, ElasticUtils elasticUtils) {
        super(httpClient, elasticUtils);
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

            for (Map<String, Object> testRecord : records) {
                if(testRecord.containsKey("data")) {
                    Map<String, Object> data = (Map<String, Object>)testRecord.get("data");
                    if(data != null && data.size() > 0) {
                        data = replaceValues(data, timeStamp);
                        testRecord.put("data", data);
                    }
                }

                testRecord.put("kind", actualKind);
                testRecord.put("id", generateRecordId(testRecord));
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

        } catch (Exception ex) {
            throw new AssertionError(ex.getMessage());
        }
    }

    protected String generateRecordId(Map<String, Object> testRecord) {
        return generateActualId(testRecord.get("id").toString(), timeStamp, testRecord.get("kind").toString());
    }

    public void i_should_get_the_documents_for_the_in_the_Elastic_Search(int expectedCount, String index) throws Throwable {
        index = generateActualName(index, timeStamp);
        long numOfIndexedDocuments = createIndex(index);
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
        // Wait for index to be populated before searching by attribute
        index = generateActualName(index, timeStamp);
        createIndex(index);

        final List<Map<String, Object>> elasticRecordData = elasticUtils.fetchRecordsByAttribute(index, field, fieldValue);
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
        xCollab = resolveCollaborationHeader(xCollab);
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
        xcollab = resolveCollaborationHeader(xcollab);
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

        final String idx = index;
        PollingResult<SearchResponse<Record>> pollingResult = PollingUtils.pollWithRetry(
            PollingConfig.documentPolling(),
            () -> {
                try {
                    return elasticUtils.fetchRecordsByIdAndMustHaveXcollab(idx, id, collaborationId);
                } catch (Exception e) {
                    return null;
                }
            },
            resp -> resp != null && resp.hits().total().value() > 0,
            context -> {
                if (context.getAttempt() > 0 && context.getAttempt() % 3 == 0) {
                    try { elasticUtils.refreshIndex(idx); } catch (IOException e) { /* ignore */ }
                }
            }
        );
        searchResponse = pollingResult.isSuccess() ? pollingResult.getValue() : 
            elasticUtils.fetchRecordsByIdAndMustHaveXcollab(index, id, collaborationId);

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

    // Each collaboration test run needs a fresh UUID so Storage does not attach the request to stale
    // server-side WIP state from a previous run, but ingest and verification must still use the same
    // resolved header value within the scenario.
    // Examples:
    // "id=<uuid>,application=pws" -> "id=123e4567-e89b-12d3-a456-426614174000,application=pws"
    // "id=<uuid>,application=app-<timestamp>" -> "id=123e4567-e89b-12d3-a456-426614174000,application=app-1781678406809"
    private String resolveCollaborationHeader(String rawXCollab) {
        return resolvedCollaborationHeaders.computeIfAbsent(rawXCollab, key ->
                generateActualName(key, timeStamp).replace("<uuid>", UUID.randomUUID().toString()));
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
        PollingResult<Long> result = PollingUtils.pollForDocuments(index, PollingConfig.documentPolling(), elasticUtils);
        if (!result.isSuccess()) {
            fail(String.format("Index '%s' not created: %s", index, result.getFailureReason()));
        }
        return result.getValue();
    }

    private long getRecordsInIndex(String index, int expectedCount) throws InterruptedException, IOException {
        PollingResult<Long> result = PollingUtils.pollForExpectedCount(index, expectedCount, PollingConfig.documentPollingWithExpectedCount(), elasticUtils);
        if (!result.isSuccess()) {
            fail(String.format("Expected %d documents in index '%s', but polling failed: %s", expectedCount, index, result.getFailureReason()));
        }
        return result.getValue();
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

    public String generateActualName(String rawName) {
        for (Map.Entry<String, String> tenant : tenantMap.entrySet()) {
            rawName = rawName.replaceAll(tenant.getKey(), tenant.getValue());
        }
        return rawName.replaceAll("<timestamp>", timeStamp);
    }

    protected void addShutDownHook() {
        if (!shutDownHookAdded) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::tearDown));
            shutDownHookAdded = true;
        }
    }
}
