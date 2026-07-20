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

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import org.opengroup.osdu.models.Setup;
import org.opengroup.osdu.models.schema.PersistentSchemaTestIndex;
import org.opengroup.osdu.util.ElasticUtils;
import org.opengroup.osdu.util.HTTPClient;
import org.opengroup.osdu.models.TestIndex;

import java.util.List;
import java.util.Map;

public class SchemaServiceRecordSteps extends RecordSteps {
    private static boolean runStatefulScenario = false;

    public SchemaServiceRecordSteps(HTTPClient httpClient, ElasticUtils elasticUtils) {
        super(httpClient, elasticUtils);
    }

    public void the_schema_is_created_with_the_following_kind(DataTable dataTable) {
        if(!SchemaServiceRecordSteps.runStatefulScenario) {
            List<Setup> inputList = dataTable.asList(Setup.class);
            inputList.forEach(this::setup);
            super.addShutDownHook();
        }
    }

    public void the_schema_is_updated_with_the_following_kind(DataTable dataTable) {
        List<Setup> inputList = dataTable.asList(Setup.class);
        inputList.forEach(this::updateSchema);
    }

    public void i_set_scenarios_as_stateful(boolean stateful) throws Throwable {
        SchemaServiceRecordSteps.runStatefulScenario = stateful;
    }

    private void setup(Setup input) {
        PersistentSchemaTestIndex testIndex = new PersistentSchemaTestIndex(super.elasticUtils, super.httpClient, this);
        testIndex.setIndex(generateActualName(input.getIndex(), super.getTimeStamp()));
        testIndex.setSchemaFile(input.getSchemaFile());
        testIndex.setHttpClient(super.httpClient);
        testIndex.setupSchema();
        testIndex.setKind(testIndex.getSchemaModel().getSchemaInfo().getSchemaIdentity().getId());

        super.getInputIndexMap().put(testIndex.getKind(), testIndex);

        // Delete the index via the indexer service to ensure a clean baseline.
        // For timestamped kinds this is a no-op (the index doesn't exist yet),
        // but for static/well-known kinds (e.g., IndexPropertyPathConfiguration)
        // it clears stale data from previous scenarios.
        this.indexerClientUtil.deleteIndex(testIndex.getKind());
    }

    private void updateSchema(Setup input) {
        String actualKind = generateActualName(input.getKind(), super.getTimeStamp());

        // Retrieve the existing TestIndex from the map
        Map<String, TestIndex> indexMap = super.getInputIndexMap();
        TestIndex existingTestIndex = indexMap.get(actualKind);

        if (existingTestIndex == null) {
            throw new AssertionError("Cannot update schema - TestIndex not found for kind: " + actualKind);
        }

        if (!(existingTestIndex instanceof PersistentSchemaTestIndex)) {
            throw new AssertionError("Cannot update schema - TestIndex is not a PersistentSchemaTestIndex");
        }

        PersistentSchemaTestIndex testIndex = (PersistentSchemaTestIndex) existingTestIndex;

        // Update the schema file reference and force schema update
        testIndex.setSchemaFile(input.getSchemaFile());
        testIndex.updateSchema();
    }

    @Override
    public void i_ingest_records_with_the_for_a_given(String record, String dataGroup, String kind) {
        super.i_ingest_records_with_the_for_a_given(record.replaceFirst("_schema", ""), dataGroup, kind);
    }
    @Override
    public void i_should_get_object_in_search_response_without_hints_in_schema(String objectField, String index, String recordFile, String acl, String kind)
            throws Throwable {
        super.i_should_get_object_in_search_response_without_hints_in_schema(objectField, index, recordFile, acl, kind);
    }
    @Override
    public void i_should_get_the_documents_for_the_in_the_Elastic_Search_by_AsIngestedCoordinates (
            int expectedCount, String index, Double topPointX, Double bottomPointX, String pointX, Double topPointY, Double bottomPointY, String pointY) throws Throwable {
        super.i_should_get_the_documents_for_the_in_the_Elastic_Search_by_AsIngestedCoordinates(expectedCount, index, topPointX, bottomPointX, pointX, topPointY, bottomPointY, pointY);
    }

}
