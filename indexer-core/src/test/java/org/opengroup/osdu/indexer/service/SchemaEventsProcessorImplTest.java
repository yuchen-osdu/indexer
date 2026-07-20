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

package org.opengroup.osdu.indexer.service;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.indexer.SchemaInfo;
import org.opengroup.osdu.indexer.config.SchemaEventsListenerConfiguration;
import org.opengroup.osdu.indexer.logging.AuditLogger;
import org.opengroup.osdu.indexer.util.ElasticClientHandler;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class SchemaEventsProcessorImplTest {

    @Mock
    private IndexSchemaService indexSchemaService;
    @Mock
    private RequestScopedElasticsearchClient requestScopedClient;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private SchemaEventsListenerConfiguration schemaEventsListenerConfiguration;
    @InjectMocks
    private SchemaEventsProcessorImpl sut;
    private ElasticsearchClient restClient;

    @Before
    public void setup() {
        when(this.schemaEventsListenerConfiguration.isListenCreateEvent()).thenReturn(true);
        when(this.schemaEventsListenerConfiguration.isListenUpdateEvent()).thenReturn(true);
    }


    @Test
    public void should_process_validSchemaCreateEvent() throws IOException, URISyntaxException {
        SchemaInfo event1 = new SchemaInfo("slb:indexer:test-data--SchemaEventIntegration:1.0.0", "create");
        this.restClient = mock(ElasticsearchClient.class);
        when(requestScopedClient.getClient()).thenReturn(restClient);

        this.sut.processSchemaMessages(singletonList(event1));

        verify(this.indexSchemaService, times(1)).processSchemaUpsertEvent(this.restClient, event1.getKind());
        verify(this.auditLogger, times(1)).indexMappingUpsertSuccess(singletonList(event1.getKind()));
    }

    @Test
    public void should_process_validSchemaUpdateEvent() throws IOException, URISyntaxException {
        SchemaInfo event1 = new SchemaInfo("slb:indexer:test-data--SchemaEventIntegration:1.0.0", "update");
        this.restClient = mock(ElasticsearchClient.class);
        when(requestScopedClient.getClient()).thenReturn(restClient);

        this.sut.processSchemaMessages(singletonList(event1));

        verify(this.indexSchemaService, times(1)).processSchemaUpsertEvent(this.restClient, event1.getKind());
        verify(this.auditLogger, times(1)).indexMappingUpsertSuccess(singletonList(event1.getKind()));
    }

    @Test
    public void should_throwError_given_unsupportedEvent() {
        SchemaInfo event1 = new SchemaInfo("slb:indexer:test-data--SchemaEventIntegration:1.0.0", "delete");

        try {
            this.sut.processSchemaMessages(singletonList(event1));
            fail("Should throw exception");
        } catch (AppException e) {
            assertEquals(BAD_REQUEST.value(), e.getError().getCode());
            assertEquals("Error parsing schema events in request payload.", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    @Test
    public void should_throwError_given_schemaUpsertFails() throws IOException, URISyntaxException {
        SchemaInfo event1 = new SchemaInfo("slb:indexer:test-data--SchemaEventIntegration:1.0.0", "update");
        this.restClient = mock(ElasticsearchClient.class);
        when(requestScopedClient.getClient()).thenReturn(restClient);

        ErrorResponse errorResponse = ErrorResponse.of(
            responseBuilder -> responseBuilder.error(
                    ErrorCause.of(errorBuilder -> errorBuilder.reason("unknown error")))
                .status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
        );

        ElasticsearchException exception = new ElasticsearchException(null, errorResponse);

        doThrow(exception).when(this.indexSchemaService).processSchemaUpsertEvent(any(ElasticsearchClient.class), anyString());

        try {
            this.sut.processSchemaMessages(singletonList(event1));
            fail("Should throw exception");
        } catch (AppException e) {
            assertEquals(INTERNAL_SERVER_ERROR.value(), e.getError().getCode());
            verify(this.auditLogger, times(1)).indexMappingUpsertFail(singletonList(event1.getKind()));
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }
}
