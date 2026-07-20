// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.indexer.api;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.indexer.RecordReindexRequest;
import org.opengroup.osdu.core.common.model.indexer.SchemaChangedMessages;
import org.opengroup.osdu.core.common.model.indexer.SchemaInfo;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.SwaggerDoc;
import org.opengroup.osdu.indexer.service.IndexerService;
import org.opengroup.osdu.indexer.service.ReindexService;
import org.opengroup.osdu.indexer.service.SchemaEventsProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

@Log
@RestController
@RequestMapping("/_dps/task-handlers")
@RequestScope
public class RecordIndexerApi {

    @Inject
    private IRequestInfo requestInfo;
    @Inject
    private IndexerService indexerService;
    @Inject
    private ReindexService reIndexService;
    @Inject
    private SchemaEventsProcessor eventsProcessingService;

    // THIS IS AN INTERNAL USE API ONLY
    // THAT MEANS WE DON'T DOCUMENT IT IN SWAGGER, ACCESS IS LIMITED TO CLOUD TASK QUEUE CALLS ONLY
    @PostMapping(path = "/index-worker", consumes = "application/json")
    @Operation(hidden = true, summary = "", description = "")
    public ResponseEntity<JobStatus> indexWorker(
             @NotNull(message = SwaggerDoc.REQUEST_VALIDATION_NOT_NULL_BODY)
             @Valid @RequestBody RecordChangedMessages recordChangedMessages) throws Exception {

        populateCorrelationIdIfExist(recordChangedMessages);

        verifyDataPartitionId(recordChangedMessages);

        try {
            Type listType = new TypeToken<List<RecordInfo>>() {}.getType();
            List<RecordInfo> recordInfos = new Gson().fromJson(recordChangedMessages.getData(), listType);

            if (recordInfos.size() == 0) {
                log.info("none of record-change message can be deserialized");
                return new ResponseEntity(HttpStatus.OK);
            }
            this.indexerService.processRecordChangedMessages(recordChangedMessages, recordInfos);
            return new ResponseEntity(HttpStatus.OK);
        } catch (AppException e) {
            throw e;
        } catch (JsonParseException e) {
            throw new AppException(org.apache.http.HttpStatus.SC_BAD_REQUEST, "Request payload parsing error", "Unable to parse request payload.", e);
        } catch (Exception e) {
            throw new AppException(org.apache.http.HttpStatus.SC_BAD_REQUEST, "Unknown error", "An unknown error has occurred.", e);
        }
    }

    private void verifyDataPartitionId(RecordChangedMessages recordChangedMessages) {
        if (recordChangedMessages.missingAccountId()) {
            throw new AppException(org.apache.http.HttpStatus.SC_BAD_REQUEST, "Invalid tenant",
                        String.format("Required header: '%s' not found", DpsHeaders.DATA_PARTITION_ID));
        }
    }

    private void populateCorrelationIdIfExist(RecordChangedMessages recordChangedMessages) {
        if (recordChangedMessages.hasCorrelationId()) {
            this.requestInfo.getHeaders().put(DpsHeaders.CORRELATION_ID, recordChangedMessages.getCorrelationId());
        }
    }

    // THIS IS AN INTERNAL USE API ONLY
    // THAT MEANS WE DON'T DOCUMENT IT IN SWAGGER, ACCESS IS LIMITED TO CLOUD TASK QUEUE CALLS ONLY
    @PostMapping("/reindex-worker")
    @Operation(hidden = true, summary = "", description = "")
    public ResponseEntity<?> reindex(
            @RequestBody @NotNull(message = SwaggerDoc.REQUEST_VALIDATION_NOT_NULL_BODY)
            @Valid RecordReindexRequest recordReindexRequest) {
        return new ResponseEntity<>(reIndexService.reindexKind(recordReindexRequest, false, false), HttpStatus.OK);
    }

    // THIS IS AN INTERNAL USE API ONLY
    // THAT MEANS WE DON'T DOCUMENT IT IN SWAGGER, ACCESS IS LIMITED TO CLOUD TASK QUEUE CALLS ONLY
    @PostMapping("/schema-worker")
    @Operation(hidden = true, summary = "", description = "")
    public ResponseEntity<?> schemaWorker(
            @NotNull(message = SwaggerDoc.REQUEST_VALIDATION_NOT_NULL_BODY)
            @Valid @RequestBody SchemaChangedMessages schemaChangedMessage) throws IOException {
        if (schemaChangedMessage == null) {
            log.warning("schema change messages is null");
        }

        if (schemaChangedMessage.missingAccountId()) {
            throw new AppException(org.apache.http.HttpStatus.SC_BAD_REQUEST, "Invalid tenant",
                    String.format("Required header: '%s' not found", DpsHeaders.DATA_PARTITION_ID));
        }

        try {
            Type listType = new TypeToken<List<SchemaInfo>>() {}.getType();
            List<SchemaInfo> schemaInfos = new Gson().fromJson(schemaChangedMessage.getData(), listType);

            if (schemaInfos.size() == 0) {
                log.warning("none of schema-change message can be deserialized");
                return new ResponseEntity(org.springframework.http.HttpStatus.OK);
            }
            this.eventsProcessingService.processSchemaMessages(schemaInfos);
            return new ResponseEntity(HttpStatus.OK);
        } catch (JsonParseException e) {
            throw new AppException(org.apache.http.HttpStatus.SC_BAD_REQUEST, "Request payload parsing error", "Unable to parse request payload.", e);
        }
    }
}
