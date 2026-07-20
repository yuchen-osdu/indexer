// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.step_definitions.index.record;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.java.Log;

import org.opengroup.osdu.common.SchemaServiceRecordSteps;
import org.opengroup.osdu.util.AzureHTTPClient;
import org.opengroup.osdu.util.Config;
import org.opengroup.osdu.util.ElasticUtils;
import org.opengroup.osdu.util.HttpResponse;
import org.opengroup.osdu.util.PollingConfig;
import org.opengroup.osdu.util.PollingResult;
import org.opengroup.osdu.util.PollingUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


@Log
public class Steps extends SchemaServiceRecordSteps {

    // Track indexes with dual-write enabled for cleanup (maps kind -> target index)
    private Map<String, String> dualWriteKindMapping = new HashMap<>();

    // Stores the physical index resolved from an alias for use in subsequent steps
    private String storedPhysicalIndex;

    // Track manually created physical indexes for cleanup
    private List<String> manuallyCreatedIndexes = new java.util.ArrayList<>();

    public Steps() {
        super(new AzureHTTPClient(), new ElasticUtils());
    }

    /**
     * One-time setup before all scenarios: delete shared reindex indices to
     * ensure a clean baseline. This replaces the per-scenario
     * "Given the reindex-locks index does not exist" step, enabling scenarios
     * to run in parallel since each uses unique kind names with timestamp
     * placeholders and therefore unique lock document keys.
     */
    @BeforeAll
    public static void beforeAll() {
        ElasticUtils utils = new ElasticUtils();
        for (String index : new String[]{"reindex-locks", "reindex-task-history"}) {
            if (utils.isIndexExist(index)) {
                utils.deleteIndex(index);
                log.info("[@BeforeAll] Deleted " + index + " index for clean test baseline");
            } else {
                log.info("[@BeforeAll] " + index + " index does not exist (clean state confirmed)");
            }
        }
    }

