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

package org.opengroup.osdu.indexer.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.search.ClusterSettings;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.multitenancy.ITenantInfoService;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticRepository;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticCredentialsCache;
import org.opengroup.osdu.core.common.model.indexer.IElasticSettingService;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

@Service
public class ElasticSettingServiceImpl implements IElasticSettingService {

    @Inject
    private TenantInfo tenantInfo;
    @Inject
    private jakarta.inject.Provider<ITenantInfoService> tenantInfoServiceProvider;
    @Inject
    private IElasticRepository elasticRepository;
    @Inject
    private IElasticCredentialsCache elasticCredentialCache;
    @Inject
    private JaxRsDpsLog log;
    @Inject
    private IndexerConfigurationProperties configurationProperties;

    @Override
    public ClusterSettings getElasticClusterInformation() {
        return getClusterSettingsByTenantInfo(tenantInfo);
    }

    @Override
    public Map<String, ClusterSettings> getAllClustersSettings() {
        List<TenantInfo> tenantInfos = tenantInfoServiceProvider.get().getAllTenantInfos();
        return tenantInfos.stream()
            .collect(Collectors.toMap(TenantInfo::getDataPartitionId,
                this::getClusterSettingsByTenantInfo));
    }

    private ClusterSettings getClusterSettingsByTenantInfo(TenantInfo tenantInfo) {
        String cacheKey = String.format("%s-%s", configurationProperties.getGaeService(),
            tenantInfo.getName());

        ClusterSettings clusterInfo = (ClusterSettings) this.elasticCredentialCache.get(cacheKey);
        if (clusterInfo != null) {
            return clusterInfo;
        }

        log.warning(String.format("elastic-credential cache missed for tenant: %s",
            tenantInfo.getName()));

        clusterInfo = this.elasticRepository.getElasticClusterSettings(tenantInfo);
        if (clusterInfo == null) {
            throw new AppException(HttpStatus.SC_NOT_FOUND, "Tenant not found",
                "No information about the given tenant was found");
        }

        this.elasticCredentialCache.put(cacheKey, clusterInfo);
        return clusterInfo;
    }
}
