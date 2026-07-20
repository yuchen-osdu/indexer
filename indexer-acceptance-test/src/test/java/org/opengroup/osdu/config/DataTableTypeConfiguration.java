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

package org.opengroup.osdu.config;

import io.cucumber.java.DataTableType;
import org.opengroup.osdu.models.Setup;
import java.util.Map;

public class DataTableTypeConfiguration {

    @DataTableType
    public Setup setupEntry(Map<String, String> entry) {
        Setup setup = new Setup();
        setup.setKind(entry.get("kind"));
        setup.setIndex(entry.get("index"));
        setup.setSchemaFile(entry.get("schemaFile"));
        setup.setViewerGroup(entry.get("viewerGroup"));
        setup.setOwnerGroup(entry.get("ownerGroup"));
        setup.setMappingFile(entry.get("mappingFile"));
        setup.setRecordFile(entry.get("recordFile"));
        return setup;
    }
}
