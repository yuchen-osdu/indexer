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

package org.opengroup.osdu.indexer.model;

public class Constants {
    // It should be moved to core common later
    public static final String ANCESTRY_KINDS = "ancestry_kinds";
    public static final int CHASING_MESSAGE_DELAY_SECONDS = 30;

    // Specifications using kind as key is not partition safe if the specifications are per data partition
    public static final int SPEC_CACHE_EXPIRATION = 600;
    public static final int SPEC_MAX_CACHE_SIZE = 20000;

    // Data id itself is partition safe
    public static final int DATA_CACHE_EXPIRATION = 120;
    public static final int DATA_CHANGE_INFO_CACHE_EXPIRATION = 3600;
    public static final int DATA_MAX_CACHE_SIZE = 20000;

    public static final String AS_INGESTED_COORDINATES_FEATURE_NAME = "featureFlag.asIngestedCoordinates.enabled";
}
