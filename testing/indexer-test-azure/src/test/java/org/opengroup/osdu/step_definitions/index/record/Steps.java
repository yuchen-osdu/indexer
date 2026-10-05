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

import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.java.Log;

import org.opengroup.osdu.common.SchemaServiceRecordSteps;
import org.opengroup.osdu.util.AzureHTTPClient;
import org.opengroup.osdu.util.ElasticUtils;

import java.util.List;


@Log
public class Steps extends SchemaServiceRecordSteps {

    // Track manually created physical indexes for cleanup
    private List<String> manuallyCreatedIndexes = new java.util.ArrayList<>();

    public Steps() {
        super(new AzureHTTPClient(), new ElasticUtils());
    }

    @Before
    public void before(Scenario scenario) {
        this.scenario = scenario;
        manuallyCreatedIndexes.clear();
    }

    @After
    public void after(Scenario scenario) {
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
}