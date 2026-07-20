/**
* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opengroup.osdu.indexer.aws.publish;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.aws.v2.sns.AmazonSNSConfig;
import org.opengroup.osdu.core.aws.v2.sns.PublishRequestBuilder;
import org.opengroup.osdu.core.common.model.indexer.RecordStatus;
import org.opengroup.osdu.core.aws.v2.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.indexer.aws.IndexerAwsApplication;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.springframework.boot.test.context.SpringBootTest;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes = {IndexerAwsApplication.class})
public class PublisherImplTest {

    private final String indexer_sns_topic_arn = "indexer_sns_topic_arn";

    @InjectMocks
    private PublisherImpl publisher = new PublisherImpl();

    @Mock
    SnsClient snsClient;

    @Test
    public void publishStatusChangedTagsToTopic() throws Exception {
        // Arrange
        DpsHeaders headers = new DpsHeaders();
        JobStatus jobStatus = new JobStatus();
        Mockito.when(snsClient.publish(Mockito.any(PublishRequest.class)))
                .thenReturn(Mockito.any(PublishResponse.class));

        PublishRequestBuilder<RecordStatus> publishRequestBuilder = new PublishRequestBuilder<>();
        publishRequestBuilder.setGeneralParametersFromHeaders(headers);

        PublishRequest publishRequest = publishRequestBuilder.generatePublishRequest(null, null, jobStatus.getStatusesList());
        // Act
        publisher.publishStatusChangedTagsToTopic(headers, jobStatus);

        // Assert
        Mockito.verify(snsClient, Mockito.times(1)).publish(Mockito.eq(publishRequest));
    }


    @Test
    public void go_through_init_DLQ() throws Exception  {

        try (MockedConstruction<K8sLocalParameterProvider> provider = Mockito.mockConstruction(K8sLocalParameterProvider.class, (mock, context) -> {
                                                                                                                when(mock.getParameterAsString(eq("INDEXER_SNS_TOPIC_ARN"))).thenReturn(indexer_sns_topic_arn);
                                                                                                            })) {

            try (MockedConstruction<AmazonSNSConfig> sns = Mockito.mockConstruction(AmazonSNSConfig.class, (mock1, context) -> {
                                                                                                                when(mock1.AmazonSNS()).thenReturn(snsClient);
                                                                                                            })) {

                publisher.init();
                JobStatus jobStatus = new JobStatus();

                // Arrange
                DpsHeaders headers = new DpsHeaders();

                PublishRequestBuilder<RecordStatus> publishRequestBuilder = new PublishRequestBuilder<>();
                publishRequestBuilder.setGeneralParametersFromHeaders(headers);

                PublishRequest publishRequest = publishRequestBuilder.generatePublishRequest(null, indexer_sns_topic_arn, jobStatus.getStatusesList());
                // Act
                publisher.publishStatusChangedTagsToTopic(headers, jobStatus);

                // Assert
                Mockito.verify(snsClient, Mockito.times(1)).publish(Mockito.eq(publishRequest));

            }

        }

    }

}
