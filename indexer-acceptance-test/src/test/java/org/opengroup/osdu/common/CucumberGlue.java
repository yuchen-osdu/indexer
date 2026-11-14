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

import lombok.experimental.UtilityClass;

/**
 * Centralises the Cucumber glue-package strings used across all indexer acceptance-test runners.
 *
 * <p>Each constant combines the shared {@code org.opengroup.osdu.common} package (step base
 * classes) with the feature-specific {@code step_definitions.*} package.
 */
@UtilityClass
public final class CucumberGlue {

    private static final String COMMON = "org.opengroup.osdu.common";

    /** Glue packages for the {@code /info} feature. */
    public static final String INFO = COMMON + ",org.opengroup.osdu.step_definitions.info";

    /** Glue packages for index-record features. */
    public static final String INDEX_RECORD =
        COMMON + ",org.opengroup.osdu.step_definitions.record,org.opengroup.osdu.config";

    /** Glue packages for the augmented-indexer features. */
    public static final String INDEX_AUGMENTED = INDEX_RECORD;
}
