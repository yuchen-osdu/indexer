/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.service;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.search.ClusterSettings;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.multitenancy.ITenantInfoService;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticRepository;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticCredentialsCache;
import org.opengroup.osdu.core.common.model.indexer.IElasticSettingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

@Service
public class ElasticSettingServiceIBMImpl implements IElasticSettingService {

    @Inject
    private TenantInfo tenantInfo;

    @Inject
    private IElasticRepository elasticRepository;
    @Inject
    private IElasticCredentialsCache elasticCredentialCache;
    @Inject
    private JaxRsDpsLog logger;
    @Inject
    private jakarta.inject.Provider<ITenantInfoService> tenantInfoServiceProvider;
    @Inject
    private IndexerConfigurationProperties configurationProperties;

    @Override
    public ClusterSettings getElasticClusterInformation() {

        String cacheKey = String.format("%s-%s", "ibm", tenantInfo.getName());
        ClusterSettings clusterInfo = (ClusterSettings) this.elasticCredentialCache.get(cacheKey);
        if (clusterInfo != null) {
            return clusterInfo;
        }

        logger.warning(String.format("elastic-credential cache missed for tenant: %s", tenantInfo.getName()));

        clusterInfo = this.elasticRepository.getElasticClusterSettings(tenantInfo);
        if (clusterInfo == null) {
            throw new AppException(HttpStatus.SC_NOT_FOUND, "Tenant not found", "No information about the given tenant was found");
        }

        this.elasticCredentialCache.put(cacheKey, clusterInfo);
        return clusterInfo;
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

        logger.warning(String.format("elastic-credential cache missed for tenant: %s",
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
