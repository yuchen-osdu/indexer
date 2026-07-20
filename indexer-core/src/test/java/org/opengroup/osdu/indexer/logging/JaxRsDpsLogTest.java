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

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.Request;
import org.opengroup.osdu.core.common.logging.ILogger;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@Ignore
@RunWith(SpringRunner.class)
public class JaxRsDpsLogTest {

    @Mock
    private ILogger log;

    @InjectMocks
    private JaxRsDpsLog sut;

    @Test
    public void should_includeAllHeadersExceptAuth_when_writingALog() {
        this.sut.info("msg");

        ArgumentCaptor<Map> argument = ArgumentCaptor.forClass(Map.class);
        verify(this.log).info(any(), any(), argument.capture());
        assertEquals("cor123", argument.getValue().get(DpsHeaders.CORRELATION_ID));
        assertFalse(argument.getValue().containsKey("authorization"));

    }

    @Test
    public void should_writeToAuditLogWithGivenPayload_on_auditRequests() {
        AuditPayload pl = new AuditPayload();
        this.sut.audit(pl);
        verify(this.log).audit(eq("search.audit"), eq(pl), any());
    }

    @Test
    public void should_writeToRequestLogWithGivenHttpObj_on_requestLog() {
        Request http = Request.builder().build();
        this.sut.request(http);
        verify(this.log).request(eq("search.request"), eq(http), any());
    }

    @Test
    public void should_writeToAppLogWithGivenMsg_on_errorLogrequest() {
        this.sut.error("error");
        verify(this.log).error(eq("search.app"), eq("error"), any());
    }

    @Test
    public void should_writeToAppLogWithGivenMsg_on_warningLogrequest() {
        this.sut.warning("warning");
        verify(this.log).warning(eq("search.app"), eq("warning"), any());
    }

    @Test
    public void should_writeToAppLogWithGivenMsgArray_on_warningLogrequest() {
        List<String> warnings = Arrays.asList("Mismatch", "OutOfRange");
        String output = "0: Mismatch" + System.lineSeparator() + "1: OutOfRange" + System.lineSeparator();
        this.sut.warning(warnings);
        verify(this.log).warning(eq("search.app"), eq(output), any());
    }

    @Test
    public void should_notWriteToAppLogWithGivenNullMsgArray_on_warningLogrequest() {
        List<String> warnings = null;
        this.sut.warning(warnings);
        verify(this.log, never()).warning(eq("search.app"), eq(null), any());
    }

    @Test
    public void should_notWriteToAppLogWithGivenEmptyMsgArray_on_warningLogrequest() {
        List<String> warnings = new ArrayList<>();
        this.sut.warning(warnings);
        verify(this.log, never()).warning(eq("search.app"), eq(""), any());
    }
}
