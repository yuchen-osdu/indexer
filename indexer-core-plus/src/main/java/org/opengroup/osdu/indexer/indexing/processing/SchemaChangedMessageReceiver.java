/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.indexer.indexing.processing;

import com.google.gson.Gson;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.model.indexer.SchemaChangedMessages;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.opengroup.osdu.indexer.api.RecordIndexerApi;
import org.opengroup.osdu.indexer.indexing.scope.ThreadDpsHeaders;
import org.springframework.http.ResponseEntity;

@Slf4j
public class SchemaChangedMessageReceiver extends IndexerOqmMessageReceiver {

  private final RecordIndexerApi recordIndexerApi;
  private final Gson gson = new Gson();

  public SchemaChangedMessageReceiver(ThreadDpsHeaders dpsHeaders, TokenProvider tokenProvider,
      RecordIndexerApi recordIndexerApi) {
    super(dpsHeaders, tokenProvider);
    this.recordIndexerApi = recordIndexerApi;
  }

  @Override
  protected void sendMessage(OqmMessage oqmMessage) throws Exception {
    SchemaChangedMessages schemaChangedMessage = getSchemaWorkerRequestBody(oqmMessage);
    log.debug("Schema changed job message body: {}", schemaChangedMessage);
    ResponseEntity<?> schemaChangeResponse = recordIndexerApi.schemaWorker(schemaChangedMessage);
    log.debug("Schema changed job status: {}", schemaChangeResponse);
  }

  private SchemaChangedMessages getSchemaWorkerRequestBody(OqmMessage oqmMessage) {
    SchemaChangedMessages schemaChangedMessages = new SchemaChangedMessages();
    schemaChangedMessages.setMessageId(dpsHeaders.getCorrelationId());
    schemaChangedMessages.setData(oqmMessage.getData());
    schemaChangedMessages.setAttributes(oqmMessage.getAttributes());
    schemaChangedMessages.setPublishTime(LocalDateTime.now().toString());
    return schemaChangedMessages;
  }
}
