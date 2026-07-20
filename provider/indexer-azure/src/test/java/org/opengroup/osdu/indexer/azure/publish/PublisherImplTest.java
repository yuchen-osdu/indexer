/*
 * Copyright 2017-2025, Microsoft
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

package org.opengroup.osdu.indexer.azure.publish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.Test;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.azure.servicebus.ITopicClientFactory;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PublisherImplTest {

    private static String serviceBusTopicField = "serviceBusTopic";
    private static String serviceBusTopicValue = "recordChangeTopic";
    private static String shouldPublishToServiceBusTopicField = "shouldPublishToServiceBusTopic";
    private static Boolean shouldPublishToServiceBusTopicValue = true;
    private static String partitionId = "opendes";

    @Mock
    public ITopicClientFactory topicClientFactory;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private JobStatus jobStatus;

    @InjectMocks
    public PublisherImpl sut;

    @Test
    public void should_invoke_getPartitionIdOfdpsHeaders_when_publishStatusChangedTagsToTopic_isCalled() throws Exception {
        ReflectionTestUtils.setField(sut,serviceBusTopicField,serviceBusTopicValue);
        ReflectionTestUtils.setField(sut,shouldPublishToServiceBusTopicField,shouldPublishToServiceBusTopicValue);
        when(dpsHeaders.getPartitionId()).thenReturn(partitionId);

        sut.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        verify(dpsHeaders, times(3)).getPartitionId();
    }

    @Test
    public void should_invoke_getAccountIdOfDpsHeaders_when_publishStatusChangedTagsToTopic_isCalledWithGetPartitionIdReturningEmptyString() throws Exception {
        ReflectionTestUtils.setField(sut,serviceBusTopicField,serviceBusTopicValue);
        ReflectionTestUtils.setField(sut,shouldPublishToServiceBusTopicField,shouldPublishToServiceBusTopicValue);
        when(dpsHeaders.getPartitionId()).thenReturn("");

        sut.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        verify(dpsHeaders, times(1)).getAccountId();
    }

    @Test
    public void should_invoke_getClientOftopicClientFactory_when_publishStatusChangedTagsToTopic_isCalled() throws Exception {
        ReflectionTestUtils.setField(sut,serviceBusTopicField,serviceBusTopicValue);
        ReflectionTestUtils.setField(sut,shouldPublishToServiceBusTopicField,shouldPublishToServiceBusTopicValue);
        when(dpsHeaders.getPartitionId()).thenReturn(partitionId);

        sut.publishStatusChangedTagsToTopic(dpsHeaders, jobStatus);

        verify(topicClientFactory, times(1)).getClient(partitionId, serviceBusTopicValue);
    }
}
