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

package org.opengroup.osdu.util.conf;

import java.util.Optional;

public class AnthosConfig {

    private static final String GROUP_ID_VARIABLE = "GROUP_ID";
    private static final String ENTITLEMENTS_DOMAIN_VARIABLE = "ENTITLEMENTS_DOMAIN";

    public static void updateEntitlementsDomainVariable() {
        String groupId = Optional.ofNullable(System.getProperty(GROUP_ID_VARIABLE, System.getenv(GROUP_ID_VARIABLE)))
                .orElse("group");
        System.setProperty(ENTITLEMENTS_DOMAIN_VARIABLE, groupId);
    }
}
