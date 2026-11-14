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

package org.opengroup.osdu.models;

import com.google.gson.Gson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.common.TestsBase;
import org.opengroup.osdu.core.test.client.IndexerClient;
import org.opengroup.osdu.core.test.client.SchemaClient;
import org.opengroup.osdu.core.test.client.StorageClient;
import org.opengroup.osdu.core.test.client.model.schema.SchemaIdentity;
import org.opengroup.osdu.core.test.client.model.schema.SchemaModel;
import org.opengroup.osdu.core.test.util.TestFileUtil;
import org.opengroup.osdu.core.test.client.ElasticClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Data
public class TestIndex {

    private String kind;
    private String index;
    private String mappingFile;
    private String recordFile;
    private int recordCount;
    private String schemaFile;
    private String[] dataGroup;
    private String[] viewerGroup;
    private String[] ownerGroup;
    private ElasticClient elasticClient;
    private IndexerClient indexerClient;
    private SchemaClient schemaClient;
    private StorageClient storageClient;
    private Gson gson = new Gson();

    /**
     * Creates a fully-configured test index with the shared typed clients from
     * {@link org.opengroup.osdu.common.TestsBase}.
     */
    public TestIndex(ElasticClient elasticClient, IndexerClient indexerClient,
                     SchemaClient schemaClient, StorageClient storageClient) {
        this.elasticClient = elasticClient;
        this.indexerClient = indexerClient;
        this.schemaClient = schemaClient;
        this.storageClient = storageClient;
    }

    /**
     * Reduced constructor for subclasses that override {@link #setupSchema()} and
     * {@link #deleteSchema(String)} and therefore do not need the schema/storage clients.
     */
    protected TestIndex(ElasticClient elasticClient, IndexerClient indexerClient) {
        this(elasticClient, indexerClient, null, null);
    }

    public void setupIndex() {
        this.addIndex();
        List<Map<String, Object>> records = getRecordsFromTestFile();
        this.recordCount = this.elasticClient.indexRecords(this.index, this.kind, records);
    }

    /**
     * Creates the schema via the Schema v1 API ({@link SchemaClient}).
     *
     * <p>The schema file is read directly into a {@link SchemaModel}. The schema identity is then
     * set from the dynamically generated {@link #kind} (format {@code authority:source:entityType:major.minor.patch})
     * so that the registered schema matches exactly the kind used to index records in this test.
     */
    public void setupSchema() {
        try {
            SchemaModel schemaModel = TestFileUtil.readTestDataFile(getSchemaFile(), SchemaModel.class);
            String[] kindParts = this.kind.split(":");
            String[] versionParts = kindParts[3].split("\\.");
            SchemaIdentity identity = schemaModel.getSchemaInfo().getSchemaIdentity();
            identity.setAuthority(kindParts[0]);
            identity.setSource(kindParts[1]);
            identity.setEntityType(kindParts[2]);
            identity.setSchemaVersionMajor(versionParts[0]);
            identity.setSchemaVersionMinor(versionParts[1]);
            identity.setSchemaVersionPatch(versionParts[2]);
            schemaClient.createIfNotExist(schemaModel);
        } catch (Exception e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    /**
     * Removes the schema record from the Storage v2 service via {@link StorageClient}.
     *
     * @param kind the schema/kind identifier to delete
     */
    public void deleteSchema(String kind) {
        storageClient.deleteRecord(kind);
    }

    public void addIndex() {
        this.elasticClient.createIndex(this.index, this.getIndexMappingFromJson());
    }

    public void cleanupIndex(String kind) {
        this.indexerClient.deleteIndex(kind);
    }

    private String getRecordFile() {
        return String.format("%s.json", this.recordFile);
    }

    private String getMappingFile() {
        return String.format("%s.mapping", this.mappingFile);
    }

    protected String getSchemaFile() {
        return String.format("%s.schema.json", this.schemaFile);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getRecordsFromTestFile() {
        try {
            List<Map<String, Object>> records =
                (List<Map<String, Object>>) TestFileUtil.readTestDataFile(getRecordFile(), List.class);
            for (Map<String, Object> testRecord : records) {
                testRecord.put("kind", this.kind);
                testRecord.put("legal", generateLegalTag());
                testRecord.put("x-acl", dataGroup);
                Acl acl = Acl.builder().viewers(viewerGroup).owners(ownerGroup).build();
                testRecord.put("acl", acl);
            }
            return records;
        } catch (Exception ex) {
            throw new AssertionError(ex.getMessage());
        }
    }

    private String getIndexMappingFromJson() {
        try {
            String fileContent = TestFileUtil.readTestDataFile(getMappingFile());
            return gson.toJson(gson.fromJson(fileContent, Object.class));
        } catch (Exception e) {
            throw new AssertionError(e.getMessage());
        }
    }

    private Legal generateLegalTag() {
        return TestsBase.generateSuiteLegalTag();
    }
}
