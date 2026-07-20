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

package org.opengroup.osdu.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.model.info.FeatureFlagStateResolver.FeatureFlagState;
import org.opengroup.osdu.response.InfoResponseMock;
import org.opengroup.osdu.util.Config;
import org.opengroup.osdu.util.ElasticUtils;
import org.opengroup.osdu.util.HTTPClient;

@Slf4j
public class InfoBase extends TestsBase {

  // Feature flag property constant - matches the value used in service configuration
  private static final String EXPOSE_FEATUREFLAG_ENABLED_PROPERTY = "expose_featureflag.enabled";

  private static final List<String> expectedFeatureFlags = List.of(
      "featureFlag.mapBooleanToString.enabled",
      "featureFlag.asIngestedCoordinates.enabled",
      "featureFlag.keywordLower.enabled",
      "featureFlag.bagOfWords.enabled",
      "collaborations-enabled",
      "custom-index-analyzer-enabled",
      "index-augmenter-enabled"
  );

  protected Map<String, String> headers = new HashMap<>();
  private InfoResponseMock response;

  public InfoBase(HTTPClient httpClient) {
    super(httpClient);
  }

  public InfoBase(HTTPClient httpClient, ElasticUtils elasticUtils) {
    super(httpClient, elasticUtils);
  }

  @Override
  protected String getApi() {
    return Config.getIndexerBaseURL() + "info";
  }

  @Override
  protected String getHttpMethod() {
    return "GET";
  }

  public void i_send_get_request_to_version_info_endpoint() {
    if (Config.getIndexerBaseURL().isEmpty()) {
      log.warn("Env variable INDEXER_HOST is empty. Version info endpoint test is skipped");
      return;
    }

    response =
            executeQuery(
                this.getApi(),
                "",
                headers,
                httpClient.getAccessToken(),
                InfoResponseMock.class);
  }

  public void i_send_get_request_to_version_info_endpoint_with_trailing_slash() {
    response =
            executeQuery(getApi()+"/", "", headers, httpClient.getAccessToken(), InfoResponseMock.class);
  }

  public void i_should_get_version_info_in_response() {
    if (response != null) {
      assertEquals(200, response.getResponseCode());
      assertNotNull(response.getGroupId());
      assertNotNull(response.getArtifactId());
      assertNotNull(response.getVersion());
      assertNotNull(response.getBuildTime());
      assertNotNull(response.getBranch());
      assertNotNull(response.getCommitId());
      assertNotNull(response.getCommitMessage());
      List<FeatureFlagState> featureFlagStates = response.getFeatureFlagStates();
      
      // Read the actual configuration property value to validate behavior alignment
      // Check system property first, then fall back to environment variable
      String featureFlagExposeEnabledProperty = System.getProperty(EXPOSE_FEATUREFLAG_ENABLED_PROPERTY);
      if (featureFlagExposeEnabledProperty == null) {
          featureFlagExposeEnabledProperty = System.getenv("EXPOSE_FEATUREFLAG_ENABLED");
      }
      boolean isFeatureFlagExposureEnabled = featureFlagExposeEnabledProperty == null || !"false".equalsIgnoreCase(featureFlagExposeEnabledProperty);
      
      if (!isFeatureFlagExposureEnabled)
      {
        assertNull(featureFlagStates);
      }
      else
      {
        assertNotNull(featureFlagStates);
        assertFalse(featureFlagStates.isEmpty());

        for (String ffName : expectedFeatureFlags){
          assertTrue(featureFlagStates.stream().anyMatch(ffState -> ffState.getName().equals(ffName)));
        }
      }
    } else {
      log.warn("Version info endpoint provided null response");
    }
  }
}
