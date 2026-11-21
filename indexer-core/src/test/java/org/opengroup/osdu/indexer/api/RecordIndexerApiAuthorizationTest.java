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

package org.opengroup.osdu.indexer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.model.entitlements.AuthorizationResponse;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.RecordReindexRequest;
import org.opengroup.osdu.core.common.model.indexer.SchemaChangedMessages;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.core.common.model.search.SearchServiceRole;
import org.opengroup.osdu.core.common.provider.interfaces.IAuthorizationService;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.middleware.AuthorizationFilter;
import org.opengroup.osdu.indexer.service.IndexerService;
import org.opengroup.osdu.indexer.service.ReindexService;
import org.opengroup.osdu.indexer.service.SchemaEventsProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

@SpringJUnitWebConfig(RecordIndexerApiAuthorizationTest.MethodSecurityConfig.class)
class RecordIndexerApiAuthorizationTest {

    private static final String PARTITION = "opendes";

    @Configuration
    @EnableMethodSecurity
    @Import({RecordIndexerApi.class, AuthorizationFilter.class})
    static class MethodSecurityConfig {
    }

    @Autowired
    private RecordIndexerApi sut;

    @MockitoBean
    private IAuthorizationService authorizationService;
    @MockitoBean
    private DpsHeaders dpsHeaders;
    @MockitoBean
    private IRequestInfo requestInfo;
    @MockitoBean
    private IndexerService indexerService;
    @MockitoBean
    private ReindexService reindexService;
    @MockitoBean
    private SchemaEventsProcessor schemaEventsProcessor;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        when(requestInfo.getHeaders()).thenReturn(dpsHeaders);
    }

    @Test
    void should_rejectAllWorkerEndpoints_when_callerLacksSearchAdminRole() {
        when(authorizationService.authorizeAny(any(), eq(SearchServiceRole.ADMIN)))
            .thenThrow(new AppException(HttpStatus.FORBIDDEN.value(), "Access denied", "The user is not authorized to perform this action"));

        AppException index = assertThrows(AppException.class, () -> sut.indexWorker(recordChangedMessages()));
        AppException reindex = assertThrows(AppException.class, () -> sut.reindex(reindexRequest()));
        AppException schema = assertThrows(AppException.class, () -> sut.schemaWorker(schemaChangedMessages()));

        assertEquals(HttpStatus.FORBIDDEN.value(), index.getError().getCode());
        assertEquals(HttpStatus.FORBIDDEN.value(), reindex.getError().getCode());
        assertEquals(HttpStatus.FORBIDDEN.value(), schema.getError().getCode());
        verifyNoInteractions(indexerService, reindexService, schemaEventsProcessor);
    }

    @Test
    void should_invokeWorkers_when_callerHasSearchAdminRole_withoutSpringAuthentication() throws Exception {
        when(authorizationService.authorizeAny(any(), eq(SearchServiceRole.ADMIN)))
            .thenReturn(AuthorizationResponse.builder().user("svc@example.com").build());

        assertEquals(HttpStatus.OK, sut.indexWorker(recordChangedMessages()).getStatusCode());
        assertEquals(HttpStatus.OK, sut.reindex(reindexRequest()).getStatusCode());
        assertEquals(HttpStatus.OK, sut.schemaWorker(schemaChangedMessages()).getStatusCode());

        verify(indexerService).processRecordChangedMessages(any(), any());
        verify(reindexService).reindexKind(any(), anyBoolean(), anyBoolean());
        verify(schemaEventsProcessor).processSchemaMessages(any());
    }

    @Test
    void should_skipAuthorization_when_subscriberCallsInProcessEntryPoints() throws Exception {
        assertEquals(HttpStatus.OK, sut.processRecordChangedMessages(recordChangedMessages()).getStatusCode());
        assertEquals(HttpStatus.OK, sut.processSchemaChangedMessages(schemaChangedMessages()).getStatusCode());

        verifyNoInteractions(authorizationService);
        verify(indexerService).processRecordChangedMessages(any(), any());
        verify(schemaEventsProcessor).processSchemaMessages(any());
    }

    private static RecordChangedMessages recordChangedMessages() {
        return RecordChangedMessages.builder()
            .data("[{\"id\":\"opendes:wellbore:1\",\"kind\":\"opendes:wks:wellbore:1.0.0\",\"op\":\"create\"}]")
            .attributes(Map.of(DpsHeaders.DATA_PARTITION_ID, PARTITION))
            .build();
    }

    private static RecordReindexRequest reindexRequest() {
        return RecordReindexRequest.builder().kind("opendes:wks:wellbore:1.0.0").build();
    }

    private static SchemaChangedMessages schemaChangedMessages() {
        SchemaChangedMessages messages = new SchemaChangedMessages();
        messages.setData("[{\"kind\":\"opendes:wks:wellbore:1.0.0\",\"op\":\"create\"}]");
        messages.setAttributes(Map.of(DpsHeaders.DATA_PARTITION_ID, PARTITION));
        return messages;
    }
}
