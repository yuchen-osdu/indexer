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
package org.opengroup.osdu.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.java.Log;

import java.util.Map;

import static org.opengroup.osdu.util.Config.getIndexerBaseURL;

@Log
public class IndexerClientUtil {

    private final HTTPClient httpClient;
    private Map<String, String> headers;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IndexerClientUtil(HTTPClient httpClient) {
        this.httpClient = httpClient;
        headers = httpClient.getCommonHeader();
    }

    public void deleteIndex(String kind) {
        String url = getIndexerBaseURL() + "index?kind=" + kind;
        log.info("URL: " + url);
        HttpResponse response = httpClient.send("DELETE", url, "", headers, httpClient.getAccessToken());
        log.info(response.toString());
    }

    /**
     * Trigger a schema update for a given kind
     * @param kind the kind to update schema for
     * @param schemaUpdate the schema update request
     */
    public void triggerSchemaUpdate(String kind, Map<String, Object> schemaUpdate) throws Exception {
        String url = getIndexerBaseURL() + "_dps/task-handlers/schema-worker";
        log.info("Triggering schema update for kind: " + kind);

        String requestBody = objectMapper.writeValueAsString(Map.of(
            "kind", kind,
            "op", "upsert",
            "schema", schemaUpdate
        ));

        HttpResponse response = httpClient.send("POST", url, requestBody, headers, httpClient.getAccessToken());

        if (response.getStatus() != 200 && response.getStatus() != 201 && response.getStatus() != 202) {
            throw new RuntimeException("Schema update failed with status: " + response.getStatus());
        }

        log.info("Schema update triggered successfully for kind: " + kind);
    }

    /**
     * Ingest a record into the index
     * @param record the record to ingest
     */
    public void ingestRecord(Map<String, Object> record) throws Exception {
        String url = getIndexerBaseURL() + "records";
        log.info("Ingesting record: " + record.get("id"));

        String requestBody = objectMapper.writeValueAsString(new Map[]{record});

        HttpResponse response = httpClient.send("POST", url, requestBody, headers, httpClient.getAccessToken());

        if (response.getStatus() != 200 && response.getStatus() != 201 && response.getStatus() != 202) {
            throw new RuntimeException("Record ingestion failed with status: " + response.getStatus());
        }

        log.info("Record ingested successfully: " + record.get("id"));
    }
}
