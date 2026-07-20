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

package org.opengroup.osdu.indexer.logging;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.opengroup.osdu.core.common.model.search.SearchServiceRole;
import org.opengroup.osdu.indexer.util.Role;

public enum AuditOperation {
    INDEX_CREATE_RECORD(Arrays.asList(SearchServiceRole.ADMIN, SearchServiceRole.USER)),
    INDEX_UPDATE_RECORD(Arrays.asList(SearchServiceRole.ADMIN, SearchServiceRole.USER)),
    INDEX_DELETE_RECORD(Arrays.asList(SearchServiceRole.ADMIN, SearchServiceRole.USER)),
    INDEX_PURGE_RECORD(Arrays.asList(SearchServiceRole.ADMIN, SearchServiceRole.USER)),
    INDEX_STARTED(Collections.emptyList()),
    REINDEX_KIND(Arrays.asList(SearchServiceRole.ADMIN)),
    REINDEX_RECORDS(Arrays.asList(Role.USER_OPS)),
    COPY_INDEX(Arrays.asList(SearchServiceRole.ADMIN)),
    GET_TASK_STATUS(Arrays.asList(SearchServiceRole.ADMIN)),
    INDEX_CLEANUP_JOB_RUN(Arrays.asList(SearchServiceRole.ADMIN)),
    INDEX_MAPPING_UPSERT(Arrays.asList(SearchServiceRole.ADMIN, SearchServiceRole.USER)),
    CONFIGURE_PARTITION(Arrays.asList(Role.USER_OPS)),
    DELETE_INDEX(Arrays.asList(SearchServiceRole.ADMIN, Role.USER_OPS));

    private final List<String> requiredGroups;

    AuditOperation(List<String> requiredGroups) {
        this.requiredGroups = Collections.unmodifiableList(requiredGroups);
    }

    public List<String> getRequiredGroups() {
        return requiredGroups;
    }
}