    @Before("@snapshot-conflict")
    public void setUpSnapshotRepository() throws Exception {
        String repoName = buildSnapshotRepoName();
        try {
            snapshotRepoCreated = elasticUtils.ensureSnapshotRepository(repoName);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("SNAPSHOT_NOT_SUPPORTED:")) {
                // Azure snapshot storage is not configured on this cluster — skip gracefully.
                org.junit.jupiter.api.Assumptions.assumeTrue(false, e.getMessage());
            }
            throw e;
        }
    }

    @Before
    public void before(Scenario scenario) {
        this.scenario = scenario;
        // Clear the map for each scenario
        dualWriteKindMapping.clear();
        storedPhysicalIndex = null;
        manuallyCreatedIndexes.clear();
        lastReindexV2Response = null;
        lastReindexV2ResponseBody = null;
        lastReindexV2ResponseRawBody = null;
        lastReindexV2TaskId = null;
        lastReindexV2Kind = null;
        lastReplayV2Response = null;
        lastReplayV2ResponseBody = null;
        lastReplayV2ResponseRawBody = null;
        lastReplayV2Kind = null;
        lastReplayV2RunId = null;
        lastReplayV2CorrelationId = null;
        lastReplayV2RequestUrl = null;
        activeSnapshotRepo = null;
        activeSnapshotName = null;
        snapshotRepoCreated = false;
    }

    @After
    public void after(Scenario scenario) {
        // Clean up reindex-v2 state first (abort any in-progress reindex)
        cleanupReindexV2State();
        // Clean up replay-v2 state (abort any in-progress replay)
        cleanupReplayV2State();
        // Clean up any snapshot started during the scenario
        cleanupActiveSnapshot();
        // Clean up dual-write configurations after each scenario
        cleanupDualWriteConfigs();
        // Clean up manually created physical indexes (e.g., from alias-switch tests)
        cleanupManuallyCreatedIndexes();
    }

    @Given("^the schema is created with the following kind$")
    public void the_schema_is_created_with_the_following_kind(DataTable dataTable) {
        super.the_schema_is_created_with_the_following_kind(dataTable);
    }

    @When("^the schema is updated with the following kind$")
    public void the_schema_is_updated_with_the_following_kind(DataTable dataTable) {
        super.the_schema_is_updated_with_the_following_kind(dataTable);
    }

    @Then("^I set starting stateful scenarios$")
    public void i_set_starting_stateful_scenarios() throws Throwable {
        super.i_set_scenarios_as_stateful(true);
    }

    @Then("^I set ending stateful scenarios$")
    public void i_set_ending_stateful_scenarios() throws Throwable {
        super.i_set_scenarios_as_stateful(false);
    }

    @When("^I ingest records with the \"(.*?)\" with \"(.*?)\" for a given \"(.*?)\"$")
    public void i_ingest_records_with_the_for_a_given(String record, String dataGroup, String kind) {
        super.i_ingest_records_with_the_for_a_given(record, dataGroup, kind);
    }

    @When("^I create index with \"(.*?)\" for a given \"(.*?)\" and \"(.*?)\"$")
    public void i_create_index_with_mapping_file_for_a_given_kind(String mappingFile, String index, String kind) throws Throwable {
        super.i_create_index_with_mapping_file_for_a_given_kind(mappingFile, index, kind);
    }

    @Then("^I should get the (\\d+) documents for the \"([^\"]*)\" in the Elastic Search$")
    public void i_should_get_the_documents_for_the_in_the_Elastic_Search(int expectedCount, String index) throws Throwable {
        super.i_should_get_the_documents_for_the_in_the_Elastic_Search(expectedCount, index);
        // Resolve and store the physical index so snapshot-related steps can scope to it.
        String resolvedAlias = generateActualName(index, getTimeStamp());
        String physicalIndex = elasticUtils.getPhysicalIndexFromAlias(resolvedAlias);
        if (physicalIndex != null) {
            storedPhysicalIndex = physicalIndex;
        }
    }

    @Then("^I should not get any documents for the \"([^\"]*)\" in the Elastic Search$")
    public void i_should_not_get_any_documents_for_the_index_in_the_Elastic_Search(String index) throws Throwable {
        super.i_should_not_get_any_documents_for_the_index_in_the_Elastic_Search(index);
    }

    @Then("^I should get the elastic (.+) for the \"([^\"]*)\" and \"([^\"]*)\" in the Elastic Search$")
    public void i_should_get_the_elastic_for_the_tenant_testindex_timestamp_well_in_the_Elastic_Search(String expectedMapping, String kind, String index) throws Throwable {
        super.i_should_get_the_elastic_for_the_tenant_testindex_timestamp_well_in_the_Elastic_Search(expectedMapping, kind, index);
    }

    @Then("^I can validate indexed meta attributes for the \"([^\"]*)\" and given \"([^\"]*)\"$")
    public void i_can_validate_indexed_meta_attributes(String index, String kind) throws Throwable {
        super.i_can_validate_indexed_attributes(index, kind);
    }

    @Then("^I should get the (\\d+) documents for the \"([^\"]*)\" in the Elastic Search with out \"(.*?)\"$")
    public void iShouldGetTheNumberDocumentsForTheIndexInTheElasticSearchWithOutSkippedAttribute(int expectedCount, String index, String skippedAttributes) throws Throwable {
        super.iShouldGetTheNumberDocumentsForTheIndexInTheElasticSearchWithOutSkippedAttribute(expectedCount, index, skippedAttributes);
    }

    @Then("^I should be able to search (\\d+) record with index \"([^\"]*)\" by tag \"([^\"]*)\" and value \"([^\"]*)\"$")
    public void iShouldBeAbleToSearchRecordByTagKeyAndTagValue(int expectedNumber, String index, String tagKey, String tagValue) throws Throwable {
        super.iShouldBeAbleToSearchRecordByTagKeyAndTagValue(index, tagKey, tagValue, expectedNumber);
    }

    @Then("^I clean up the index of the extended kinds \"([^\"]*)\" in the Elastic Search$")
    public void iShouldCleanupIndicesOfExtendedKinds(String extendedKinds) throws Throwable {
        super.iShouldCleanupIndicesOfExtendedKinds(extendedKinds);
    }

    @Then("^I should be able to search (\\d+) record with index \"([^\"]*)\" by extended data field \"([^\"]*)\" and value \"([^\"]*)\"$")
    public void iShouldBeAbleToSearchRecordByFieldAndFieldValue(int expectedNumber, String index, String fieldKey, String fieldValue) throws Throwable {
        super.iShouldBeAbleToSearchRecordByFieldAndFieldValue(index, fieldKey, fieldValue, expectedNumber);
    }

    @Then("^the field \"(.*?)\" in index \"(.*?)\" should have ES field type \"(.*?)\"$")
    public void theFieldInIndexShouldHaveESFieldType(String fieldPath, String index, String expectedType) {
        super.verifyElasticFieldType(index, fieldPath, expectedType);
    }

    @Then("^I should be able to find (\\d+) record with index \"([^\"]*)\" by boolean field \"([^\"]*)\" with value (true|false)$")
    public void iShouldBeAbleToFindRecordsByBooleanFieldValue(int expectedNumber, String index, String fieldKey, boolean booleanValue) {
        super.iShouldBeAbleToFindRecordsByBooleanFieldValue(index, fieldKey, booleanValue, expectedNumber);
    }

    @Then("^I should be able search (\\d+) documents for the \"([^\"]*)\" by bounding box query with points \\((-?\\d+), (-?\\d+)\\) and  \\((-?\\d+), (-?\\d+)\\) on field \"([^\"]*)\"$")
    public void i_should_get_the_documents_for_the_in_the_Elastic_Search_by_geoQuery(
            int expectedCount, String index, Double topLatitude, Double topLongitude, Double bottomLatitude, Double bottomLongitude, String field) throws Throwable {
        super.i_should_get_the_documents_for_the_in_the_Elastic_Search_by_geoQuery(expectedCount, index, topLatitude, topLongitude, bottomLatitude, bottomLongitude, field);
    }

    @Then("^I should be able search (\\d+) documents for the \"([^\"]*)\" by bounding box query with points \\((-?[\\d.]+), (-?[\\d.]+)\\) on field \"([^\"]*)\" and points \\((-?[\\d.]+), (-?[\\d.]+)\\) on field \"([^\"]*)\"$")
    public void i_should_get_the_documents_for_the_in_the_Elastic_Search_by_AsIngestedCoordinates(
            int expectedCount, String index, Double topPointX, Double bottomPointX, String pointX, Double topPointY, Double bottomPointY, String pointY) throws Throwable {
        super.i_should_get_the_documents_for_the_in_the_Elastic_Search_by_AsIngestedCoordinates(expectedCount, index, topPointX, bottomPointX, pointX, topPointY, bottomPointY, pointY);
    }

    @Then("^I should be able search (\\d+) documents for the \"([^\"]*)\" by nested \"([^\"]*)\" and properties \\(\"([^\"]*)\", (\\d+)\\) and  \\(\"([^\"]*)\", \"([^\"]*)\"\\)$")
    public void i_should_get_the_documents_for_the_in_the_Elastic_Search_by_nestedQuery(
        int expectedCount, String index, String path, String firstNestedProperty, String firstNestedValue, String secondNestedProperty,
        String secondNestedValue) throws Throwable {
        super.i_should_get_the_documents_for_the_in_the_Elastic_Search_by_nestedQuery(expectedCount, index, path, firstNestedProperty, firstNestedValue,
            secondNestedProperty, secondNestedValue);
    }

    @Then("^I should be able search (\\d+) documents for the \"([^\"]*)\" by flattened inner properties \\(\"([^\"]*)\", \"([^\"]*)\"\\)$")
    public void i_should_be_able_search_documents_for_the_by_flattened_inner_properties(int expectedCount, String index, String flattenedField,
        String flattenedFieldValue) throws Throwable {
        super.i_should_be_able_search_documents_for_the_by_flattened_inner_properties(expectedCount, index, flattenedField, flattenedFieldValue);

    }

    @Then("^I should get \"([^\"]*)\" in search response for the \"([^\"]*)\"$")
    public void i_should_get_object_in_search_response(String innerField, String index) throws Throwable {
        super.i_should_get_object_in_search_response(innerField, index);
    }

    @Then("^I should get \"([^\"]*)\" in response, without hints in schema for the \"([^\"]*)\" that present in the \"([^\"]*)\" with \"([^\"]*)\" for a given \"([^\"]*)\"$")
    public void i_should_get_object_in_search_response_without_hints_in_schema(String objectInnerField, String index, String recordFile, String acl, String kind)
        throws Throwable {
        super.i_should_get_object_in_search_response_without_hints_in_schema(objectInnerField ,index, recordFile, acl, kind);
    }

    @Then("^I should be able to search for record from \"([^\"]*)\" by \"([^\"]*)\" for value \"([^\"]*)\" and find String arrays in \"([^\"]*)\" with \"([^\"]*)\"$")
    public void i_should_get_string_array_in_search_response(String index, String field, String fieldValue, String arrayField, String arrayValue)
            throws Throwable {
        super.i_should_get_string_array_in_search_response(index, field, fieldValue, arrayField, arrayValue);
    }

    // ============ REINDEX STEP DEFINITIONS ============

    @Given("^I have ingested records with the \"([^\"]*)\" with \"([^\"]*)\" for a given \"([^\"]*)\"$")
    public void iHaveIngestedRecordsWithTheWithForAGiven(String recordFile, String acl, String kind) {
        super.i_ingest_records_with_the_for_a_given(recordFile, acl, kind);
    }

    @When("^I trigger reindex for the \"([^\"]*)\" with cursor \"([^\"]*)\"$")
    public void iTriggerReindexForTheWithCursor(String kind, String cursor) throws Throwable {
        super.i_trigger_reindex_for_kind_with_cursor(kind, cursor);
    }

    @When("^I capture document count before reindex for \"([^\"]*)\"$")
    public void iCaptureDocumentCountBeforeReindex(String index) throws Throwable {
        super.i_capture_document_count_before_reindex(index);
    }

    @When("^I trigger reindex for the \"([^\"]*)\" with force_clean enabled$")
    public void iTriggerReindexForTheWithForceCleanEnabled(String kind) throws Throwable {
        super.i_trigger_reindex_for_kind_with_force_clean(kind);
    }

    @When("^I trigger reindex for specific record IDs \"([^\"]*)\"$")
    public void iTriggerReindexForSpecificRecordIDs(String recordIds) throws Throwable {
        super.i_trigger_reindex_for_record_ids(recordIds);
    }

    @When("^I trigger reindex for invalid \"([^\"]*)\" with cursor \"([^\"]*)\"$")
    public void iTriggerReindexForInvalidWithCursor(String invalidKind, String cursor) throws Throwable {
        super.i_trigger_reindex_for_invalid_kind(invalidKind, cursor);
    }

    @Then("^I should get successful reindex response with task ID$")
    public void iShouldGetSuccessfulReindexResponseWithTaskID() throws Throwable {
        super.i_should_get_successful_reindex_response();
    }

    @Then("^I should get successful reindex response for next batch$")
    public void iShouldGetSuccessfulReindexResponseForNextBatch() throws Throwable {
        super.i_should_get_successful_reindex_response_for_next_batch();
    }

    @Then("^I should get successful reindex response$")
    public void iShouldGetSuccessfulReindexResponse() throws Throwable {
        super.i_should_get_successful_reindex_response();
    }

    @Then("^I should verify reindexed documents are present in the \"([^\"]*)\" in Elastic Search$")
    public void iShouldVerifyReindexedDocumentsArePresentInTheInElasticSearch(String index) throws Throwable {
        super.i_should_verify_reindexed_documents_in_index(index);
    }

    @Then("^I should verify all reindexed documents are present in the \"([^\"]*)\" in Elastic Search$")
    public void iShouldVerifyAllReindexedDocumentsArePresentInTheInElasticSearch(String index) throws Throwable {
        super.i_should_verify_all_reindexed_documents_in_index(index);
    }

    @Then("^I should verify the specific records are reindexed in the \"([^\"]*)\" in Elastic Search$")
    public void iShouldVerifyTheSpecificRecordsAreReindexedInTheInElasticSearch(String index) throws Throwable {
        super.i_should_verify_reindexed_documents_in_index(index);
    }

    @Then("^I should get error response with status code (\\d+)$")
    public void iShouldGetErrorResponseWithStatusCode(int statusCode) throws Throwable {
        super.i_should_get_error_response_with_status_code(statusCode);
    }

    @Then("^the error message should indicate invalid kind format$")
    public void theErrorMessageShouldIndicateInvalidKindFormat() throws Throwable {
        super.i_should_get_error_message_for_invalid_kind();
    }

    @Then("^the error message should indicate request parsing error$")
    public void theErrorMessageShouldIndicateRequestParsingError() throws Throwable {
        super.i_should_get_error_message_for_parsing_error();
    }

    @Then("^all records should be successfully reindexed in the \"([^\"]*)\"$")
    public void allRecordsShouldBeSuccessfullyReindexedInThe(String index) throws Throwable {
        super.i_should_verify_reindexed_documents_in_index(index);
    }

    @When("I trigger reindex for dynamic record IDs")
    public void iTriggerReindexForDynamicRecordIDs() throws Throwable {
        super.i_trigger_reindex_for_dynamic_record_ids();
    }

    // ============ ALIAS-SPECIFIC STEP DEFINITIONS (CONSOLIDATED) ============

    @And("^I verify in Elasticsearch that alias \"([^\"]*)\" exists and points to physical index \"([^\"]*)\"$")
    public void verifyAliasExistsAndPointsToPhysicalIndex(String aliasName, String physicalIndexName) throws InterruptedException {
        super.i_verify_alias_exists_and_points_to_physical_index(aliasName, physicalIndexName);
    }

    @When("^I delete the index using indexer service endpoint for kind \"([^\"]*)\"$")
    public void deleteIndexViaServiceEndpoint(String kind) {
        super.i_delete_index_via_service_endpoint(kind);
    }

    @And("^I verify in Elasticsearch that physical index \"([^\"]*)\" exists$")
    public void verifyPhysicalIndexExists(String physicalIndexName) throws InterruptedException {
        super.i_verify_physical_index_exists(physicalIndexName);
    }

    @Given("^I manually create a physical index \"([^\"]*)\" in Elasticsearch$")
    public void createPhysicalIndex(String indexName) {
        super.i_create_physical_index(indexName);
        String resolvedName = generateActualName(indexName, getTimeStamp());
        manuallyCreatedIndexes.add(resolvedName);
    }

    @Then("^I verify in Elasticsearch that physical index \"([^\"]*)\" does not exist$")
    public void verifyPhysicalIndexDoesNotExist(String physicalIndexName) throws InterruptedException {
        super.i_verify_physical_index_does_not_exist(physicalIndexName);
    }

    @And("^I verify in Elasticsearch that alias \"([^\"]*)\" does not exist$")
    public void verifyAliasDoesNotExist(String aliasName) throws InterruptedException {
        super.i_verify_alias_does_not_exist(aliasName);
    }

    // ============ SCHEMA MERGE STEP DEFINITIONS ============

    @Then("^I verify the mapping is merged in physical index \"([^\"]*)\"$")
    public void verifyMappingMergedInPhysicalIndex(String physicalIndexName) throws Exception {
        super.i_verify_mapping_merged_in_physical_index(physicalIndexName);
    }

    @Then("I verify fields {string} are present in the mapping for physical index {string}")
    public void verifyNewFieldsPresentInMapping(String newFields, String physicalIndexName) throws Exception {
        super.i_verify_fields_present_in_mapping(newFields, physicalIndexName);
    }

    @Given("^I manually create a physical index \"([^\"]*)\" in Elasticsearch with initial mapping$")
    public void createPhysicalIndexWithInitialMapping(String indexName) throws Exception {
        super.i_create_physical_index_with_initial_mapping(indexName);
    }

    /**
     * Brownfield simulation for AB#68601: pre-creates an ES index with the legacy
     * text+copy_to:bagOfWords mapping for IsActive. Without the fix, m25-master sends
     * a raw Java boolean which ES rejects with VALUE_BOOLEAN on this mapping.
     */
    @Given("^I pre-create ES index \"([^\"]*)\" with brownfield boolean mapping in Elasticsearch$")
    public void createPhysicalIndexWithBrownfieldBooleanMapping(String indexName) throws Exception {
        String physicalIndexName = super.i_create_physical_index_with_brownfield_boolean_mapping(indexName);
        manuallyCreatedIndexes.add(physicalIndexName);
    }

    // ============ DUAL-WRITE STEP DEFINITIONS ============

    private static final String DUAL_WRITE_CONFIG_API = "/api/indexer/v2/dual-write/config";

    private static final String REINDEX_V2_API = "/api/indexer/v2/reindex-v2";
    private static final String REPLAY_V2_API = "/api/indexer/v2/replay-v2";

    // Stores reindex-v2 response state for multi-step scenarios
    private HttpResponse lastReindexV2Response;
    private Map<String, Object> lastReindexV2ResponseBody;
    private String lastReindexV2ResponseRawBody;
    private String lastReindexV2TaskId;
    private String lastReindexV2Kind;

    // Stores replay-v2 response state for multi-step scenarios
    private HttpResponse lastReplayV2Response;
    private Map<String, Object> lastReplayV2ResponseBody;
    private String lastReplayV2ResponseRawBody;
    private String lastReplayV2Kind;
    private String lastReplayV2RunId;
    private String lastReplayV2CorrelationId;
    private String lastReplayV2RequestUrl;

    // Tracks the snapshot repo and name started during a scenario for cleanup
    private String activeSnapshotRepo;
    private String activeSnapshotName;
    // True if this test run created the snapshot repository (so we delete it on teardown)
    private boolean snapshotRepoCreated;


    // Stores the last retrieved dual-write configuration for validation
    private Map<String, Object> lastDualWriteConfigResponse;

    /**
     * Enables dual-write for a specific kind by calling the Indexer Management API.
     * R2 index is always created automatically when enabling dual-write.
     */
    @Given("^I enable dual-write for kind \"([^\"]*)\" with target \"([^\"]*)\"$")
    public void enableDualWrite(String kind, String targetIndex) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String resolvedTarget = generateActualName(targetIndex, getTimeStamp());

        log.info(String.format("Enabling dual-write: kind=%s, target=%s", resolvedKind, resolvedTarget));

        // Build request body for Management API
        Map<String, Object> body = new HashMap<>();
        body.put("kind", resolvedKind);
        body.put("targetIndex", resolvedTarget);

        Map<String, String> headers = getDualWriteApiHeaders();
        String url = getIndexerBaseUrl() + DUAL_WRITE_CONFIG_API;
        String payload = new Gson().toJson(body);

        HttpResponse response = httpClient.send(
            "POST",
            url,
            payload,
            headers,
            httpClient.getAccessToken()
        );

        log.info(String.format("Dual-write enable response: %d - %s", response.getStatus(), response.getEntity(String.class)));
        assertTrue(response.getStatus() >= 200 && response.getStatus() < 300,
            "Failed to enable dual-write via Management API: " + response.getStatus() + " - " + response.getEntity(String.class));

        // Track this kind and its target for cleanup after the scenario
        dualWriteKindMapping.put(resolvedKind, resolvedTarget);
    }

    /**
     * Explicitly disables dual-write for a kind.
     * Uses the Indexer Management API which proxies to the partition service.
     */
    @Given("^dual-write is NOT enabled for kind \"([^\"]*)\"$")
    public void dualWriteNotEnabled(String kind) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());

        log.info(String.format("Disabling dual-write for kind: %s", resolvedKind));

        Map<String, String> headers = getDualWriteApiHeaders();
        String url = getIndexerBaseUrl() + DUAL_WRITE_CONFIG_API + "/" + resolvedKind;

        HttpResponse response = httpClient.send(
            "DELETE",
            url,
            null,
            headers,
            httpClient.getAccessToken()
        );

        log.info(String.format("Dual-write disable response: %d", response.getStatus()));
        assertTrue(response.getStatus() >= 200 && response.getStatus() < 300,
            "Failed to disable dual-write via Management API: " + response.getStatus());
    }

    /**
     * Compares documents between two indexes to verify they match by count.
     */
    @And("^documents in \"([^\"]*)\" should match documents in \"([^\"]*)\"$")
    public void compareDocumentsInIndexes(String index1, String index2) throws Exception {
        String resolvedIndex1 = generateActualName(index1, getTimeStamp());
        String resolvedIndex2 = generateActualName(index2, getTimeStamp());

        log.info(String.format("Comparing documents between %s and %s", resolvedIndex1, resolvedIndex2));

        PollingResult<Long> result = PollingUtils.pollForMatchingDocumentCount(
            resolvedIndex1,
            resolvedIndex2,
            PollingConfig.dualWritePolling(),
            elasticUtils
        );

        assertTrue(result.isSuccess(),
            String.format("Document count mismatch between %s and %s after %.1f seconds. %s",
                resolvedIndex1, resolvedIndex2,
                result.getTotalWaitTimeSeconds(),
                result.getFailureReason() != null ? result.getFailureReason() : ""));

        log.info(String.format("Documents match: %d documents in both indexes after %.1f seconds",
            result.getValue(), result.getTotalWaitTimeSeconds()));
    }

    /**
     * Verifies a document with a specific field value exists in an index.
     */
    @Then("^the document with field \"([^\"]*)\" value \"([^\"]*)\" should exist in \"([^\"]*)\"$")
    public void verifyDocumentWithFieldValue(String fieldPath, String expectedValue, String index) throws Exception {
        String resolvedIndex = generateActualName(index, getTimeStamp());

        log.info(String.format("Verifying document with %s=%s exists in %s",
            fieldPath, expectedValue, resolvedIndex));

        PollingResult<List<Map<String, Object>>> result = PollingUtils.pollForDocumentByAttribute(
            resolvedIndex,
            fieldPath,
            expectedValue,
            PollingConfig.dualWritePolling(),
            elasticUtils
        );

        assertTrue(result.isSuccess(),
            String.format("Document with %s=%s not found in %s after %.1f seconds. %s",
                fieldPath, expectedValue, resolvedIndex,
                result.getTotalWaitTimeSeconds(),
                result.getFailureReason() != null ? result.getFailureReason() : ""));

        log.info(String.format("Found %d documents with %s=%s after %.1f seconds",
            result.getValue().size(), fieldPath, expectedValue, result.getTotalWaitTimeSeconds()));
    }

    /**
     * Deletes all ingested records.
     * Note: Verification of deletion should be done in subsequent steps using proper polling.
     */
    @When("^I delete the ingested records$")
    public void deleteIngestedRecords() throws Exception {
        log.info("Deleting ingested records via Storage API");
        super.cleanupRecords();
        // No sleep needed - subsequent verification steps use PollingUtils for proper retry logic
    }

    /**
     * Updates records with new data.
     * Note: Verification of update should be done in subsequent steps using proper polling.
     */
    @When("^I update records with the \"([^\"]*)\" with \"([^\"]*)\" for a given \"([^\"]*)\"$")
    public void updateRecords(String recordFile, String acl, String kind) throws Exception {
        log.info(String.format("Updating records from file: %s for kind: %s", recordFile, kind));
        super.i_ingest_records_with_the_for_a_given(recordFile, acl, kind);
        // No sleep needed - subsequent verification steps use PollingUtils for proper retry logic
    }

    /**
     * Retrieves dual-write configuration for a specific kind via GET endpoint.
     */
    @When("^I get dual-write configuration for kind \"([^\"]*)\"$")
    public void getDualWriteConfiguration(String kind) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());

        log.info(String.format("Getting dual-write configuration for kind: %s", resolvedKind));

        Map<String, String> headers = getDualWriteApiHeaders();
        String url = getIndexerBaseUrl() + DUAL_WRITE_CONFIG_API + "/" + resolvedKind;

        HttpResponse response = httpClient.send(
            "GET",
            url,
            null,
            headers,
            httpClient.getAccessToken()
        );

        log.info(String.format("Dual-write GET response: %d - %s", response.getStatus(), response.getEntity(String.class)));
        assertTrue(response.getStatus() >= 200 && response.getStatus() < 300,
            "Failed to get dual-write config via Management API: " + response.getStatus() + " - " + response.getEntity(String.class));

        // Parse and store the response for subsequent validation steps
        String responseBody = response.getEntity(String.class);
        lastDualWriteConfigResponse = new Gson().fromJson(responseBody,
            new TypeToken<Map<String, Object>>(){}.getType());
        log.info(String.format("Parsed dual-write config: %s", lastDualWriteConfigResponse));
    }

    /**
     * Validates that the dual-write configuration has the expected 'enabled' value.
     */
    @Then("^the dual-write configuration should have enabled \"([^\"]*)\"$")
    public void validateDualWriteEnabled(String expectedEnabled) {
        assertNotNull(lastDualWriteConfigResponse, "No dual-write configuration response available. Call GET endpoint first.");

        Object enabledValue = lastDualWriteConfigResponse.get("enabled");
        assertNotNull(enabledValue, "The 'enabled' field is missing from the response");

        boolean expected = Boolean.parseBoolean(expectedEnabled);
        boolean actual = enabledValue instanceof Boolean ? (Boolean) enabledValue : Boolean.parseBoolean(enabledValue.toString());

        assertEquals(expected, actual,
            String.format("Expected enabled=%s but got enabled=%s", expected, actual));
        log.info(String.format("Validated dual-write enabled=%s", actual));
    }

    /**
     * Validates that the dual-write configuration has the expected 'targetIndex' value.
     */
    @Then("^the dual-write configuration should have targetIndex \"([^\"]*)\"$")
    public void validateDualWriteTargetIndex(String expectedTargetIndex) {
        assertNotNull(lastDualWriteConfigResponse, "No dual-write configuration response available. Call GET endpoint first.");

        String resolvedExpectedTarget = generateActualName(expectedTargetIndex, getTimeStamp());
        Object targetIndexValue = lastDualWriteConfigResponse.get("targetIndex");

        // targetIndex may be null if dual-write is disabled
        if (resolvedExpectedTarget != null && !resolvedExpectedTarget.isEmpty()) {
            assertNotNull(targetIndexValue, "The 'targetIndex' field is missing from the response");
            assertEquals(resolvedExpectedTarget, targetIndexValue.toString(),
                String.format("Expected targetIndex=%s but got targetIndex=%s", resolvedExpectedTarget, targetIndexValue));
        }
        log.info(String.format("Validated dual-write targetIndex=%s", targetIndexValue));
    }

    // ============ ALIAS VALIDATION STEPS ============

    // Stores the last API response for validation in subsequent steps
    private HttpResponse lastApiResponse;

    /**
     * Verifies that the given alias exists and resolves the physical index it points to.
     * Stores the physical index name for use in subsequent steps.
     */
    @Given("^I verify the alias \"([^\"]*)\" exists and points to a physical index$")
    public void verifyAliasExistsAndStorePhysicalIndex(String aliasName) {
        String resolvedAlias = generateActualName(aliasName, getTimeStamp());

        log.info(String.format("Verifying alias exists: %s", resolvedAlias));

        String physicalIndex = elasticUtils.getPhysicalIndexFromAlias(resolvedAlias);
        assertNotNull(physicalIndex,
            String.format("Alias '%s' does not exist or does not point to a physical index", resolvedAlias));

        log.info(String.format("Alias '%s' points to physical index: %s", resolvedAlias, physicalIndex));

        // Store the physical index for subsequent steps (e.g., alias-switch V3 test)
        storedPhysicalIndex = physicalIndex;
        scenario.log(String.format("Alias physical index: %s", physicalIndex));
    }

    /**
     * Adds an extra alias to the stored physical index.
     * Used to simulate kind aliases that exist alongside the primary alias.
     */
    @And("^I add extra alias \"([^\"]*)\" to the current physical index$")
    public void addExtraAliasToCurrentPhysicalIndex(String aliasName) {
        assertNotNull(storedPhysicalIndex,
            "No stored physical index. Call 'I verify the alias exists and points to a physical index' first.");
        String resolvedAlias = generateActualName(aliasName, getTimeStamp());

        log.info(String.format("Adding extra alias '%s' to physical index '%s'", resolvedAlias, storedPhysicalIndex));
        boolean added = elasticUtils.addAlias(storedPhysicalIndex, resolvedAlias);
        assertTrue(added,
            String.format("Failed to add alias '%s' to physical index '%s'", resolvedAlias, storedPhysicalIndex));
    }

    /**
     * Validates that the alias-switch response movedAliases field contains the specified extra aliases.
     */
    @Then("^the alias switch response shows movedAliases containing \"([^\"]*)\" and \"([^\"]*)\"$")
    public void validateMovedAliasesContains(String alias1, String alias2) {
        assertNotNull(lastApiResponse, "No API response available.");
        String responseBody = lastApiResponse.getEntity(String.class);
        Map<String, Object> response = new Gson().fromJson(responseBody,
            new TypeToken<Map<String, Object>>(){}.getType());

        assertNotNull(response.get("movedAliases"),
            "Response missing 'movedAliases' field. Response: " + responseBody);

        @SuppressWarnings("unchecked")
        java.util.List<String> movedAliases = (java.util.List<String>) response.get("movedAliases");

        String resolvedAlias1 = generateActualName(alias1, getTimeStamp());
        String resolvedAlias2 = generateActualName(alias2, getTimeStamp());

        assertTrue(movedAliases.contains(resolvedAlias1),
            String.format("movedAliases does not contain '%s'. Actual: %s", resolvedAlias1, movedAliases));
        assertTrue(movedAliases.contains(resolvedAlias2),
            String.format("movedAliases does not contain '%s'. Actual: %s", resolvedAlias2, movedAliases));

        log.info(String.format("Verified movedAliases contains '%s' and '%s'. All moved: %s",
            resolvedAlias1, resolvedAlias2, movedAliases));
    }

    /**
     * Verifies that the previous physical index (stored from earlier step) has no aliases remaining.
     */
    @Then("^the previous physical index has no aliases remaining$")
    public void verifyPreviousIndexHasNoAliases() {
        assertNotNull(storedPhysicalIndex,
            "No stored physical index. Call 'I verify the alias exists and points to a physical index' first.");

        java.util.List<String> aliases = elasticUtils.getAliasesOnIndex(storedPhysicalIndex);
        assertTrue(aliases.isEmpty(),
            String.format("Previous physical index '%s' still has aliases: %s", storedPhysicalIndex, aliases));

        log.info(String.format("Verified previous physical index '%s' has no aliases remaining", storedPhysicalIndex));
    }

    /**
     * Attempts to enable dual-write with a target index that equals the current alias target.
     * This should fail with a 400 Bad Request error due to alias validation.
     */
    @When("^I try to enable dual-write for kind \"([^\"]*)\" with target equal to alias target$")
    public void tryEnableDualWriteWithTargetEqualToAliasTarget(String kind) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        // Convert kind to index name for ES alias lookup
        String resolvedIndex = resolvedKind.replace(":", "-");

        // Get the current physical index that the alias points to
        String physicalIndex = elasticUtils.getPhysicalIndexFromAlias(resolvedIndex);
        assertNotNull(physicalIndex,
            String.format("Cannot determine alias target for index '%s'", resolvedIndex));

        log.info(String.format("Attempting to enable dual-write for kind '%s' with target '%s' (same as alias target)",
            resolvedKind, physicalIndex));

        Map<String, String> headers = getDualWriteApiHeaders();
        // Use the same endpoint as enableDualWriteWithAutoCreate - POST to /api/indexer/v2/dual-write/config
        String url = getIndexerBaseUrl() + DUAL_WRITE_CONFIG_API;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("kind", resolvedKind);
        requestBody.put("targetIndex", physicalIndex);

        String jsonBody = new Gson().toJson(requestBody);

        lastApiResponse = httpClient.send(
            "POST",
            url,
            jsonBody,
            headers,
            httpClient.getAccessToken()
        );

        log.info(String.format("Dual-write enable attempt response: %d - %s",
            lastApiResponse.getStatus(), lastApiResponse.getEntity(String.class)));
    }

    /**
     * Attempts to enable dual-write with a specific target index.
     * Stores the response for validation in subsequent steps.
     */
    @When("^I try to enable dual-write for kind \"([^\"]*)\" with target \"([^\"]*)\"$")
    public void tryEnableDualWriteWithSpecificTarget(String kind, String targetIndex) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String resolvedTarget = generateActualName(targetIndex, getTimeStamp());

        log.info(String.format("Attempting to enable dual-write for kind '%s' with target '%s'",
            resolvedKind, resolvedTarget));

        Map<String, String> headers = getDualWriteApiHeaders();
        // Use the same endpoint as enableDualWrite - POST to /api/indexer/v2/dual-write/config
        String url = getIndexerBaseUrl() + DUAL_WRITE_CONFIG_API;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("kind", resolvedKind);
        requestBody.put("targetIndex", resolvedTarget);

        String jsonBody = new Gson().toJson(requestBody);

        lastApiResponse = httpClient.send(
            "POST",
            url,
            jsonBody,
            headers,
            httpClient.getAccessToken()
        );

        log.info(String.format("Dual-write enable attempt response: %d - %s",
            lastApiResponse.getStatus(), lastApiResponse.getEntity(String.class)));
    }

    /**
     * Validates that the last API response has the expected HTTP status code.
     */
    @Then("^the API should return status code (\\d+)$")
    public void validateApiStatusCode(int expectedStatusCode) {
        assertNotNull(lastApiResponse, "No API response available. Make sure to call an API endpoint first.");

        assertEquals(expectedStatusCode, lastApiResponse.getStatus(),
            String.format("Expected status code %d but got %d. Response: %s",
                expectedStatusCode, lastApiResponse.getStatus(), lastApiResponse.getEntity(String.class)));

        log.info(String.format("Validated API response status code: %d", expectedStatusCode));
    }

    /**
     * Validates that the last API response body contains the expected text.
     */
    @And("^the error message should contain \"([^\"]*)\"$")
    public void validateErrorMessageContains(String expectedText) {
        assertNotNull(lastApiResponse, "No API response available. Make sure to call an API endpoint first.");

        String responseBody = lastApiResponse.getEntity(String.class);
        assertNotNull(responseBody, "Response body is null");

        assertTrue(responseBody.contains(expectedText),
            String.format("Expected response to contain '%s' but got: %s", expectedText, responseBody));

        log.info(String.format("Validated error message contains: '%s'", expectedText));
    }

    // ============ DUAL-WRITE HELPER METHODS ============

    /**
     * Returns headers required for the Indexer Management API.
     */
    private Map<String, String> getDualWriteApiHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("data-partition-id", Config.getDataPartitionIdTenant1());
        headers.put("Content-Type", "application/json");
        return headers;
    }

    /**
     * Returns the base URL for the Indexer service.
     */
    private String getIndexerBaseUrl() {
        String indexerHost = Config.getIndexerBaseURL();
        if (indexerHost != null && indexerHost.endsWith("/")) {
            indexerHost = indexerHost.substring(0, indexerHost.length() - 1);
        }
        // Strip any existing API path to get the base host
        if (indexerHost != null && indexerHost.contains("/api/")) {
            indexerHost = indexerHost.substring(0, indexerHost.indexOf("/api/"));
        }
        return indexerHost;
    }

    /**
     * Cleans up dual-write configurations and target indexes for all tracked kinds.
     * Called after each scenario to ensure no stale configurations or orphaned indexes remain.
     */
    private void cleanupDualWriteConfigs() {
        if (dualWriteKindMapping.isEmpty()) {
            return;
        }

        log.info(String.format("Cleaning up dual-write configs and target indexes for %d kinds", dualWriteKindMapping.size()));

        for (Map.Entry<String, String> entry : dualWriteKindMapping.entrySet()) {
            String kind = entry.getKey();
            String targetIndex = entry.getValue();

            // First, disable the dual-write configuration
            try {
                Map<String, String> headers = getDualWriteApiHeaders();
                String url = getIndexerBaseUrl() + DUAL_WRITE_CONFIG_API + "/" + kind;

                HttpResponse response = httpClient.send(
                    "DELETE",
                    url,
                    null,
                    headers,
                    httpClient.getAccessToken()
                );

                log.info(String.format("Dual-write config cleanup for kind %s: status %d", kind, response.getStatus()));
            } catch (Exception e) {
                log.warning(String.format("Failed to cleanup dual-write config for kind %s: %s", kind, e.getMessage()));
            }

            // Then, delete the target (-r2) index directly from Elasticsearch
            // This is necessary because the indexer service's deleteIndex only deletes
            // indexes pointed to by the alias, not the dual-write target index
            try {
                if (targetIndex != null && elasticUtils.isIndexExist(targetIndex)) {
                    elasticUtils.deleteIndex(targetIndex);
                    log.info(String.format("Deleted dual-write target index %s from Elasticsearch", targetIndex));
                }
            } catch (Exception e) {
                log.warning(String.format("Failed to delete dual-write target index %s: %s", targetIndex, e.getMessage()));
            }
        }

        dualWriteKindMapping.clear();
    }

    // ============ ALIAS-SWITCH STEP DEFINITIONS ============

    private static final String ALIAS_SWITCH_API = "/api/indexer/v2/index-management/alias-switch";

    /**
     * Sends a POST request to the alias-switch endpoint.
     */
    @When("^I switch alias for kind \"([^\"]*)\" to target \"([^\"]*)\"$")
    public void switchAlias(String kind, String targetIndex) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String resolvedTarget = generateActualName(targetIndex, getTimeStamp());

        log.info(String.format("Switching alias for kind '%s' to target '%s'", resolvedKind, resolvedTarget));

        Map<String, String> headers = getDualWriteApiHeaders();
        String url = getIndexerBaseUrl() + ALIAS_SWITCH_API;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("kind", resolvedKind);
        requestBody.put("targetIndex", resolvedTarget);

        String jsonBody = new Gson().toJson(requestBody);

        lastApiResponse = httpClient.send(
            "POST",
            url,
            jsonBody,
            headers,
            httpClient.getAccessToken()
        );

        log.info(String.format("Alias switch response: %d - %s",
            lastApiResponse.getStatus(), lastApiResponse.getEntity(String.class)));
    }

    /**
     * Sends a POST request to alias-switch using the stored physical index as target (V3 test).
     */
    @When("^I switch alias for kind \"([^\"]*)\" to current alias target$")
    public void switchAliasToCurrentTarget(String kind) throws Exception {
        assertNotNull(storedPhysicalIndex,
            "No stored physical index. Call 'I verify the alias exists and points to a physical index' first.");

        String resolvedKind = generateActualName(kind, getTimeStamp());

        log.info(String.format("Switching alias for kind '%s' to current target '%s' (should be rejected)",
            resolvedKind, storedPhysicalIndex));

        Map<String, String> headers = getDualWriteApiHeaders();
        String url = getIndexerBaseUrl() + ALIAS_SWITCH_API;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("kind", resolvedKind);
        requestBody.put("targetIndex", storedPhysicalIndex);

        String jsonBody = new Gson().toJson(requestBody);

        lastApiResponse = httpClient.send(
            "POST",
            url,
            jsonBody,
            headers,
            httpClient.getAccessToken()
        );

        log.info(String.format("Alias switch response: %d - %s",
            lastApiResponse.getStatus(), lastApiResponse.getEntity(String.class)));
    }

    /**
     * Validates that the alias-switch response contains previousTarget and newTarget fields.
     */
    @Then("^the alias switch response shows previousTarget and newTarget$")
    public void validateAliasSwitchResponseFields() {
        assertNotNull(lastApiResponse, "No API response available.");

        String responseBody = lastApiResponse.getEntity(String.class);
        Map<String, Object> response = new Gson().fromJson(responseBody,
            new TypeToken<Map<String, Object>>(){}.getType());

        assertNotNull(response.get("previousTarget"),
            "Response missing 'previousTarget' field. Response: " + responseBody);
        assertNotNull(response.get("newTarget"),
            "Response missing 'newTarget' field. Response: " + responseBody);
        assertNotNull(response.get("message"),
            "Response missing 'message' field. Response: " + responseBody);

        log.info(String.format("Alias switch response validated: previousTarget=%s, newTarget=%s",
            response.get("previousTarget"), response.get("newTarget")));

        // Also capture previousTarget here so that subsequent cleanup-verification steps
        // (e.g. "the previous physical index has no aliases remaining") can find it even
        // when the dedicated verifyAliasExistsAndPointsToPhysicalIndex step was not called
        // earlier in the scenario (e.g. the dual-write alias-switch scenario).
        String previousTarget = (String) response.get("previousTarget");
        if (previousTarget != null && storedPhysicalIndex == null) {
            storedPhysicalIndex = previousTarget;
        }
    }

    /**
     * Verifies that an alias now points to the expected physical index.
     */
    @Then("^alias \"([^\"]*)\" now points to physical index \"([^\"]*)\"$")
    public void verifyAliasPointsToIndex(String aliasName, String expectedTarget) {
        String resolvedAlias = generateActualName(aliasName, getTimeStamp());
        String resolvedTarget = generateActualName(expectedTarget, getTimeStamp());

        String actualTarget = elasticUtils.getPhysicalIndexFromAlias(resolvedAlias);
        assertNotNull(actualTarget,
            String.format("Alias '%s' does not exist or has no target", resolvedAlias));
        assertEquals(resolvedTarget, actualTarget,
            String.format("Expected alias '%s' to point to '%s' but points to '%s'",
                resolvedAlias, resolvedTarget, actualTarget));

        log.info(String.format("Verified alias '%s' now points to '%s'", resolvedAlias, resolvedTarget));
    }

    // ==================== REINDEX V2 LIFECYCLE API STEPS ====================

    /**
     * Best-effort cleanup of reindex-v2 state. If a reindex was started during
     * the scenario, attempts to abort it to restore the index to NOT_STARTED state.
     */
    private void cleanupReindexV2State() {
        if (lastReindexV2Kind == null) {
            return;
        }

        log.info(String.format("Cleaning up reindex-v2 state: kind=%s", lastReindexV2Kind));

        try {
            String url = getIndexerBaseUrl() + REINDEX_V2_API + "/" + lastReindexV2Kind;

            HttpResponse response = httpClient.send(
                "DELETE", url, null, getDualWriteApiHeaders(), httpClient.getAccessToken()
            );

            log.info(String.format("Reindex-v2 cleanup abort response: %d", response.getStatus()));
        } catch (Exception e) {
            log.warning(String.format("Failed to clean up reindex-v2 state: %s", e.getMessage()));
        }
    }

    @When("^I start a reindex-v2 for kind \"([^\"]*)\"$")
    public void startReindexV2(String kind) throws Exception {
        startReindexV2Internal(kind, null);
    }

    @When("^I start a reindex-v2 for kind \"([^\"]*)\" with autoMonitor disabled$")
    public void startReindexV2WithAutoMonitorDisabled(String kind) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("autoMonitor", false);
        startReindexV2Internal(kind, new Gson().toJson(body));
    }

    @When("^I start a reindex-v2 for kind \"([^\"]*)\" with autoMonitor enabled$")
    public void startReindexV2WithAutoMonitorEnabled(String kind) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("autoMonitor", true);
        startReindexV2Internal(kind, new Gson().toJson(body));
    }

    @When("^I start a reindex-v2 for kind \"([^\"]*)\" with autoMonitor disabled and (\\d+) shards and (\\d+) replicas$")
    public void startReindexV2WithShardsAndReplicas(String kind, int shards, int replicas) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("autoMonitor", false);
        body.put("numberOfShards", shards);
        body.put("numberOfReplicas", replicas);
        startReindexV2Internal(kind, new Gson().toJson(body));
    }

    private void startReindexV2Internal(String kind, String requestBody) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        log.info(String.format("Starting reindex-v2 for kind: %s (body: %s)", resolvedKind, requestBody));

        String url = getIndexerBaseUrl() + REINDEX_V2_API + "/" + resolvedKind;

        lastReindexV2Response = httpClient.send(
            "POST", url, requestBody, getDualWriteApiHeaders(), httpClient.getAccessToken()
        );

        lastReindexV2ResponseRawBody = lastReindexV2Response.getEntity(String.class);
        log.info(String.format("Reindex-v2 start response: %d - %s",
            lastReindexV2Response.getStatus(), lastReindexV2ResponseRawBody));

        parseReindexV2ResponseBody();

        // Capture the kind on successful start for cleanup purposes
        if (lastReindexV2Response.getStatus() == 202 && lastReindexV2TaskId != null) {
            lastReindexV2Kind = resolvedKind;
        }
    }

    @When("^I get reindex-v2 status for kind \"([^\"]*)\" without taskId$")
    public void getReindexV2StatusWithoutTaskId(String kind) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String url = getIndexerBaseUrl() + REINDEX_V2_API + "/" + resolvedKind + "/status";

        lastReindexV2Response = httpClient.send(
            "GET", url, null, getDualWriteApiHeaders(), httpClient.getAccessToken()
        );

        lastReindexV2ResponseRawBody = lastReindexV2Response.getEntity(String.class);
        log.info(String.format("Reindex-v2 status response: %d - %s",
            lastReindexV2Response.getStatus(), lastReindexV2ResponseRawBody));

        parseReindexV2ResponseBody();
    }

    @When("^I validate reindex-v2 for kind \"([^\"]*)\"$")
    public void validateReindexV2(String kind) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String url = getIndexerBaseUrl() + REINDEX_V2_API + "/" + resolvedKind + "/validate";

        lastReindexV2Response = httpClient.send(
            "GET", url, null, getDualWriteApiHeaders(), httpClient.getAccessToken()
        );

        lastReindexV2ResponseRawBody = lastReindexV2Response.getEntity(String.class);
        log.info(String.format("Reindex-v2 validate response: %d - %s",
            lastReindexV2Response.getStatus(), lastReindexV2ResponseRawBody));

        parseReindexV2ResponseBody();
    }

    @When("^I finalize reindex-v2 for kind \"([^\"]*)\"$")
    public void finalizeReindexV2(String kind) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String url = getIndexerBaseUrl() + REINDEX_V2_API + "/" + resolvedKind + "/finalize";

        int maxRetries = 5;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            lastReindexV2Response = httpClient.send(
                "POST", url, null, getDualWriteApiHeaders(), httpClient.getAccessToken()
            );

            lastReindexV2ResponseRawBody = lastReindexV2Response.getEntity(String.class);
            log.info(String.format("Reindex-v2 finalize attempt %d response: %d - %s",
                attempt + 1, lastReindexV2Response.getStatus(), lastReindexV2ResponseRawBody));

            if (lastReindexV2Response.getStatus() == 200 || attempt == maxRetries) {
                break;
            }
            if (lastReindexV2Response.getStatus() >= 500) {
                break;
            }
            log.info(String.format("Finalize returned %d, retrying in 3s (attempt %d/%d)",
                lastReindexV2Response.getStatus(), attempt + 1, maxRetries));
            Thread.sleep(3000);
        }

        parseReindexV2ResponseBody();
    }

    @When("^I abort reindex-v2 for kind \"([^\"]*)\"$")
    public void abortReindexV2(String kind) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String url = getIndexerBaseUrl() + REINDEX_V2_API + "/" + resolvedKind;

        lastReindexV2Response = httpClient.send(
            "DELETE", url, null, getDualWriteApiHeaders(), httpClient.getAccessToken()
        );

        lastReindexV2ResponseRawBody = lastReindexV2Response.getEntity(String.class);
        log.info(String.format("Reindex-v2 abort response: %d - %s",
            lastReindexV2Response.getStatus(), lastReindexV2ResponseRawBody));

        parseReindexV2ResponseBody();
    }

    @When("^I wait for reindex-v2 validation to pass for kind \"([^\"]*)\" with timeout (\\d+) seconds$")
    public void waitForReindexV2ValidationToPassWithoutTaskId(String kind, int timeoutSeconds) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String validateUrl = getIndexerBaseUrl() + REINDEX_V2_API + "/" + resolvedKind + "/validate";

        PollingConfig config = PollingConfig.builder()
            .withMaxAttempts(timeoutSeconds)
            .withMaxWaitTime(timeoutSeconds * 1000L)
            .withFixedDelay(2000)
            .withDescription("Reindex-v2 validation polling for valid=true")
            .build();

        PollingResult<Map<String, Object>> result = PollingUtils.pollWithRetry(
            config,
            () -> {
                try {
                    HttpResponse response = httpClient.send(
                        "GET", validateUrl, null, getDualWriteApiHeaders(), httpClient.getAccessToken()
                    );
                    String body = response.getEntity(String.class);
                    if (response.getStatus() == 200 && body != null && !body.isEmpty()) {
                        Map<String, Object> parsed = new Gson().fromJson(body,
                            new TypeToken<Map<String, Object>>(){}.getType());
                        parsed.put("_statusCode", response.getStatus());
                        return parsed;
                    }
                    return null;
                } catch (Exception e) {
                    log.warning("Polling reindex-v2 validate failed: " + e.getMessage());
                    return null;
                }
            },
            validateBody -> {
                if (validateBody == null) return false;
                Object valid = validateBody.get("valid");
                log.info(String.format("Reindex-v2 validation result: valid=%s", valid));
                return Boolean.TRUE.equals(valid);
            },
            null
        );

        assertTrue(result.isSuccess(),
            String.format("Reindex-v2 validation did not return valid=true within %d seconds. Last response: %s",
                timeoutSeconds, result.getValue() != null ? new Gson().toJson(result.getValue()) : "null"));

        lastReindexV2ResponseBody = result.getValue();
        lastReindexV2ResponseBody.remove("_statusCode");
        lastReindexV2ResponseRawBody = new Gson().toJson(lastReindexV2ResponseBody);
    }

    @When("^I wait for reindex-v2 phase \"([^\"]*)\" for kind \"([^\"]*)\" with timeout (\\d+) seconds$")
    public void waitForReindexV2Phase(String targetPhase, String kind, int timeoutSeconds) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String statusUrl = getIndexerBaseUrl() + REINDEX_V2_API + "/" + resolvedKind + "/status";

        PollingConfig config = PollingConfig.builder()
            .withMaxAttempts(timeoutSeconds)
            .withMaxWaitTime(timeoutSeconds * 1000L)
            .withFixedDelay(2000)
            .withDescription("Reindex-v2 phase polling for " + targetPhase)
            .build();

        PollingResult<Map<String, Object>> result = PollingUtils.pollWithRetry(
            config,
            () -> {
                try {
                    HttpResponse response = httpClient.send(
                        "GET", statusUrl, null, getDualWriteApiHeaders(), httpClient.getAccessToken()
                    );
                    String body = response.getEntity(String.class);
                    if (response.getStatus() == 200 && body != null && !body.isEmpty()) {
                        return new Gson().fromJson(body,
                            new TypeToken<Map<String, Object>>(){}.getType());
                    }
                    return null;
                } catch (Exception e) {
                    log.warning("Polling reindex-v2 status failed: " + e.getMessage());
                    return null;
                }
            },
            statusBody -> {
                if (statusBody == null) return false;
                String phase = (String) statusBody.get("phase");
                log.info(String.format("Reindex-v2 current phase: %s (waiting for: %s)", phase, targetPhase));
                return targetPhase.equals(phase) || "FAILED".equals(phase);
            },
            null
        );

        assertTrue(result.isSuccess(),
            String.format("Timed out waiting for reindex-v2 phase '%s' after %d seconds",
                targetPhase, timeoutSeconds));

        lastReindexV2ResponseBody = result.getValue();
        lastReindexV2ResponseRawBody = new Gson().toJson(result.getValue());

        String actualPhase = (String) lastReindexV2ResponseBody.get("phase");
        assertEquals(targetPhase, actualPhase,
            String.format("Expected phase '%s' but reached '%s'", targetPhase, actualPhase));
    }

    @Then("^the reindex-v2 response has field \"([^\"]*)\" with value \"([^\"]*)\"$")
    public void verifyReindexV2ResponseField(String fieldName, String expectedValue) {
        assertNotNull(lastReindexV2ResponseBody,
            "No reindex-v2 response body available");
        Object actualValue = lastReindexV2ResponseBody.get(fieldName);
        assertNotNull(actualValue,
            String.format("Field '%s' not found in response. Response: %s",
                fieldName, lastReindexV2ResponseRawBody));
        assertEquals(expectedValue, String.valueOf(actualValue),
            String.format("Field '%s': expected '%s' but got '%s'",
                fieldName, expectedValue, actualValue));
    }

    @Then("^the reindex-v2 response has field \"([^\"]*)\"$")
    public void verifyReindexV2ResponseFieldExists(String fieldName) {
        assertNotNull(lastReindexV2ResponseBody,
            "No reindex-v2 response body available");
        assertTrue(lastReindexV2ResponseBody.containsKey(fieldName),
            String.format("Field '%s' not found in response. Response: %s",
                fieldName, lastReindexV2ResponseRawBody));
    }

    @Then("^the reindex-v2 response status code is (\\d+)$")
    public void verifyReindexV2ResponseStatusCode(int expectedStatus) {
        assertNotNull(lastReindexV2Response, "No reindex-v2 response available");
        assertEquals(expectedStatus, lastReindexV2Response.getStatus(),
            "Unexpected reindex-v2 status code");
    }

    @Then("^the reindex-v2 idempotent start is verified$")
    public void verifyIdempotentStart() {
        assertNotNull(lastReindexV2Response, "No reindex-v2 response available");
        int status = lastReindexV2Response.getStatus();
        if (status == 202) {
            log.info(String.format("Idempotent start verified: 202 with taskId '%s'", lastReindexV2TaskId));
        } else if (status == 409) {
            log.info("Idempotent start: 409 Conflict (phase advanced past REINDEXING before second start)");
        } else {
            fail(String.format("Unexpected status code %d for idempotent start (expected 202 or 409)", status));
        }
    }

    @Then("^the reindex-v2 status response status code is (\\d+)$")
    public void verifyReindexV2StatusResponseCode(int expectedStatus) {
        assertNotNull(lastReindexV2Response, "No reindex-v2 status response available");
        assertEquals(expectedStatus, lastReindexV2Response.getStatus(),
            "Unexpected reindex-v2 status code");
    }

    @Then("^the reindex-v2 status response has a phase$")
    public void verifyReindexV2StatusHasPhase() {
        assertNotNull(lastReindexV2ResponseBody, "No reindex-v2 status response body available");
        assertNotNull(lastReindexV2ResponseBody.get("phase"), "Response does not contain a phase");
    }

    @Then("^the reindex-v2 status response has phase \"([^\"]*)\"$")
    public void verifyReindexV2StatusPhase(String expectedPhase) {
        assertNotNull(lastReindexV2ResponseBody, "No reindex-v2 status response body available");
        String actualPhase = (String) lastReindexV2ResponseBody.get("phase");
        assertEquals(expectedPhase, actualPhase,
            String.format("Expected phase '%s' but got '%s'", expectedPhase, actualPhase));
    }


    @When("^I delete a document from the reindex-v2 target index$")
    public void deleteDocumentFromReindexV2TargetIndex() throws Exception {
        assertNotNull(lastReindexV2ResponseBody, "No reindex-v2 response body available");

        // Get the target index from the last status/start response
        String targetIndex = (String) lastReindexV2ResponseBody.get("targetIndex");
        assertNotNull(targetIndex, "No targetIndex found in reindex-v2 response. Response: "
            + lastReindexV2ResponseRawBody);

        // Refresh the target index so all documents are searchable
        elasticUtils.refreshIndex(targetIndex);

        long docCountBefore = elasticUtils.fetchRecords(targetIndex);
        assertTrue(docCountBefore > 0,
            String.format("Target index '%s' has no documents to delete", targetIndex));

        // Delete the first document found in the target index
        // (deleteFirstDocument calls refreshIndex internally)
        String deletedId = elasticUtils.deleteFirstDocument(targetIndex);
        log.info(String.format("Deleted document '%s' from target index '%s'", deletedId, targetIndex));

        long docCountAfter = elasticUtils.fetchRecords(targetIndex);
        log.info(String.format("Target index '%s' document count: %d -> %d",
            targetIndex, docCountBefore, docCountAfter));
    }

    @When("^I delete all documents from the \"([^\"]*)\" index$")
    public void deleteAllDocumentsFromIndex(String index) throws Exception {
        String resolvedIndex = generateActualName(index, getTimeStamp());
        log.info(String.format("Deleting all documents from index '%s'", resolvedIndex));

        elasticUtils.refreshIndex(resolvedIndex);
        long docCountBefore = elasticUtils.fetchRecords(resolvedIndex);
        log.info(String.format("Index '%s' document count before delete: %d", resolvedIndex, docCountBefore));

        long deleted = elasticUtils.deleteAllDocuments(resolvedIndex);
        log.info(String.format("Deleted %d documents from index '%s'", deleted, resolvedIndex));

        long docCountAfter = elasticUtils.fetchRecords(resolvedIndex);
        assertEquals(0, docCountAfter,
            String.format("Expected 0 documents after delete-all but found %d in index '%s'",
                docCountAfter, resolvedIndex));
    }

    // ==================== REPLAY V2 START API STEPS ====================

    /**
     * Best-effort cleanup of replay-v2 state. If a replay was started during
     * the scenario, attempts to abort it.
     */
    private void cleanupReplayV2State() {
        if (lastReplayV2Kind == null) {
            return;
        }

        log.info(String.format("Cleaning up replay-v2 state: kind=%s", lastReplayV2Kind));

        try {
            // Abort endpoint will be available in later PBIs; for now just log.
            // When abort is available:
            // String url = getIndexerBaseUrl() + REPLAY_V2_API + "/" + lastReplayV2Kind;
            // httpClient.send("DELETE", url, null, getDualWriteApiHeaders(), httpClient.getAccessToken());
            log.info("Replay-v2 cleanup: abort endpoint not yet available (PBI-2 only has start)");
        } catch (Exception e) {
            log.warning(String.format("Failed to clean up replay-v2 state: %s", e.getMessage()));
        }
    }

    @When("^I start a replay-v2 for kind \"([^\"]*)\"$")
    public void startReplayV2(String kind) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        log.info(String.format("Starting replay-v2 for kind: %s", resolvedKind));

        String url = getIndexerBaseUrl() + REPLAY_V2_API + "/" + resolvedKind;
        lastReplayV2RequestUrl = url;
        Map<String, String> headers = getDualWriteApiHeaders();

        lastReplayV2Response = httpClient.send(
            "POST", url, "", headers, httpClient.getAccessToken()
        );

        // AzureHTTPClient injects correlation-id into the headers map before sending
        lastReplayV2CorrelationId = headers.get("correlation-id");

        lastReplayV2ResponseRawBody = lastReplayV2Response.getEntity(String.class);
        log.info(String.format("Replay-v2 start response: %d - %s (correlationId=%s)",
            lastReplayV2Response.getStatus(), lastReplayV2ResponseRawBody, lastReplayV2CorrelationId));

        parseReplayV2ResponseBody();

        if (lastReplayV2Response.getStatus() >= 400) {
            logReplayV2Error("START", url, resolvedKind, null);
        }

        // Track the kind and runId for cleanup and advance calls
        if (lastReplayV2Response.getStatus() == 202) {
            lastReplayV2Kind = resolvedKind;
            if (lastReplayV2ResponseBody != null && lastReplayV2ResponseBody.containsKey("runId")) {
                lastReplayV2RunId = String.valueOf(lastReplayV2ResponseBody.get("runId"));
            }
        }
    }

    @Then("^the replay-v2 response status code is (\\d+)$")
    public void verifyReplayV2ResponseStatusCode(int expectedStatus) {
        assertNotNull(lastReplayV2Response, "No replay-v2 response available");
        int actualStatus = lastReplayV2Response.getStatus();
        if (actualStatus != expectedStatus) {
            String detail = String.format(
                "\n===== REPLAY-V2 ASSERTION FAILURE ====="
                + "\n  Expected HTTP: %d"
                + "\n  Actual HTTP:   %d"
                + "\n  URL:           %s"
                + "\n  Kind:          %s"
                + "\n  RunId:         %s"
                + "\n  CorrelationId: %s"
                + "\n  Response Body: %s"
                + "\n======================================",
                expectedStatus, actualStatus,
                lastReplayV2RequestUrl,
                lastReplayV2Kind,
                lastReplayV2RunId,
                lastReplayV2CorrelationId,
                lastReplayV2ResponseRawBody);
            System.err.println(detail);
            fail(detail);
        }
    }

    @Then("^the replay-v2 response status code is not (\\d+)$")
    public void verifyReplayV2ResponseStatusCodeIsNot(int unexpectedStatus) {
        assertNotNull(lastReplayV2Response, "No replay-v2 response available");
        assertNotEquals(unexpectedStatus, lastReplayV2Response.getStatus(),
            String.format("Should NOT get HTTP %d but did", unexpectedStatus));
    }

    @Then("^the replay-v2 response has field \"([^\"]*)\" with value \"([^\"]*)\"$")
    public void verifyReplayV2ResponseField(String fieldName, String expectedValue) {
        assertNotNull(lastReplayV2ResponseBody,
            "No replay-v2 response body available");
        Object actualValue = lastReplayV2ResponseBody.get(fieldName);
        assertNotNull(actualValue,
            String.format("Field '%s' not found in replay-v2 response. Response: %s",
                fieldName, lastReplayV2ResponseRawBody));
        assertEquals(expectedValue, String.valueOf(actualValue),
            String.format("Field '%s': expected '%s' but got '%s'",
                fieldName, expectedValue, actualValue));
    }

    @Then("^the replay-v2 response has field \"([^\"]*)\"$")
    public void verifyReplayV2ResponseFieldExists(String fieldName) {
        assertNotNull(lastReplayV2ResponseBody,
            "No replay-v2 response body available");
        assertTrue(lastReplayV2ResponseBody.containsKey(fieldName),
            String.format("Field '%s' not found in replay-v2 response. Response: %s",
                fieldName, lastReplayV2ResponseRawBody));
    }

    private void parseReplayV2ResponseBody() {
        lastReplayV2ResponseBody = null;
        if (lastReplayV2ResponseRawBody != null && !lastReplayV2ResponseRawBody.isEmpty()) {
            try {
                lastReplayV2ResponseBody = new Gson().fromJson(lastReplayV2ResponseRawBody,
                    new TypeToken<Map<String, Object>>(){}.getType());
            } catch (Exception e) {
                log.warning("Failed to parse replay-v2 response body as JSON: " + e.getMessage());
            }
        }
    }

    /**
     * Logs a prominent error block to stderr so it stands out in CI Maven Surefire output.
     * Includes correlation-id for tracing to server-side pod logs.
     */
    private void logReplayV2Error(String operation, String url, String kind, String runId) {
        String errorMessage = "(raw body)";
        String errorReason = "";
        if (lastReplayV2ResponseBody != null) {
            Object msg = lastReplayV2ResponseBody.get("message");
            Object reason = lastReplayV2ResponseBody.get("reason");
            if (msg != null) errorMessage = String.valueOf(msg);
            if (reason != null) errorReason = String.valueOf(reason);
        }
        String detail = String.format(
            "\n!!!!! REPLAY-V2 %s ERROR !!!!!"
            + "\n  HTTP Status:   %d"
            + "\n  Error Reason:  %s"
            + "\n  Error Message: %s"
            + "\n  URL:           %s"
            + "\n  Kind:          %s"
            + "\n  RunId:         %s"
            + "\n  CorrelationId: %s"
            + "\n  Full Body:     %s"
            + "\n  Tip: Search server logs with correlationId above to find root cause"
            + "\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!",
            operation,
            lastReplayV2Response.getStatus(),
            errorReason,
            errorMessage,
            url,
            kind,
            runId,
            lastReplayV2CorrelationId,
            lastReplayV2ResponseRawBody);
        System.err.println(detail);
    }

    // ==================== REPLAY V2 ADVANCE API STEPS ====================

    @When("^I advance the replay-v2 for kind \"([^\"]*)\" with the runId from start$")
    public void advanceReplayV2WithStartRunId(String kind) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        assertNotNull(lastReplayV2RunId,
            "No runId available from a prior replay-v2 start response");
        advanceReplayV2(resolvedKind, lastReplayV2RunId);
    }

    @When("^I advance the replay-v2 for kind \"([^\"]*)\" with runId \"([^\"]*)\"$")
    public void advanceReplayV2WithExplicitRunId(String kind, String runId) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        advanceReplayV2(resolvedKind, runId);
    }

    private void advanceReplayV2(String resolvedKind, String runId) throws Exception {
        log.info(String.format("Advancing replay-v2: kind=%s, runId=%s", resolvedKind, runId));

        String url = getIndexerBaseUrl() + REPLAY_V2_API + "/advance"
            + "?kind=" + java.net.URLEncoder.encode(resolvedKind, "UTF-8")
            + "&runId=" + java.net.URLEncoder.encode(runId, "UTF-8");
        lastReplayV2RequestUrl = url;
        Map<String, String> headers = getDualWriteApiHeaders();

        lastReplayV2Response = httpClient.send(
            "POST", url, "", headers, httpClient.getAccessToken()
        );

        // AzureHTTPClient injects correlation-id into the headers map before sending
        lastReplayV2CorrelationId = headers.get("correlation-id");

        lastReplayV2ResponseRawBody = lastReplayV2Response.getEntity(String.class);
        log.info(String.format("Replay-v2 advance response: %d - %s (correlationId=%s)",
            lastReplayV2Response.getStatus(), lastReplayV2ResponseRawBody, lastReplayV2CorrelationId));

        parseReplayV2ResponseBody();

        if (lastReplayV2Response.getStatus() >= 400) {
            logReplayV2Error("ADVANCE", url, resolvedKind, runId);
        }
    }

    // ============ SEARCH API VERIFICATION STEP DEFINITIONS ============

    private static final String SEARCH_QUERY_PATH = "query";

    /**
     * Returns the base URL for the Search service, normalized to have a
     * trailing slash so we can simply append "query".
     */
    private String getSearchBaseUrl() {
        String searchHost = Config.getSearchBaseURL();
        assertNotNull(searchHost, "SEARCH_HOST environment variable is not configured");
        assertFalse(searchHost.isEmpty(), "SEARCH_HOST environment variable is empty");
        if (!searchHost.endsWith("/")) {
            searchHost = searchHost + "/";
        }
        return searchHost;
    }

    @Then("^I should find (\\d+) records via the search API for kind \"([^\"]*)\"$")
    public void verifySearchApiRecordCount(int expectedCount, String kind) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String searchUrl = getSearchBaseUrl() + SEARCH_QUERY_PATH;

        Map<String, Object> searchBody = new HashMap<>();
        searchBody.put("kind", resolvedKind);
        searchBody.put("limit", 1000);
        searchBody.put("query", "*");
        String payload = new Gson().toJson(searchBody);

        log.info(String.format("Searching via OSDU Search API for kind '%s', expecting %d records",
            resolvedKind, expectedCount));

        PollingConfig config = PollingConfig.builder()
            .withMaxAttempts(15)
            .withMaxWaitTime(60000L)
            .withFixedDelay(4000)
            .withDescription("Search API polling for " + expectedCount + " records of kind " + resolvedKind)
            .build();

        PollingResult<Long> result = PollingUtils.pollWithRetry(
            config,
            () -> {
                try {
                    HttpResponse response = httpClient.send(
                        "POST", searchUrl, payload, getDualWriteApiHeaders(), httpClient.getAccessToken()
                    );
                    String body = response.getEntity(String.class);
                    if (response.getStatus() == 200 && body != null && !body.isEmpty()) {
                        Map<String, Object> parsed = new Gson().fromJson(body,
                            new TypeToken<Map<String, Object>>(){}.getType());
                        Number totalCount = (Number) parsed.get("totalCount");
                        long count = totalCount != null ? totalCount.longValue() : -1;
                        log.info(String.format("Search API returned totalCount=%d for kind '%s'",
                            count, resolvedKind));
                        return count;
                    }
                    log.warning(String.format("Search API returned status %d: %s",
                        response.getStatus(), body));
                    return -1L;
                } catch (Exception e) {
                    log.warning("Search API call failed: " + e.getMessage());
                    return -1L;
                }
            },
            count -> count == expectedCount,
            null
        );

        assertTrue(result.isSuccess(),
            String.format("Search API did not return %d records for kind '%s' within timeout. Last count: %d",
                expectedCount, resolvedKind, result.getValue() != null ? result.getValue() : -1));
    }

    private void parseReindexV2ResponseBody() {
        lastReindexV2ResponseBody = null;
        if (lastReindexV2ResponseRawBody != null && !lastReindexV2ResponseRawBody.isEmpty()) {
            try {
                lastReindexV2ResponseBody = new Gson().fromJson(lastReindexV2ResponseRawBody,
                    new TypeToken<Map<String, Object>>(){}.getType());
                if (lastReindexV2ResponseBody != null && lastReindexV2ResponseBody.containsKey("taskId")) {
                    lastReindexV2TaskId = (String) lastReindexV2ResponseBody.get("taskId");
                }
            } catch (Exception e) {
                log.warning("Failed to parse reindex-v2 response body as JSON: " + e.getMessage());
            }
        }
    }

    @When("^I wait for reindex-v2 status without taskId to contain \"([^\"]*)\" for kind \"([^\"]*)\" with timeout (\\d+) seconds$")
    public void waitForReindexV2StatusWithoutTaskIdToContainField(String fieldName, String kind, int timeoutSeconds) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String statusUrl = getIndexerBaseUrl() + REINDEX_V2_API + "/" + resolvedKind + "/status";

        PollingConfig config = PollingConfig.builder()
            .withMaxAttempts(timeoutSeconds / 2)
            .withMaxWaitTime(timeoutSeconds * 1000L)
            .withFixedDelay(2000)
            .withDescription("Reindex-v2 status (no taskId) polling for field '" + fieldName + "'")
            .build();

        PollingResult<Map<String, Object>> result = PollingUtils.pollWithRetry(
            config,
            () -> {
                try {
                    HttpResponse response = httpClient.send(
                        "GET", statusUrl, null, getDualWriteApiHeaders(), httpClient.getAccessToken()
                    );
                    String body = response.getEntity(String.class);
                    if (response.getStatus() == 200 && body != null && !body.isEmpty()) {
                        return new Gson().fromJson(body,
                            new TypeToken<Map<String, Object>>(){}.getType());
                    }
                    return null;
                } catch (Exception e) {
                    log.warning("Polling reindex-v2 status (no taskId) failed: " + e.getMessage());
                    return null;
                }
            },
            statusBody -> {
                if (statusBody == null) return false;
                boolean hasField = statusBody.containsKey(fieldName) && statusBody.get(fieldName) != null;
                log.info(String.format("Reindex-v2 status (no taskId): phase=%s, has %s=%s",
                    statusBody.get("phase"), fieldName, hasField));
                return hasField;
            },
            null
        );

        assertTrue(result.isSuccess(),
            String.format("Timed out waiting for reindex-v2 status (no taskId) to contain field '%s' after %d seconds",
                fieldName, timeoutSeconds));

        lastReindexV2ResponseBody = result.getValue();
        lastReindexV2ResponseRawBody = new Gson().toJson(result.getValue());
        if (lastReindexV2ResponseBody.containsKey("taskId")) {
            lastReindexV2TaskId = (String) lastReindexV2ResponseBody.get("taskId");
        }
    }

    // ==================== END REINDEX V2 LIFECYCLE API STEPS ====================

    @Then("^the reindex-v2 target index should have \"([^\"]*)\" set to \"([^\"]*)\"$")
    public void verifyReindexV2TargetIndexSetting(String settingName, String expectedValue) {
        assertNotNull(lastReindexV2ResponseBody, "No reindex-v2 response body available");

        String targetIndex = (String) lastReindexV2ResponseBody.get("targetIndex");
        assertNotNull(targetIndex, "No targetIndex found in reindex-v2 response. Response: "
            + lastReindexV2ResponseRawBody);

        Map<String, String> settings = elasticUtils.getIndexSettings(targetIndex);
        assertFalse(settings.isEmpty(),
            String.format("Could not retrieve settings for target index '%s'", targetIndex));

        String actualValue = settings.get(settingName);
        assertNotNull(actualValue,
            String.format("Setting '%s' not found in target index '%s'. Available: %s",
                settingName, targetIndex, settings));
        assertEquals(expectedValue, actualValue,
            String.format("Target index '%s' setting '%s': expected '%s' but got '%s'",
                targetIndex, settingName, expectedValue, actualValue));
    }

    // ============ MAPPING-CHECK STEP DEFINITIONS ============

    @When("^I check mapping for kind \"([^\"]*)\"$")
    public void checkMappingForKind(String kind) throws Exception {
        String resolvedKind = generateActualName(kind, getTimeStamp());
        String url = getIndexerBaseUrl() + REINDEX_V2_API + "/" + resolvedKind + "/mapping-check";

        lastReindexV2Response = httpClient.send(
            "GET", url, null, getDualWriteApiHeaders(), httpClient.getAccessToken()
        );

        lastReindexV2ResponseRawBody = lastReindexV2Response.getEntity(String.class);
        log.info(String.format("Mapping-check response: %d - %s",
            lastReindexV2Response.getStatus(), lastReindexV2ResponseRawBody));

        parseReindexV2ResponseBody();
    }

    @SuppressWarnings("unchecked")
    @Then("^the mapping-check response diffSummary has \"([^\"]*)\"$")
    public void verifyMappingCheckDiffSummaryField(String fieldName) {
        assertNotNull(lastReindexV2ResponseBody,
            "No mapping-check response body available");

        Object diffSummary = lastReindexV2ResponseBody.get("diffSummary");
        assertNotNull(diffSummary,
            "No 'diffSummary' found in mapping-check response. Response: " + lastReindexV2ResponseRawBody);

        assertTrue(diffSummary instanceof Map,
            "Expected 'diffSummary' to be a JSON object but got: " + diffSummary.getClass().getSimpleName());

        Map<String, Object> diffMap = (Map<String, Object>) diffSummary;
        assertTrue(diffMap.containsKey(fieldName),
            String.format("Field '%s' not found in diffSummary. Available fields: %s", fieldName, diffMap.keySet()));
    }

    @Then("^the mapping-check response has no diffSummary$")
    public void verifyMappingCheckHasNoDiffSummary() {
        assertNotNull(lastReindexV2ResponseBody,
            "No mapping-check response body available");
        assertFalse(lastReindexV2ResponseBody.containsKey("diffSummary"),
            "Expected 'diffSummary' to be absent (mapping is current) but found it. Response: " + lastReindexV2ResponseRawBody);
    }

    @Then("^the reindex-v2 response body contains \"([^\"]*)\"$")
    public void verifyReindexV2ResponseBodyContains(String expectedText) {
        assertNotNull(lastReindexV2ResponseRawBody,
            "No reindex-v2 response body available");
        assertTrue(lastReindexV2ResponseRawBody.contains(expectedText),
            String.format("Expected response body to contain '%s' but got: %s",
                expectedText, lastReindexV2ResponseRawBody));
    }

    /**
     * Cleans up manually created physical indexes after each scenario.
     */
    private void cleanupManuallyCreatedIndexes() {
        for (String indexName : manuallyCreatedIndexes) {
            try {
                if (elasticUtils.isIndexExist(indexName)) {
                    elasticUtils.deleteIndex(indexName);
                    log.info(String.format("Cleaned up manually created index: %s", indexName));
                }
            } catch (Exception e) {
                log.warning(String.format("Failed to clean up index %s: %s", indexName, e.getMessage()));
            }
        }
        manuallyCreatedIndexes.clear();
    }

    /**
     * Best-effort cleanup of any snapshot started during the scenario.
     * Also restores the repository throttle in case it was set.
     */
    private void cleanupActiveSnapshot() {
        if (activeSnapshotRepo == null) {
            return;
        }
        log.info(String.format("Cleaning up snapshot state: repo=%s, snapshot=%s",
            activeSnapshotRepo, activeSnapshotName));
        try {
            if (activeSnapshotName != null) {
                elasticUtils.deleteSnapshot(activeSnapshotRepo, activeSnapshotName);
            }
        } catch (Exception e) {
            log.warning(String.format("Failed to delete snapshot '%s': %s", activeSnapshotName, e.getMessage()));
        }
        try {
            elasticUtils.restoreSnapshotRepositoryThrottle(activeSnapshotRepo);
        } catch (Exception e) {
            log.warning(String.format("Failed to restore throttle on repo '%s': %s", activeSnapshotRepo, e.getMessage()));
        }
        if (snapshotRepoCreated) {
            try {
                elasticUtils.deleteSnapshotRepository(activeSnapshotRepo);
                snapshotRepoCreated = false;
            } catch (Exception e) {
                log.warning(String.format("Failed to delete test snapshot repository '%s': %s", activeSnapshotRepo, e.getMessage()));
            }
        }
    }

    // ========== Snapshot step definitions ==========

    /**
     * Builds the snapshot repository name using the configured tenant1 partition ID
     * and the "-primary" suffix (matching the Helm chart setup in post-setup-tasks.yaml).
     */
    private String buildSnapshotRepoName() {
        return Config.getDataPartitionIdTenant1() + "-primary";
    }

    @When("^I throttle the Elasticsearch snapshot repository to prevent progress$")
    public void throttleElasticsearchSnapshotRepository() throws Exception {
        String repoName = buildSnapshotRepoName();
        activeSnapshotRepo = repoName;
        elasticUtils.throttleSnapshotRepository(repoName);
        log.info(String.format("Snapshot repository '%s' throttled to 1b/s", repoName));
    }

    @When("^I start an Elasticsearch snapshot in the snapshot repository$")
    public void startElasticsearchSnapshot() throws Exception {
        String repoName = buildSnapshotRepoName();
        String snapshotName = "test-snapshot-" + System.currentTimeMillis();
        // Scope the snapshot to the current test's physical index only.
        // This prevents the throttled snapshot from blocking finalize on indices
        // used by other concurrently-running reindex-v2 scenarios.
        assertNotNull(storedPhysicalIndex,
                "storedPhysicalIndex is null — the 'I should get N documents' step must run first");
        activeSnapshotName = elasticUtils.startSnapshot(repoName, snapshotName, storedPhysicalIndex);
        log.info(String.format("Started snapshot '%s' in repository '%s' scoped to index '%s'",
                activeSnapshotName, repoName, storedPhysicalIndex));
    }

    @When("^I wait for the snapshot to be IN_PROGRESS with timeout (\\d+) seconds$")
    public void waitForSnapshotInProgress(int timeoutSeconds) throws Exception {
        assertNotNull(activeSnapshotRepo, "No active snapshot repository — call throttle step first");
        assertNotNull(activeSnapshotName, "No active snapshot — call start snapshot step first");

        PollingConfig config = PollingConfig.builder()
            .withMaxAttempts(timeoutSeconds)
            .withMaxWaitTime(timeoutSeconds * 1000L)
            .withFixedDelay(1000)
            .withDescription("Waiting for snapshot to reach IN_PROGRESS")
            .build();

        PollingResult<String> result = PollingUtils.pollWithRetry(
            config,
            () -> {
                try {
                    return elasticUtils.getSnapshotState(activeSnapshotRepo, activeSnapshotName);
                } catch (Exception e) {
                    log.warning("Polling snapshot state failed: " + e.getMessage());
                    return null;
                }
            },
            state -> "IN_PROGRESS".equals(state),
            state -> log.info(String.format("Snapshot state: %s", state))
        );

        assertTrue(result.isSuccess(),
            String.format("Snapshot did not reach IN_PROGRESS within %d seconds. Last state: %s",
                timeoutSeconds, result.getValue()));
        log.info(String.format("Snapshot '%s' is IN_PROGRESS", activeSnapshotName));
    }

    @When("^I abort the active snapshot$")
    public void abortActiveSnapshot() throws Exception {
        assertNotNull(activeSnapshotRepo, "No active snapshot repository");
        assertNotNull(activeSnapshotName, "No active snapshot name");
        elasticUtils.deleteSnapshot(activeSnapshotRepo, activeSnapshotName);
        log.info(String.format("Aborted snapshot '%s' in repository '%s'", activeSnapshotName, activeSnapshotRepo));
        activeSnapshotName = null;
    }

    @When("^I restore the Elasticsearch snapshot repository throttle settings$")
    public void restoreElasticsearchSnapshotRepositoryThrottle() throws Exception {
        assertNotNull(activeSnapshotRepo, "No active snapshot repository");
        elasticUtils.restoreSnapshotRepositoryThrottle(activeSnapshotRepo);
        log.info(String.format("Restored throttle settings for repository '%s'", activeSnapshotRepo));
    }

    @When("^I wait for no active snapshots in the repository with timeout (\\d+) seconds$")
    public void waitForNoActiveSnapshots(int timeoutSeconds) throws Exception {
        assertNotNull(activeSnapshotRepo, "No active snapshot repository");
        String repoName = activeSnapshotRepo;

        PollingConfig config = PollingConfig.builder()
            .withMaxAttempts(timeoutSeconds / 2)
            .withMaxWaitTime(timeoutSeconds * 1000L)
            .withFixedDelay(2000)
            .withDescription("Waiting for no active snapshots in " + repoName)
            .build();

        PollingResult<Boolean> result = PollingUtils.pollWithRetry(
            config,
            () -> {
                try {
                    boolean active = elasticUtils.hasActiveSnapshots(repoName);
                    log.info(String.format("Active snapshots in '%s': %s", repoName, active));
                    return !active;
                } catch (Exception e) {
                    log.warning("Polling active snapshots failed: " + e.getMessage());
                    return null;
                }
            },
            noActive -> Boolean.TRUE.equals(noActive),
            null
        );

        assertTrue(result.isSuccess(),
            String.format("Active snapshots still present in '%s' after %d seconds", repoName, timeoutSeconds));
        activeSnapshotRepo = null;
        log.info(String.format("No active snapshots in repository '%s'", repoName));
    }

    @Then("^the reindex-v2 response field \"([^\"]*)\" contains \"([^\"]*)\"$")
    public void verifyReindexV2ResponseFieldContains(String fieldName, String expectedSubstring) {
        assertNotNull(lastReindexV2ResponseBody, "No reindex-v2 response body available");
        Object actualValue = lastReindexV2ResponseBody.get(fieldName);
        assertNotNull(actualValue,
            String.format("Field '%s' not found in response. Response: %s",
                fieldName, lastReindexV2ResponseRawBody));
        assertTrue(String.valueOf(actualValue).contains(expectedSubstring),
            String.format("Field '%s' value '%s' does not contain '%s'",
                fieldName, actualValue, expectedSubstring));
    }
}
