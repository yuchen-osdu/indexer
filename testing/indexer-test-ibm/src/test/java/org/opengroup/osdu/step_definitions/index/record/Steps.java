/*
 * Copyright 2017-2025, IBM
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

package org.opengroup.osdu.step_definitions.index.record;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.common.RecordSteps;
import org.opengroup.osdu.common.SchemaServiceRecordSteps;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.common.http.HttpClient;
import org.opengroup.osdu.core.common.http.HttpRequest;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.core.ibm.util.Config;
import org.opengroup.osdu.util.ElasticUtils;
import org.opengroup.osdu.util.IBMHTTPClient;

import com.google.gson.Gson;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.DataTableType;
import io.cucumber.java.Scenario;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.java.Log;

import org.opengroup.osdu.models.Setup;

@Log
public class Steps extends SchemaServiceRecordSteps {

    public Steps() {
        super(new IBMHTTPClient(), new ElasticUtils());
    }

    @DataTableType
    public Setup setupEntry(Map<String, String> entry) {
        Setup setup = new Setup();
        setup.setTenantId(entry.get("tenantId"));
        setup.setKind(entry.get("kind"));
        setup.setIndex(entry.get("index"));
        setup.setViewerGroup(entry.get("viewerGroup"));
        setup.setOwnerGroup(entry.get("ownerGroup"));
        setup.setMappingFile(entry.get("mappingFile"));
        setup.setRecordFile(entry.get("recordFile"));
        setup.setSchemaFile(entry.get("schemaFile"));
        return setup;
    }

    @Before
    public void before(Scenario scenario) {
        this.scenario = scenario;
        this.httpClient = new IBMHTTPClient();
    }

    @Given("^the schema is created with the following kind$")
    public void the_schema_is_created_with_the_following_kind(DataTable dataTable) {
        super.the_schema_is_created_with_the_following_kind(dataTable);
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

    @Then("^I should get the elastic \"(.*?)\" for the \"([^\"]*)\" and \"([^\"]*)\" in the Elastic Search$")
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

    @Then("^I should be able search (\\d+) documents for the \"([^\"]*)\" by bounding box query with points \\((-?\\d+), (-?\\d+)\\) and  \\((-?\\d+), (-?\\d+)\\) on field \"(.*?)\"$")
    public void i_should_get_the_documents_for_the_in_the_Elastic_Search_by_geoQuery (
            int expectedCount, String index, Double topLatitude, Double topLongitude, Double bottomLatitude, Double bottomLongitude, String field) throws Throwable {
        super.i_should_get_the_documents_for_the_in_the_Elastic_Search_by_geoQuery(expectedCount, index, topLatitude, topLongitude, bottomLatitude, bottomLongitude, field);
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

    @Then("^I should get \"([^\"]*)\" in response, without hints in schema for the \"([^\"]*)\" that present in the \"([^\"]*)\" with \"([^\"]*)\" for a given \"([^\"]*)\"$")
    public void i_should_get_object_in_search_response_without_hints_in_schema(String objectInnerField, String index, String recordFile, String acl, String kind)
        throws Throwable {
        super.i_should_get_object_in_search_response_without_hints_in_schema(objectInnerField ,index, recordFile, acl, kind);
    }

    @When("^I pass api key$")
    public void i_pass_the_api_key() {
    }

    @Then("^compare with key configured in properties file$")
    public void compare_with_key_configured_in_propertiesFile() throws Throwable {

    	final String CORRELATION_ID = "1234";
        final String OPENDES = "opendes";
    	final Gson gson = new Gson();

    	String INDEXER_API_KEY = Config.getEnvironmentVariable("INDEXER_API_KEY");
    	String INDEXER_HOST_URL = Config.getEnvironmentVariable("INDEXER_HOST_URL");

    	RecordChangedMessages recordChangeMessage = new RecordChangedMessages();

    	String data = "[{\"id\":\"opendes:doc:1234\",\"kind\":\"opendes:test:test:1.0.0\",\"op\":\"create\"}]";

    	Map<String, String> attributes = new HashMap<>();
    	attributes.put("correlation-id", CORRELATION_ID);
    	attributes.put("data-partition-id", OPENDES);
    	recordChangeMessage.setAttributes(attributes);
    	recordChangeMessage.setData(data);

    	String url = StringUtils.join(INDEXER_HOST_URL, Constants.WORKER_RELATIVE_URL);
		HttpClient httpClient = new HttpClient();
		DpsHeaders dpsHeaders = new DpsHeaders();
		dpsHeaders.put("x-api-key", INDEXER_API_KEY);
		dpsHeaders.put("correlation-id", CORRELATION_ID);
		dpsHeaders.put("data-partition-id", OPENDES);

		HttpRequest rq = HttpRequest.post(recordChangeMessage).url(url).headers(dpsHeaders.getHeaders()).build();
		HttpResponse result = httpClient.send(rq);
		if(result.hasException() == false && result.getResponseCode() == 500) {
			assertEquals(500, result.getResponseCode());
		} else {
			AppError error = gson.fromJson(result.getBody(), AppError.class);
			assertFalse(error.getCode() == 401, "Token is mismatched");
		}

    }

    @Then("^I should be able to search for record from \"([^\"]*)\" by \"([^\"]*)\" for value \"([^\"]*)\" and find String arrays in \"([^\"]*)\" with \"([^\"]*)\"$")
    public void i_should_get_string_array_in_search_response(String index, String field, String fieldValue, String arrayField, String arrayValue)
            throws Throwable {
        super.i_should_get_string_array_in_search_response(index, field, fieldValue, arrayField, arrayValue);
    }
}
