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

import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.SnsClient;

import org.opengroup.osdu.core.aws.v2.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.aws.v2.sns.AmazonSNSConfig;
import org.opengroup.osdu.core.aws.v2.sns.PublishRequestBuilder;
import org.opengroup.osdu.core.common.model.indexer.RecordStatus;
import org.opengroup.osdu.indexer.provider.interfaces.IPublisher;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.opengroup.osdu.core.aws.v2.ssm.K8sLocalParameterProvider;
import jakarta.inject.Inject;

@Component
public class PublisherImpl implements IPublisher {

    SnsClient snsClient;
    private String amazonSNSTopic;

    @Value("${aws.region}")
    private String amazonSNSRegion;

    @Value("${OSDU_TOPIC}")
    private String osduIndexerTopic;

    @Inject
    public void init() throws K8sParameterNotFoundException {
        AmazonSNSConfig snsConfig = new AmazonSNSConfig(amazonSNSRegion);
        snsClient = snsConfig.AmazonSNS();
        K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
        amazonSNSTopic = provider.getParameterAsString("INDEXER_SNS_TOPIC_ARN");
    }

    public void publishStatusChangedTagsToTopic(DpsHeaders headers, JobStatus indexerBatchStatus) throws Exception
    {
        PublishRequestBuilder<RecordStatus> publishRequestBuilder = new PublishRequestBuilder<>();
        publishRequestBuilder.setGeneralParametersFromHeaders(headers);

        PublishRequest publishRequest = publishRequestBuilder.generatePublishRequest(osduIndexerTopic, amazonSNSTopic, indexerBatchStatus.getStatusesList());

        snsClient.publish(publishRequest);
    }

}
