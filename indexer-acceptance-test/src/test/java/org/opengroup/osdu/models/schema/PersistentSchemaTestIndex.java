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

package org.opengroup.osdu.models.schema;

import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.common.SchemaServiceRecordSteps;
import org.opengroup.osdu.core.test.auth.UserType;
import org.opengroup.osdu.core.test.client.IndexerClient;
import org.opengroup.osdu.core.test.client.SchemaClient;
import org.opengroup.osdu.core.test.client.StringHttpClient;
import org.opengroup.osdu.core.test.client.model.schema.SchemaIdentity;
import org.opengroup.osdu.core.test.client.model.schema.SchemaModel;
import org.opengroup.osdu.core.test.util.TestFileUtil;
import org.opengroup.osdu.core.test.client.ElasticClient;
import org.opengroup.osdu.models.TestIndex;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A {@link TestIndex} backed by a schema registered with the Schema service.
 *
 * <p>Uses the os-core-test {@link SchemaClient} (Schema v1 API) instead of the previous in-repo
 * Spring {@code RestTemplate}-based client, and the os-core-test schema model types.
 */
@Slf4j
public class PersistentSchemaTestIndex extends TestIndex {

    private final SchemaClient schemaClient;
    private final SchemaServiceRecordSteps recordSteps;
    private SchemaModel schemaModel;

    public PersistentSchemaTestIndex(ElasticClient elasticClient, IndexerClient indexerClient,
                                     StringHttpClient client, SchemaServiceRecordSteps recordSteps) {
        super(elasticClient, indexerClient);
        this.schemaClient = new SchemaClient(client, UserType.PRIVILEGED_USER);
        this.recordSteps = recordSteps;
    }

    @Override
    public void setupSchema() {
        loadAndPrepareSchema();
        log.info("Setting up the schema={}", schemaModel.getSchemaInfo().getSchemaIdentity());
        schemaClient.createIfNotExist(schemaModel);
        log.info("Finished setting up the schema={}", schemaModel.getSchemaInfo().getSchemaIdentity());
    }

    private void loadAndPrepareSchema() {
        this.schemaModel = readSchemaFromJson();
        SchemaIdentity schemaIdentity = schemaModel.getSchemaInfo().getSchemaIdentity();
        log.info("Read the schema={}", schemaIdentity.getId());
        String timeStamp = recordSteps.getTimeStamp();
        schemaIdentity.setAuthority(recordSteps.generateActualNameWithoutTs(schemaIdentity.getAuthority()));
        schemaIdentity.setSource(recordSteps.generateActualName(schemaIdentity.getSource(), timeStamp));
        schemaIdentity.setId(toSchemaId(schemaIdentity));
        log.info("Prepared the schema identity={}", schemaIdentity.getId());
    }

    private String toSchemaId(SchemaIdentity schemaIdentity) {
        return String.format("%s:%s:%s:%s.%s.%s",
            schemaIdentity.getAuthority(),
            schemaIdentity.getSource(),
            schemaIdentity.getEntityType(),
            schemaIdentity.getSchemaVersionMajor(),
            schemaIdentity.getSchemaVersionMinor(),
            schemaIdentity.getSchemaVersionPatch());
    }

    @Override
    public void deleteSchema(String kind) {
        // The DELETE API is not supported in the Schema service.
        // In order not to overwhelm a DB with a lots of test schemas
        // the integration tests create/update a schema per schema file if the schema does not exists
        // If a developer updates the schema manually, the developer is supposed to update its version as well
    }

    private SchemaModel readSchemaFromJson() {
        try {
            return TestFileUtil.readTestDataFile(getSchemaFile(), SchemaModel.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public SchemaModel getSchemaModel() {
        return schemaModel;
    }

}
