/*
 * Copyright 2017-2025, The Open Group
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

package org.opengroup.osdu.indexer.config;

import com.google.common.base.Strings;
import java.util.regex.PatternSyntaxException;
import lombok.Getter;
import lombok.Setter;
import org.opengroup.osdu.core.common.model.search.DeploymentEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties
@Getter
@Setter
public class IndexerConfigurationProperties {
	public static final String MAP_BOOL2STRING_FEATURE_NAME = "featureFlag.mapBooleanToString.enabled";
	public static final String KEYWORD_LOWER_FEATURE_NAME = "featureFlag.keywordLower.enabled";
	public static final String BAG_OF_WORDS_FEATURE_NAME = "featureFlag.bagOfWords.enabled";
	public static final String COLLABORATIONS_FEATURE_NAME = "collaborations-enabled";

	//Search query properties
	private Integer queryDefaultLimit = 10;
	private Integer queryLimitMaximum = 1000;
	private Integer aggregationSize = 1000;


	private String elasticDatastoreKind;
	private String elasticDatastoreId;

	//Default Cache Settings
	private Integer schemaCacheExpiration = 60;
	private Integer indexCacheExpiration = 60;
	private Integer elasticCacheExpiration = 1440;
	private Integer cursorCacheExpiration = 60;

	//Kinds Cache expiration 2*24*60
	private Integer kindsCacheExpiration = 2880;

	//Attributes Cache expiration 2*24*60
	private Integer attributesCacheExpiration = 2880;

	private Integer kindsRedisDatabase = 1;
	private Integer cronIndexCleanupThresholdDays = 3;
	private Integer cronEmptyIndexCleanupThresholdDays = 7;

	private String deploymentEnvironment = DeploymentEnvironment.CLOUD.name();
	private String environment;
	private String indexerHost;
	private String searchHost;
	private String storageQueryKindsHost;
	private String storageQueryRecordForConversionHost;
	private String storageQueryRecordHost;
	private Integer storageRecordsBatchSize;
	private Integer storageRecordsByKindBatchSize;
	private String storageSchemaHost;
	private String schemaHost;
	private String entitlementsHost;
	private String entitlementTargetAudience;
	private String indexerQueueHost;
	private String redisSearchHost;
	private String redisSearchPort;
	private String elasticHost;
	private String elasticClusterName;
	private String keyRing;
	private String kmsKey;
	private String cronIndexCleanupPattern;
	private String cronIndexCleanupTenants;
	private String smartSearchCcsDisabled;

	private String gaeService;
	private String gaeVersion;
	private String googleCloudProject;
	private String googleCloudProjectRegion;

	public DeploymentEnvironment getDeploymentEnvironment(){
		return DeploymentEnvironment.valueOf(deploymentEnvironment);
	}

	public String getDeploymentLocation() {
		return googleCloudProjectRegion;
	}

	public String getDeployedServiceId() {
		return gaeService;
	}

	public String getDeployedVersionId() {
		return gaeVersion;
	}


	public boolean isLocalEnvironment() {
		return "local".equalsIgnoreCase(environment);
	}

	public boolean isPreP4d() {
		return isLocalEnvironment() || "evd".equalsIgnoreCase(environment) || "evt".equalsIgnoreCase(environment);
	}

	public boolean isPreDemo() {
		return isPreP4d() || "p4d".equalsIgnoreCase(environment);
	}

	public String[] getIndexCleanupPattern() {
		if (!Strings.isNullOrEmpty(cronIndexCleanupPattern)) {
			try {
				return cronIndexCleanupPattern.split(",");
			} catch (PatternSyntaxException var2) {
			}
		}

		return new String[0];
	}

	public String[] getIndexCleanupTenants() {
		if (!Strings.isNullOrEmpty(cronIndexCleanupTenants)) {
			try {
				return cronIndexCleanupTenants.split(",");
			} catch (PatternSyntaxException var2) {
			}
		}

		return new String[0];
	}

	public final Boolean isSmartSearchCcsDisabled() {
		return Boolean.TRUE.toString().equalsIgnoreCase(smartSearchCcsDisabled);
	}

	public Integer getStorageRecordsByKindBatchSize() {
		if (this.storageRecordsByKindBatchSize != null) {
			return this.storageRecordsByKindBatchSize;
		}
		// if property is not set, fall back to storageRecordsBatchSize property which is used by all CSPs to set batch size.
		return this.storageRecordsBatchSize;
	}
}
