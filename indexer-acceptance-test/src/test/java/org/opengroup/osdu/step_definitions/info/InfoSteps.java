/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
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

package org.opengroup.osdu.step_definitions.info;

import org.opengroup.osdu.core.test.auth.UserType;
import org.opengroup.osdu.core.test.cucumber.BaseGetInfoCucumberAcceptanceTests;
import org.opengroup.osdu.core.test.service.ServiceType;

import java.util.List;

/**
 * Cucumber glue class for the indexer {@code /info} feature.
 *
 * <p>Extends {@link BaseGetInfoCucumberAcceptanceTests} directly, matching the pattern used by the
 * Search service. Step definitions (Given/Then) are registered by the parent class via the
 * Cucumber {@code En} lambda interface. No additional step annotations are needed here.
 */
@SuppressWarnings("unused")
public class InfoSteps extends BaseGetInfoCucumberAcceptanceTests {

    private static final List<String> EXPECTED_FEATURE_FLAGS = List.of(
        "featureFlag.mapBooleanToString.enabled",
        "featureFlag.asIngestedCoordinates.enabled",
        "featureFlag.keywordLower.enabled",
        "featureFlag.bagOfWords.enabled",
        "collaborations-enabled",
        "custom-index-analyzer-enabled",
        "index-augmenter-enabled");

    public InfoSteps() {
        super(UserType.PRIVILEGED_USER, ServiceType.INDEXER_V2, EXPECTED_FEATURE_FLAGS);
    }
}
