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

import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;

@SuppressWarnings("unused")
public final class ClientTeardownHooks {

    private ClientTeardownHooks() {
    }

    @BeforeAll
    public static void setUpSuiteLegalTag() {
        TestsBase.setupSuiteLegalTag();
    }

    @AfterAll
    public static void tearDownTrackedClients() {
        TestsBase.tearDownTrackedResources();
        TestsBase.tearDownSuiteLegalTag();
    }
}
