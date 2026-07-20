// Copyright 2017-2020, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.indexer.service;

import org.opengroup.osdu.indexer.model.SchemaInfo;
import org.opengroup.osdu.indexer.model.SchemaInfoResponse;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Interface to consume schemas from the Schema Service
 */
public interface SchemaService {
    /**
     * @param kind key to retrieve schema
     * @return obtained schema
     * @throws URISyntaxException
     * @throws UnsupportedEncodingException
     */
    String getSchema(String kind) throws URISyntaxException, UnsupportedEncodingException;

    /**
     * @param authority
     * @param source
     * @param entityType
     * @param majorVersion
     * @return The latest version of the kind
     */
    SchemaInfoResponse getSchemaInfos(String authority, String source, String entityType, String majorVersion, String minorVersion, String patchVersion, boolean latestVersion) throws URISyntaxException, UnsupportedEncodingException;
}
