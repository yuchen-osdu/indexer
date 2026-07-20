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
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import lombok.Data;

import org.opengroup.osdu.util.HTTPClient;
import org.opengroup.osdu.util.HttpResponse;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.util.ElasticUtils;
import org.opengroup.osdu.util.FileHandler;
import org.opengroup.osdu.util.IndexerClientUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opengroup.osdu.util.Config.*;
import static org.opengroup.osdu.util.HTTPClient.indentatedResponseBody;

@Data
public class TestIndex {
    private static final Logger LOGGER = Logger.getLogger(TestIndex.class.getName());
    private String kind;
    private String index;
    private String mappingFile;
    private String recordFile;
    private int recordCount;
    private String schemaFile;
    private String[] dataGroup;
    private String[] viewerGroup;
    private String[] ownerGroup;
    private HTTPClient httpClient;
    private Map<String, String> headers;
    private ElasticUtils elasticUtils;
    private IndexerClientUtil indexerClientUtil;
    private Gson gson = new Gson();

    public TestIndex(ElasticUtils elasticUtils){
        this.elasticUtils = elasticUtils;
    }

    public void setHttpClient(HTTPClient httpClient) {
        this.httpClient = httpClient;
        headers = httpClient.getCommonHeader();
        this.indexerClientUtil = new IndexerClientUtil(this.httpClient);
    }

    public void setupIndex() {
        this.addIndex();
        List<Map<String, Object>> records = getRecordsFromTestFile();
        this.recordCount = this.elasticUtils.indexRecords(this.index, this.kind, records);
    }

    public void setupSchema() {
        HttpResponse httpResponse = this.httpClient.send("POST", getStorageBaseURL() + "schemas", this.getStorageSchemaFromJson(), headers, httpClient.getAccessToken());
        if (httpResponse.getType() != null) {
            LOGGER.info(String.format("Response status: %s, type: %s\nResponse body: %s", httpResponse.getStatus(), httpResponse.getType(), indentatedResponseBody(httpResponse.getEntity(String.class))));
        }
    }

    public void deleteSchema(String kind) {
        HttpResponse httpResponse = this.httpClient.send("DELETE", getStorageBaseURL() + "schemas/" + kind, null, headers, httpClient.getAccessToken());
        assertEquals(204, httpResponse.getStatus());
        if (httpResponse.getType() != null)
            LOGGER.info(String.format("Response status: %s, type: %s", httpResponse.getStatus(), httpResponse.getType()));
    }

    public void addIndex() {
        this.elasticUtils.createIndex(this.index, this.getIndexMappingFromJson());
    }

    public void cleanupIndex(String kind) {
        this.indexerClientUtil.deleteIndex(kind);
    }

    private String getRecordFile() {
        return String.format("%s.json", this.recordFile);
    }

    private String getMappingFile() {
        return String.format("%s.mapping", this.mappingFile);
    }

    protected String getSchemaFile() {
        return String.format("%s.schema", this.schemaFile);
    }

    private List<Map<String, Object>> getRecordsFromTestFile() {
         try {
            String fileContent = FileHandler.readFile(getRecordFile());
            List<Map<String, Object>> records = new Gson().fromJson(
                    fileContent, new TypeToken<List<Map<String,Object>>>() {}.getType());

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
            String fileContent = FileHandler.readFile(getMappingFile());
            JsonElement json = gson.fromJson(fileContent, JsonElement.class);
            return gson.toJson(json);
        } catch (Exception e) {
            throw new AssertionError(e.getMessage());
        }
    }

    private String getStorageSchemaFromJson() {
        try {
            String fileContent = FileHandler.readFile(getSchemaFile());
            fileContent = fileContent.replaceAll("KIND_VAL", this.kind);
            JsonElement json = gson.fromJson(fileContent, JsonElement.class);
            return gson.toJson(json);
        } catch (Exception e) {
            throw new AssertionError(e.getMessage());
        }
    }

    private Legal generateLegalTag() {
        Legal legal = new Legal();
        Set<String> legalTags = new HashSet<>();
        legalTags.add(getLegalTag());
        legal.setLegaltags(legalTags);
        Set<String> otherRelevantCountries = new HashSet<>();
        otherRelevantCountries.add(getOtherRelevantDataCountries());
        legal.setOtherRelevantDataCountries(otherRelevantCountries);
        return legal;
    }

}
