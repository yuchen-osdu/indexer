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

package org.opengroup.osdu.indexer.service;

import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.indexer.model.SearchRecord;
import org.opengroup.osdu.indexer.model.indexproperty.AugmenterConfiguration;

import java.util.List;
import java.util.Map;

public interface AugmenterConfigurationService {
    boolean isConfigurationEnabled(String kind);

    AugmenterConfiguration getConfiguration(String kind);

    Map<String, Object> getExtendedProperties(String objectId, Map<String, Object> originalDataMap, AugmenterConfiguration propertyConfigurations);

    List<SchemaItem> getExtendedSchemaItems(Schema originalSchema, Map<String, Schema> relatedObjectKindSchemas, AugmenterConfiguration propertyConfigurations);

    String resolveConcreteKind(String kind);

    void cacheDataRecord(String recordId, String kind, Map<String, Object> dataMap);

    void updateAssociatedRecords(RecordChangedMessages message, Map<String, List<String>> upsertKindIds, Map<String, List<String>> deleteKindIds, List<SearchRecord> deletedRecordsWithParentReferred);

    List<String> getRelatedKindsOfConfigurations(List<String> configurationIds);

    List<SearchRecord> getAllRecordsReferredByParentRecords(Map<String, List<String>> childRecordMap);
}
