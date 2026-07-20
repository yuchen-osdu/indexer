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

package org.opengroup.osdu.indexer.logging;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class AuditLoggerTest {

    @Mock
    private JaxRsDpsLog logger;
    @Mock
    private DpsHeaders headers;
    @Mock
    private ObjectProvider<HttpServletRequest> requestProvider;
    @Mock
    private HttpServletRequest httpServletRequest;
    @InjectMocks
    private AuditLogger sut;

    @Before
    public void setup() {
        when(this.headers.getUserEmail()).thenReturn("testUser");
        when(this.httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(this.httpServletRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(this.headers.getUserAuthorizedGroupName()).thenReturn("users.datalake.viewers");
        when(this.requestProvider.getIfAvailable()).thenReturn(this.httpServletRequest);
    }

    @Test
    public void should_createAuditLogEvent_when_indexCreateRecordSuccess() {
        this.sut.indexCreateRecordSuccess(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN001", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_indexCreateRecordPartialSuccess() {
        this.sut.indexCreateRecordPartialSuccess(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN001", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_indexCreateRecordFail() {
        this.sut.indexCreateRecordFail(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN001", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_indexUpdateRecordSuccess() {
        this.sut.indexUpdateRecordSuccess(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN002", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_indexUpdateRecordPartialSuccess() {
        this.sut.indexUpdateRecordPartialSuccess(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN002", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_indexUpdateRecordFail() {
        this.sut.indexUpdateRecordFail(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN002", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_indexDeleteRecordSuccess() {
        this.sut.indexDeleteRecordSuccess(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN003", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_indexDeleteRecordFail() {
        this.sut.indexDeleteRecordFail(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN003", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_getReindex() {
        this.sut.getReindex(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN007", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_copyIndex() {
        this.sut.copyIndex(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN008", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_getTaskStatus() {
        this.sut.getTaskStatus(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN009", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_getIndexCleanUpJobRun() {
        this.sut.getIndexCleanUpJobRun(Lists.newArrayList("anything"));

        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);

        verify(this.logger).audit(payloadCaptor.capture());

        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN010", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_indexMappingUpdateFail() {
        this.sut.indexMappingUpsertFail(Lists.newArrayList("anything"));
        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);
        verify(this.logger).audit(payloadCaptor.capture());
        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN0011", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_indexMappingUpdateSuccess() {
        this.sut.indexMappingUpsertSuccess(Lists.newArrayList("anything"));
        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);
        verify(this.logger).audit(payloadCaptor.capture());
        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN0011", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_configurePartitionSuccess() {
        this.sut.getConfigurePartition(Lists.newArrayList("anything"));
        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);
        verify(this.logger).audit(payloadCaptor.capture());
        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("IN0012", ((Map) payload.get("auditLog")).get("actionId"));
        assertEquals("testUser", ((Map) payload.get("auditLog")).get("user"));
    }

    @Test
    public void should_createAuditLogEvent_when_httpServletRequestIsNotAvailable() {
        when(this.requestProvider.getIfAvailable()).thenReturn(null);
        this.sut.getConfigurePartition(Lists.newArrayList("anything"));
        ArgumentCaptor<AuditPayload> payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);
        verify(this.logger).audit(payloadCaptor.capture());
        AuditPayload payload = payloadCaptor.getValue();
        assertEquals("0.0.0.0", ((Map) payload.get("auditLog")).get("userIpAddress"));
        assertEquals("unknown", ((Map) payload.get("auditLog")).get("userAgent"));
    }
}
