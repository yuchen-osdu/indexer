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

package org.opengroup.osdu.indexer.model;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(SpringRunner.class)
public class RecordChagedMessagesTest {

    private RecordChangedMessages recordChangedMessages;
    private final String ACCOUNT_ID = "test-tenant";
    private final String CORRELATION_ID = "xxxxxx";

    @Before
    public void setup() {
        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.ACCOUNT_ID, ACCOUNT_ID);
        headers.put(DpsHeaders.CORRELATION_ID, CORRELATION_ID);
        recordChangedMessages = RecordChangedMessages.builder()
                .attributes(headers).build();
    }

    @Test
    public void should_returnCorrectAccountID_getAccountIdTest() {
        assertEquals(ACCOUNT_ID, recordChangedMessages.getDataPartitionId());
    }

    @Test
    public void should_returnFalse_ifThereIsAccountID_missingAccountIdTest() {
        assertFalse(recordChangedMessages.missingAccountId());
    }

    @Test
    public void should_returnTrue_ifThereIsNoAccountID_missingAccountIdTest() {
        recordChangedMessages.setAttributes(null);
        assertTrue(recordChangedMessages.missingAccountId());
    }

    @Test
    public void should_returnCorrectCorrelationId_getCorrelationIdTest() {
        assertEquals(CORRELATION_ID, recordChangedMessages.getCorrelationId());
    }

    @Test
    public void should_returnTrue_ifThereIsCorrelationId_hasCorrelationIdTest() {
        Assert.assertTrue(recordChangedMessages.hasCorrelationId());
    }

    @Test
    public void should_returnFalse_ifThereIsNoCorrelationId_hasCorrelationIdTest() {
        RecordChangedMessages recordMessages = new RecordChangedMessages();
        assertFalse(recordMessages.hasCorrelationId());
    }

    @Test
    public void should_returnFalse_ifEmptyAccountIDTest() {
        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.ACCOUNT_ID, "");
        headers.put(DpsHeaders.CORRELATION_ID, "");
        RecordChangedMessages invalidMessages = RecordChangedMessages.builder()
                .attributes(headers).build();

        assertTrue(invalidMessages.missingAccountId());
        assertFalse(invalidMessages.hasCorrelationId());
    }
}
