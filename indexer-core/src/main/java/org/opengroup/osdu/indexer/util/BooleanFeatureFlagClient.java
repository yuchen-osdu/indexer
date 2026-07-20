/*
 * Copyright © Schlumberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.indexer.util;

import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.partition.IPartitionFactory;
import org.opengroup.osdu.core.common.partition.IPartitionProvider;
import org.opengroup.osdu.core.common.partition.PartitionException;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.core.common.partition.Property;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.opengroup.osdu.indexer.cache.partitionsafe.FeatureFlagCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class BooleanFeatureFlagClient {
    @Lazy
    @Autowired
    private FeatureFlagCache cache;

    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private IPartitionFactory factory;

    @Autowired
    private IServiceAccountJwtClient tokenService;

    public boolean isEnabled(String featureName, boolean defaultValue) {
        Boolean isEnabled = this.cache.get(featureName);
        if (isEnabled != null) {
            return isEnabled;
        }

        String dataPartitionId = headers.getPartitionId();
        isEnabled = isEnabled(featureName, defaultValue, dataPartitionId);
        this.cache.put(featureName, isEnabled);
        return isEnabled;
    }

    public boolean isEnabled(String featureName, boolean defaultValue, String dataPartitionId){
        boolean isEnabled = defaultValue;
        try {
            PartitionInfo partitionInfo = getPartitionInfo(dataPartitionId);
            isEnabled = getFeatureValue(partitionInfo, featureName, defaultValue);
            this.logger.info(String.format("BooleanFeatureFlagClient: The feature flag '%s' in data partition '%s' is set to %s", featureName, dataPartitionId, isEnabled));
        } catch (Exception e) {
            this.logger.error(String.format("BooleanFeatureFlagClient: Error on getting the feature flag '%s' for data partition '%s'. Using default value %s.", featureName, dataPartitionId, isEnabled), e);
        }
        return isEnabled;
    }

    private PartitionInfo getPartitionInfo(String dataPartitionId) throws PartitionException {
        try {
            DpsHeaders partitionHeaders = new DpsHeaders();
            partitionHeaders.put(DpsHeaders.AUTHORIZATION, this.tokenService.getIdToken(dataPartitionId));

            IPartitionProvider partitionProvider = this.factory.create(partitionHeaders);
            PartitionInfo partitionInfo = partitionProvider.get(dataPartitionId);
            return partitionInfo;
        } catch (PartitionException e) {
            if (e.getResponse() != null) {
                logger.error(String.format("Error getting partition info for data-partition: %s. Message: %s. ResponseCode: %s.", dataPartitionId, e.getResponse().getBody(), e.getResponse().getResponseCode()), e);
            } else {
                logger.error(String.format("Error getting partition info for data-partition: %s.", dataPartitionId), e);
            }
            throw e;
        }
    }

    private boolean getFeatureValue(PartitionInfo partitionInfo, String featureName, boolean defaultValue) {
        if(partitionInfo == null || partitionInfo.getProperties() == null)
            return defaultValue;

        if(partitionInfo.getProperties().containsKey(featureName)) {
            Property property = partitionInfo.getProperties().get(featureName);
            return Boolean.parseBoolean((String)property.getValue());
        }
        return defaultValue;
    }
}
