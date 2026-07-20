/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.indexer.util;


import static org.opengroup.osdu.core.common.feature.PartitionFeatureFlagImpl.FF_SOURCE_DATA_PARTITION;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.BAG_OF_WORDS_FEATURE_NAME;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.COLLABORATIONS_FEATURE_NAME;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.KEYWORD_LOWER_FEATURE_NAME;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.MAP_BOOL2STRING_FEATURE_NAME;
import static org.opengroup.osdu.indexer.model.Constants.AS_INGESTED_COORDINATES_FEATURE_NAME;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.feature.CommonFeatureFlagStateResolverUtil;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.info.FeatureFlagStateResolver;
import org.opengroup.osdu.core.common.model.info.FeatureFlagStateResolver.FeatureFlagState;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.multitenancy.ITenantInfoService;
import org.opengroup.osdu.indexer.config.FeatureConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = FeatureConstants.EXPOSE_FEATUREFLAG_ENABLED_PROPERTY, havingValue = "true", matchIfMissing = true)
public class FeatureFlagStateConfiguration {

  private final AugmenterSetting augmenterSetting;
  private final CustomIndexAnalyzerSetting indexAnalyzerSetting;
  private final ITenantInfoService tenantInfoService;
  private final IFeatureFlag featureFlagService;
  private final BooleanFeatureFlagClient booleanFeatureFlagClient;

  @Bean
  @RequestScope
  public FeatureFlagStateResolver bagOfWordsFFResolver() {
    return CommonFeatureFlagStateResolverUtil.buildCommonFFStateResolver(
        BAG_OF_WORDS_FEATURE_NAME,
        tenantInfoService,
        featureFlagService
    );
  }

  @Bean
  @RequestScope
  public FeatureFlagStateResolver collaborationFFResolver() {
    return CommonFeatureFlagStateResolverUtil.buildCommonFFStateResolver(
        COLLABORATIONS_FEATURE_NAME,
        tenantInfoService,
        featureFlagService
    );
  }

  @Bean
  @RequestScope
  public FeatureFlagStateResolver keywordLowerFFResolver() {
    return CommonFeatureFlagStateResolverUtil.buildCommonFFStateResolver(
        KEYWORD_LOWER_FEATURE_NAME,
        tenantInfoService,
        featureFlagService
    );
  }

  @Bean
  @RequestScope
  public FeatureFlagStateResolver asIngestedFFResolver() {
    return CommonFeatureFlagStateResolverUtil.buildCommonFFStateResolver(
        AS_INGESTED_COORDINATES_FEATURE_NAME,
        tenantInfoService,
        featureFlagService
    );
  }

  @Bean
  @RequestScope
  public FeatureFlagStateResolver indexAnalyzerFFResolver() {
    return () -> {
      List<TenantInfo> allTenantInfo = tenantInfoService.getAllTenantInfos();
      return allTenantInfo.stream().map(tenantInfo -> FeatureFlagState.builder()
          .partition(tenantInfo.getName())
          .name(CustomIndexAnalyzerSetting.PROPERTY_NAME)
          .source(FF_SOURCE_DATA_PARTITION)
          .enabled(indexAnalyzerSetting.isEnabled(tenantInfo.getName()))
          .build()).toList();
    };
  }

  @Bean
  @RequestScope
  public FeatureFlagStateResolver augmenterFFResolver() {
    return () -> {
      List<TenantInfo> allTenantInfo = tenantInfoService.getAllTenantInfos();
      return allTenantInfo.stream().map(tenantInfo -> FeatureFlagState.builder()
          .partition(tenantInfo.getName())
          .name(AugmenterSetting.PROPERTY_NAME)
          .source(FF_SOURCE_DATA_PARTITION)
          .enabled(augmenterSetting.isEnabled(tenantInfo.getName()))
          .build()).toList();
    };
  }

  @Bean
  @RequestScope
  public FeatureFlagStateResolver mapToBoolFFResolver() {
    return () -> {
      List<TenantInfo> allTenantInfo = tenantInfoService.getAllTenantInfos();
      return allTenantInfo.stream()
          .map(tenantInfo -> FeatureFlagState.builder()
              .partition(tenantInfo.getName())
              .name(MAP_BOOL2STRING_FEATURE_NAME)
              .source(featureFlagService.source() + " +  " + FF_SOURCE_DATA_PARTITION)
              .enabled(
                  featureFlagService.isFeatureEnabled(MAP_BOOL2STRING_FEATURE_NAME, tenantInfo.getName())
                      || booleanFeatureFlagClient.isEnabled(MAP_BOOL2STRING_FEATURE_NAME, false, tenantInfo.getName())
              )
              .build())
          .toList();
    };
  }
}
