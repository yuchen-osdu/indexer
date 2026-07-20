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

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import org.springframework.util.CollectionUtils;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.IndexingStatus;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.cache.partitionsafe.*;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.opengroup.osdu.indexer.model.*;
import org.opengroup.osdu.indexer.model.indexproperty.*;
import org.opengroup.osdu.indexer.util.QueryUtil;
import org.opengroup.osdu.indexer.util.IndexerQueueTaskBuilder;
import org.opengroup.osdu.indexer.util.PropertyUtil;
import org.opengroup.osdu.indexer.util.SearchClient;
import org.opengroup.osdu.indexer.util.function.AugmenterFunctionFactory;
import org.opengroup.osdu.indexer.util.function.IAugmenterFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;


@Component
public class AugmenterConfigurationServiceImpl implements AugmenterConfigurationService {
    private static final String ASSOCIATED_IDENTITIES_PROPERTY = "AssociatedIdentities";
    private static final String VERSION_PROPERTY = "version";
    private static final String ASSOCIATED_IDENTITIES_PROPERTY_STORAGE_FORMAT_TYPE = "[]string";
    private static final String INDEX_PROPERTY_PATH_CONFIGURATION_KIND = "osdu:wks:reference-data--IndexPropertyPathConfiguration:*";
    private static final String ANCESTRY_KINDS_DELIMITER = ",";
    private static final String PROPERTY_DELIMITER = ".";
    private static final String NESTED_OBJECT_DELIMITER = "[].";
    private static final String ARRAY_SYMBOL = "[]";
    private static final String SCHEMA_NESTED_KIND = "nested";

    private static final String STRING_KIND = "string";

    private static final String STRING_ARRAY_KIND = "[]string";

    private static final AugmenterConfiguration EMPTY_AUGMENTER_CONFIGURATION = new AugmenterConfiguration();
    private static final String SEARCH_GENERAL_ERROR = "Augmenter: Failed to search.";

    private static final int NO_LIMIT = -1;

    private final Gson gson = new Gson();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private IndexerConfigurationProperties configurationProperties;
    @Inject
    private AugmenterConfigurationCache augmenterConfigurationCache;
    @Inject
    private AugmenterConfigurationEnabledCache augmenterConfigurationEnabledCache;
    @Inject
    private ChildRelationshipSpecsCache parentChildRelationshipSpecsCache;
    @Inject
    private ChildrenKindsCache childrenKindsCache;
    @Inject
    private KindCache kindCache;
    @Inject
    private RelatedObjectCache relatedObjectCache;
    @Inject
    private RecordChangeInfoCache recordChangeInfoCache;
    @Inject
    private SchemaService schemaService;
    @Inject
    private IndexerQueueTaskBuilder indexerQueueTaskBuilder;
    @Inject
    private IRequestInfo requestInfo;
    @Inject
    private JaxRsDpsLog jaxRsDpsLog;
    @Inject
    private JobStatus jobStatus;
    @Inject
    private SearchClient searchClient;
    @Inject
    AugmenterFunctionFactory augmenterFunctionFactory;

    @Value("${augmenter.extended_list_value.max_size:2000}")
    int maxSizeOfExtendedListValue;

    @Override
    public boolean isConfigurationEnabled(String kind) {
        kind = PropertyUtil.getKindWithMajor(kind);
        if (Strings.isNullOrEmpty(kind))
            return false;

        Boolean enabled = augmenterConfigurationEnabledCache.get(kind);
        if(enabled == null) {
            Query query = QueryUtil.createQueryForConfigurations(kind);
            if(searchFirstRecord(INDEX_PROPERTY_PATH_CONFIGURATION_KIND, query) != null) {
                enabled = true;
            }
            else {
                enabled = false;
            }
            augmenterConfigurationEnabledCache.put(kind, enabled);
        }

        return enabled;
    }

    @Override
    public AugmenterConfiguration getConfiguration(String kind) {
        kind = PropertyUtil.getKindWithMajor(kind);
        if (Strings.isNullOrEmpty(kind))
            return null;

        AugmenterConfiguration augmenterConfiguration = augmenterConfigurationCache.get(kind);
        if (augmenterConfiguration == null) {
            augmenterConfiguration = searchConfiguration(kind);
            if (augmenterConfiguration != null) {
                if(augmenterConfiguration.isValid()) {
                    // Log for debug
                    if(augmenterConfiguration.hasInvalidConfigurations()) {
                        String msg = String.format("PropertyConfigurations: it has invalid PropertyConfiguration for configurations with name '%s':", augmenterConfiguration.getName());
                        this.jaxRsDpsLog.warning(msg);
                    }
                }
                else {
                    // Log for debug
                    StringBuilder msgBuilder = new StringBuilder();
                    msgBuilder.append(String.format("PropertyConfigurations: it is invalid for configurations with name '%s':", augmenterConfiguration.getName()));
                    if(!augmenterConfiguration.hasValidCode()) {
                        msgBuilder.append(System.lineSeparator());
                        msgBuilder.append(String.format("The code '%s' is invalid. It should be a valid kind with major version ended with '.'", augmenterConfiguration.getCode()));
                    }
                    if(!augmenterConfiguration.hasValidConfigurations()) {
                        msgBuilder.append(System.lineSeparator());
                        msgBuilder.append("It does not have any valid PropertyConfiguration");
                    }
                    this.jaxRsDpsLog.warning(msgBuilder.toString());

                    augmenterConfiguration = EMPTY_AUGMENTER_CONFIGURATION; // reset
                }

                augmenterConfigurationCache.put(kind, augmenterConfiguration);
            } else {
                // It is common that a kind does not have extended property. So we need to cache an empty augmenterConfiguration
                // to avoid unnecessary search
                augmenterConfigurationCache.put(kind, EMPTY_AUGMENTER_CONFIGURATION);
            }
        }

        if (!isEmptyConfiguration(augmenterConfiguration)) {
            return augmenterConfiguration;
        }

        return null;
    }

    @Override
    public Map<String, Object> getExtendedProperties(String objectId, Map<String, Object> originalDataMap, AugmenterConfiguration augmenterConfiguration) {
        // Get all data maps of the related objects in one query in order to improve the performance.
        Map<String, Map<String, Object>> idObjectDataMap = getRelatedObjectsData(originalDataMap, augmenterConfiguration);

        Set<String> associatedIdentities = new HashSet<>();
        Map<String, Object> extendedDataMap = new HashMap<>();
        for (PropertyConfiguration configuration : augmenterConfiguration.getConfigurations().stream().filter(c -> c.isValid()).toList()) {
            String extendedPropertyName = configuration.getExtendedPropertyName();
            if (originalDataMap.containsKey(extendedPropertyName) && originalDataMap.get(extendedPropertyName) != null) {
                // If the original record already has the property, then we should not override.
                // For example, if the trajectory record already SpatialLocation value, then it should not be overridden by the SpatialLocation of the well bore.
                continue;
            }

            Map<String, Object> allPropertyValues = new HashMap<>();
            for (PropertyPath path : configuration.getPaths().stream().filter(p -> p.hasValidValueExtraction()).toList()) {
                if (path.hasValidRelatedObjectsSpec()) {
                    RelatedObjectsSpec relatedObjectsSpec = path.getRelatedObjectsSpec();
                    if (relatedObjectsSpec.isChildToParent()) {
                        List<String> relatedObjectIds = getRelatedObjectIds(originalDataMap, relatedObjectsSpec);
                        for (String relatedObjectId : relatedObjectIds) {
                            String id = PropertyUtil.removeIdPostfix(relatedObjectId);
                            associatedIdentities.add(id);
                            Map<String, Object> relatedObject = idObjectDataMap.getOrDefault(id, new HashMap<>());
                            Map<String, Object> propertyValues = getExtendedPropertyValues(extendedPropertyName, relatedObject, path.getValueExtraction(), configuration.isExtractFirstMatch());
                            if (allPropertyValues.isEmpty() && configuration.isExtractFirstMatch()) {
                                allPropertyValues = propertyValues;
                                break;
                            } else {
                                allPropertyValues = PropertyUtil.combineObjectMap(allPropertyValues, propertyValues);
                            }
                        }
                    } else {
                        List<SearchRecord> childrenRecords = searchChildrenRecords(relatedObjectsSpec.getRelatedObjectKind(), relatedObjectsSpec.getRelatedObjectID(), objectId);
                        for (SearchRecord searchRecord : childrenRecords) {
                            // If the child record is in the cache, that means the searchRecord was updated very recently.
                            // In this case, use the cache's record instead of the searchRecord from search result
                            RecordData cachedRecordData = this.relatedObjectCache.get(searchRecord.getId());
                            Map<String, Object> childDataMap = (cachedRecordData != null)? cachedRecordData.getData() : searchRecord.getData();
                            Map<String, Object> propertyValues = getExtendedPropertyValues(extendedPropertyName, childDataMap, path.getValueExtraction(), configuration.isExtractFirstMatch());
                            if (allPropertyValues.isEmpty() && configuration.isExtractFirstMatch()) {
                                allPropertyValues = propertyValues;
                                break;
                            } else {
                                allPropertyValues = PropertyUtil.combineObjectMap(allPropertyValues, propertyValues);
                            }
                        }
                    }
                } else {
                    Map<String, Object> propertyValues = getExtendedPropertyValues(extendedPropertyName, originalDataMap, path.getValueExtraction(), configuration.isExtractFirstMatch());
                    if (allPropertyValues.isEmpty() && configuration.isExtractFirstMatch()) {
                        allPropertyValues = propertyValues;
                    } else {
                        allPropertyValues = PropertyUtil.combineObjectMap(allPropertyValues, propertyValues);
                    }
                }

                if (!allPropertyValues.isEmpty() && configuration.isExtractFirstMatch())
                    break;
            }

            extendedDataMap.putAll(allPropertyValues);
        }

        // Remove oversized extended properties with array type
        removeOversizedExtendedProperties(objectId, extendedDataMap);

        if (!associatedIdentities.isEmpty()) {
            extendedDataMap.put(ASSOCIATED_IDENTITIES_PROPERTY, Arrays.asList(associatedIdentities.toArray()));
        }

        return extendedDataMap;
    }

    @Override
    public List<SchemaItem> getExtendedSchemaItems(Schema originalSchema, Map<String, Schema> relatedObjectKindSchemas, AugmenterConfiguration augmenterConfiguration) {
        List<SchemaItem> extendedSchemaItems = new ArrayList<>();
        boolean hasChildToParentRelationship = false;
        for (PropertyConfiguration configuration : augmenterConfiguration.getConfigurations().stream().filter(c -> c.isValid()).toList()) {
            Schema schema = null;
            PropertyPath propertyPath = null;
            for (PropertyPath path : configuration.getPaths().stream().filter(p -> p.hasValidRelatedObjectsSpec()).toList()) {
                RelatedObjectsSpec relatedObjectsSpec = path.getRelatedObjectsSpec();
                if (relatedObjectsSpec.isChildToParent()) {
                    hasChildToParentRelationship = true;
                }
                if (relatedObjectKindSchemas.containsKey(relatedObjectsSpec.getRelatedObjectKind())) {
                    // Refer to the schema of the related object
                    schema = relatedObjectKindSchemas.get(relatedObjectsSpec.getRelatedObjectKind());
                    propertyPath = path;
                    break;
                }
            }
            if (schema == null) {
                // Refer to the schema of the object itself
                schema = originalSchema;
                propertyPath = configuration.getPaths().stream().filter(p -> p.getRelatedObjectsSpec() == null && p.hasValidValueExtraction()).findFirst().orElse(null);
            }

            if (schema != null && propertyPath != null) {
                List<SchemaItem> schemaItems = getExtendedSchemaItems(schema, configuration, propertyPath);
                extendedSchemaItems.addAll(schemaItems);
            }
        }

        if (hasChildToParentRelationship) {
            extendedSchemaItems.add(createAssociatedIdentitiesSchemaItem());
        }

        return extendedSchemaItems;
    }

    @Override
    public String resolveConcreteKind(String kind) {
        if (Strings.isNullOrEmpty(kind) || PropertyUtil.isConcreteKind(kind)) {
            return kind;
        }

        String concreteKind = kindCache.get(kind);
        if (concreteKind == null) {
            concreteKind = getLatestVersionOfKind(kind);
            if (!Strings.isNullOrEmpty(concreteKind)) {
                kindCache.put(kind, concreteKind);
            }
        }
        return concreteKind;
    }

    @Override
    public void cacheDataRecord(String recordId, String kind, Map<String, Object> dataMap) {
        Map<String, Object> previousDataMap = this.getObjectData(kind, recordId);
        RecordInfo recordInfo = new RecordInfo();
        recordInfo.setId(recordId);
        recordInfo.setKind(kind);
        RecordChangeInfo changedInfo = new RecordChangeInfo();
        changedInfo.setRecordInfo(recordInfo);
        // Using recordChangeInfoCache is the best effort to avoid updating the associated records when unnecessary
        // It should only store the updated records (ids) with updated properties. However, in order to
        // handle the case that a new record is updated in a short period of time, the ids of the new records with OPT
        // OperationType.create should be cached too.
        if (previousDataMap == null || previousDataMap.isEmpty()) {
            recordInfo.setOp(OperationType.create.getValue());
        } else {
            recordInfo.setOp(OperationType.update.getValue());
            List<String> updatedProperties = PropertyUtil.getChangedProperties(previousDataMap, dataMap);

            RecordChangeInfo previousChangedInfo = recordChangeInfoCache.get(recordId);
            if(previousChangedInfo != null) {
                if(previousChangedInfo.getRecordInfo().getOp().equals(OperationType.create.getValue())) {
                    recordInfo.setOp(OperationType.create.getValue());
                }
                else if(previousChangedInfo.getUpdatedProperties() != null) {
                    previousChangedInfo.getUpdatedProperties().forEach(p -> {
                        if(!updatedProperties.contains(p))
                            updatedProperties.add(p);
                    });
                }
            }

            if(recordInfo.getOp().equals(OperationType.update.getValue()))
                changedInfo.setUpdatedProperties(updatedProperties);
        }
        recordChangeInfoCache.put(recordId, changedInfo);
        RecordData recordData = new RecordData();
        recordData.setData(dataMap);
        relatedObjectCache.put(recordId, recordData);
    }

    @Override
    public void updateAssociatedRecords(RecordChangedMessages message, Map<String, List<String>> upsertKindIds,
                                        Map<String, List<String>> deleteKindIds, List<SearchRecord> deletedRecordsWithParentReferred) {
        if (upsertKindIds == null) {
            upsertKindIds = new HashMap<>();
        }
        if (deleteKindIds == null) {
            deleteKindIds = new HashMap<>();
        }

        Map<String, String> attributes = message.getAttributes();
        String ancestors = attributes.containsKey(Constants.ANCESTRY_KINDS) ? attributes.get(Constants.ANCESTRY_KINDS) : "";
        Map<String, List<RecordChangeInfo>> recordChangeInfoMap = createRecordChangeInfoMap(upsertKindIds, deleteKindIds);
        for (Map.Entry<String, List<RecordChangeInfo>> entry : recordChangeInfoMap.entrySet()) {
            String kind = entry.getKey();
            List<RecordChangeInfo> recordChangeInfoList = entry.getValue();
            String updatedAncestors = Strings.isNullOrEmpty(ancestors) ? kind : ancestors + ANCESTRY_KINDS_DELIMITER + kind;

            updateAssociatedParentRecords(updatedAncestors, kind, recordChangeInfoList, deletedRecordsWithParentReferred);
            updateAssociatedChildrenRecords(updatedAncestors, kind, recordChangeInfoList);
        }
    }

    @Override
    public List<String> getRelatedKindsOfConfigurations(List<String> configurationIds) {
        if(CollectionUtils.isEmpty(configurationIds)) {
            return new ArrayList<>();
        }

        Set<String> codes = new HashSet<>();
        for(String configurationId: configurationIds) {
            // We should only get it from cache to avoid getting an old version of the configuration from index
            // as there is a few second delay to have the new record searchable
            // The implementation is a bit subtle as set and get from the cache are separated in two different places (classes)
            RecordData recordData = relatedObjectCache.get(configurationId);
            if(recordData != null) {
                try {
                    String data = objectMapper.writeValueAsString(recordData.getData());
                    AugmenterConfiguration augmenterConfiguration = objectMapper.readValue(data, AugmenterConfiguration.class);
                    if(augmenterConfiguration.isValid()) {
                        // The code must be a kind with major version format if the configuration is valid
                        String code = augmenterConfiguration.getCode();
                        codes.add(code);

                        // Same here to use cache to
                        // cache configuration used by method getConfiguration(String kind)
                        augmenterConfigurationCache.put(code, augmenterConfiguration);
                    }
                    else {
                        jaxRsDpsLog.warning(String.format("resolveKindsForSchemaUpdate: configuration with id %s is invalid", configurationId));
                    }
                } catch (JsonProcessingException e) {
                    jaxRsDpsLog.error(String.format("resolveKindsForSchemaUpdate: failed to deserialize PropertyConfigurations object with id %s", configurationId), e);
                }
            }
        }

        List<String> relatedKinds = new ArrayList<>();
        for(String code: codes) {
            List<String> kinds = getConcreteKinds(code, false);
            relatedKinds.addAll(kinds);
        }
        return relatedKinds;
    }

    @Override
    public List<SearchRecord> getAllRecordsReferredByParentRecords(Map<String, List<String>> childRecordMap) {
        if(childRecordMap == null || childRecordMap.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> kinds = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for(Map.Entry<String, List<String>> childRecordEntry : childRecordMap.entrySet()) {
            String childKind = childRecordEntry.getKey();
            if(hasParentChildRelationshipSpecs(childKind)) {
                kinds.add(childKind);
                ids.addAll(childRecordEntry.getValue());
            }
        }
        if(!kinds.isEmpty()) {
            return searchRelatedRecords(kinds, ids);
        }
        else {
            return new ArrayList<>();
        }
    }


    /******************************************************** Private methods **************************************************************/
    private boolean hasParentChildRelationshipSpecs(String childKind) {
        ParentChildRelationshipSpecs specs = getParentChildRelatedObjectsSpecs(childKind);
        return specs.getSpecList() != null && !specs.getSpecList().isEmpty();
    }

    private boolean isEmptyConfiguration(AugmenterConfiguration configuration) {
        return configuration == null || Strings.isNullOrEmpty(configuration.getCode());
    }

    private SchemaItem createAssociatedIdentitiesSchemaItem() {
        SchemaItem extendedSchemaItem = new SchemaItem();
        extendedSchemaItem.setPath(ASSOCIATED_IDENTITIES_PROPERTY);
        extendedSchemaItem.setKind(ASSOCIATED_IDENTITIES_PROPERTY_STORAGE_FORMAT_TYPE);
        return extendedSchemaItem;
    }

    private String createIdsQuery(List<String> ids) {
        return String.format("id: (%s)", QueryUtil.createIdsFilter(ids));
    }

    private void createWorkerTask(String ancestors, List<RecordInfo> recordInfos) {
        Map<String, String> attributes = new HashMap<>();
        DpsHeaders headers = this.requestInfo.getHeadersWithDwdAuthZ();
        attributes.put(DpsHeaders.ACCOUNT_ID, headers.getAccountId());
        attributes.put(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
        attributes.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
        attributes.put(Constants.ANCESTRY_KINDS, ancestors);

        RecordChangedMessages recordChangedMessages = RecordChangedMessages.builder().data(gson.toJson(recordInfos)).attributes(attributes).build();
        String recordChangedMessagePayload = gson.toJson(recordChangedMessages);
        // GC implementation of IndexerQueueTaskBuilder honors the delay (countdownMillis) while Azure/AWS don't
        // In Azure/AWS implementation, we need to set the chasing delay separately.
        this.indexerQueueTaskBuilder.createWorkerTask(recordChangedMessagePayload, Constants.CHASING_MESSAGE_DELAY_SECONDS * 1000L, this.requestInfo.getHeadersWithDwdAuthZ());
    }

    private Map<String, Object> getObjectData(String kind, String id) {
        RecordData recordData = relatedObjectCache.get(id);
        Map<String, Object> data = (recordData != null)? recordData.getData() : null;
        if (data == null) {
            String queryString = String.format("id: \"%s\"", id);
            Query query = QueryUtil.createSimpleTextQuery(queryString);
            SearchRecord searchRecord = searchFirstRecord(kind, query);
            if (searchRecord != null) {
                data = searchRecord.getData();
                recordData = new RecordData();
                recordData.setData(data);
                relatedObjectCache.put(id, recordData);
            }
        }

        return data;
    }

    private Map<String, Map<String, Object>> getRelatedObjectsData(Map<String, Object> originalDataMap, AugmenterConfiguration augmenterConfiguration) {
        Map<String, Map<String, Object>> idData = new HashMap<>();
        Map<String, Set<String>> kindIds = new HashMap<>();
        for (PropertyConfiguration configuration : augmenterConfiguration.getConfigurations().stream().filter(c -> c.isValid()).toList()) {
            for (PropertyPath path : configuration.getPaths().stream().filter(p -> p.hasValidValueExtraction()).toList()) {
                if (path.hasValidRelatedObjectsSpec()) {
                    RelatedObjectsSpec relatedObjectsSpec = path.getRelatedObjectsSpec();
                    List<String> relatedObjectIds = getRelatedObjectIds(originalDataMap, relatedObjectsSpec);
                    String relatedObjectKind = relatedObjectsSpec.getRelatedObjectKind();
                    if(!kindIds.containsKey(relatedObjectKind)) {
                        kindIds.put(relatedObjectKind, new HashSet<>());
                    }
                    kindIds.get(relatedObjectKind).addAll(relatedObjectIds);
                }
            }
        }

        if(!kindIds.isEmpty()) {
            List<String> kindsToSearch = new ArrayList<>();
            List<String> idsToSearch = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : kindIds.entrySet()) {
                for (String recordId : entry.getValue()) {
                    String id = PropertyUtil.removeIdPostfix(recordId);
                    RecordData recordData = relatedObjectCache.get(id);
                    Map<String, Object> data = (recordData != null)? recordData.getData() : null;
                    if (data != null) {
                        idData.put(id, data);
                    } else {
                        kindsToSearch.add(entry.getKey());
                        idsToSearch.add(recordId);
                    }
                }
            }
            if (!kindsToSearch.isEmpty()) {
                List<SearchRecord> records = searchRelatedRecords(kindsToSearch, idsToSearch);
                for (SearchRecord searchRecord : records) {
                    Map<String, Object> data = searchRecord.getData();
                    String id = searchRecord.getId();
                    RecordData recordData = new RecordData();
                    recordData.setData(data);
                    relatedObjectCache.put(id, recordData);
                    idData.put(id, data);
                }
            }
        }
        return idData;
    }

    private Map<String, List<RecordChangeInfo>> createRecordChangeInfoMap(Map<String, List<String>> upsertKindIds, Map<String, List<String>> deleteKindIds) {
        Map<String, List<RecordChangeInfo>> recordChangeInfoMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : upsertKindIds.entrySet()) {
            List<RecordChangeInfo> recordChangeInfoList = getOrCreateRecordChangeInfoList(entry.getKey(), recordChangeInfoMap);
            for (String id : entry.getValue()) {
                RecordChangeInfo changeInfo = recordChangeInfoCache.get(id);
                if (changeInfo == null) {
                    changeInfo = new RecordChangeInfo();
                    changeInfo.setRecordInfo(this.createRecordInfo(entry.getKey(), id, OperationType.create));
                }
                recordChangeInfoList.add(changeInfo);
            }
        }
        for (Map.Entry<String, List<String>> entry : deleteKindIds.entrySet()) {
            List<RecordChangeInfo> recordChangeInfoList = getOrCreateRecordChangeInfoList(entry.getKey(), recordChangeInfoMap);
            for (String id : entry.getValue()) {
                RecordChangeInfo changeInfo = new RecordChangeInfo();
                changeInfo.setRecordInfo(this.createRecordInfo(entry.getKey(), id, OperationType.delete));
                recordChangeInfoList.add(changeInfo);
            }
        }

        return recordChangeInfoMap;
    }

    private List<RecordChangeInfo> getOrCreateRecordChangeInfoList(String kind, Map<String, List<RecordChangeInfo>> recordChangeInfoMap) {
        List<RecordChangeInfo> recordChangeInfoList;
        if (recordChangeInfoMap.containsKey(kind)) {
            recordChangeInfoList = recordChangeInfoMap.get(kind);
        } else {
            recordChangeInfoList = new ArrayList<>();
            recordChangeInfoMap.put(kind, recordChangeInfoList);
        }
        return recordChangeInfoList;
    }

    private RecordInfo createRecordInfo(String kind, String id, OperationType operationType) {
        RecordInfo recordInfo = new RecordInfo();
        recordInfo.setKind(kind);
        recordInfo.setId(id);
        recordInfo.setOp(operationType.getValue());
        return recordInfo;
    }

    private List<SchemaItem> getExtendedSchemaItems(Schema schema, PropertyConfiguration configuration, PropertyPath propertyPath) {
        ValueExtraction valueExtraction = propertyPath.getValueExtraction();
        if(augmenterFunctionFactory.isAugmenterFunction(valueExtraction)) {
            IAugmenterFunction augmenterFunction = augmenterFunctionFactory.getAugmenterFunction(valueExtraction);
            return augmenterFunction.getExtendedSchemaItems(configuration.getExtendedPropertyName());
        }

        String relatedPropertyPath = PropertyUtil.removeDataPrefix(valueExtraction.getValuePath());
        List<SchemaItem> extendedSchemaItems;
        if (relatedPropertyPath.contains(ARRAY_SYMBOL)) { // Nested
            extendedSchemaItems = cloneExtendedSchemaItemsFromNestedSchema(Arrays.asList(schema.getSchema()), configuration, relatedPropertyPath);
        } else {// Flatten
            extendedSchemaItems = cloneExtendedSchemaItems(Arrays.asList(schema.getSchema()), configuration, relatedPropertyPath);
        }

        if (extendedSchemaItems.isEmpty()) {
            // It is possible that the format (or schema) of the source property is not defined.
            // In this case, we assume that the format of property is string in order to make its value(s) searchable
            SchemaItem extendedSchemaItem = new SchemaItem();
            extendedSchemaItem.setPath(configuration.getExtendedPropertyName());
            if (configuration.isExtractFirstMatch()) {
                extendedSchemaItem.setKind(STRING_KIND);
            } else {
                extendedSchemaItem.setKind(STRING_ARRAY_KIND);
            }
            extendedSchemaItems.add(extendedSchemaItem);
        }
        return extendedSchemaItems;
    }

    private List<SchemaItem> cloneExtendedSchemaItems(List<SchemaItem> schemaItems, PropertyConfiguration configuration, String relatedPropertyPath) {
        List<SchemaItem> extendedSchemaItems = new ArrayList<>();
        String extendedPropertyName = configuration.getExtendedPropertyName();
        for (SchemaItem schemaItem : schemaItems) {
            if (PropertyUtil.isPropertyPathMatched(schemaItem.getPath(), relatedPropertyPath)) {
                String path = schemaItem.getPath();
                path = path.replace(relatedPropertyPath, extendedPropertyName);
                SchemaItem extendedSchemaItem = new SchemaItem();
                extendedSchemaItem.setPath(path);
                if (configuration.isExtractFirstMatch()) {
                    extendedSchemaItem.setKind(schemaItem.getKind());
                } else {
                    extendedSchemaItem.setKind("[]" + schemaItem.getKind());
                }
                extendedSchemaItems.add(extendedSchemaItem);
            }
        }
        return extendedSchemaItems;
    }

    private List<SchemaItem> cloneExtendedSchemaItemsFromNestedSchema(List<SchemaItem> schemaItems, PropertyConfiguration configuration, String relatedPropertyPath) {
        if (relatedPropertyPath.contains(ARRAY_SYMBOL)) {
            List<SchemaItem> extendedSchemaItems = new ArrayList<>();
            int idx = relatedPropertyPath.indexOf(NESTED_OBJECT_DELIMITER);
            String prePath = relatedPropertyPath.substring(0, idx);
            String postPath = relatedPropertyPath.substring(idx + NESTED_OBJECT_DELIMITER.length());
            for (SchemaItem schemaItem : schemaItems) {
                if (schemaItem.getPath().equals(prePath)) {
                    if (schemaItem.getKind().equals(SCHEMA_NESTED_KIND) && schemaItem.getProperties() != null) {
                        schemaItems = Arrays.asList(schemaItem.getProperties());
                        extendedSchemaItems = cloneExtendedSchemaItemsFromNestedSchema(schemaItems, configuration, postPath);
                    }
                    break;
                }
            }
            return extendedSchemaItems;
        } else {
            return cloneExtendedSchemaItems(schemaItems, configuration, relatedPropertyPath);
        }
    }

    private List<String> getRelatedObjectIds(Map<String, Object> dataMap, RelatedObjectsSpec relatedObjectsSpec) {
        if (dataMap == null || dataMap.isEmpty() || relatedObjectsSpec == null || !relatedObjectsSpec.isValid())
            return new ArrayList<>();

        Map<String, Object> propertyValues = getPropertyValues(dataMap, relatedObjectsSpec.getRelatedObjectID(), relatedObjectsSpec, relatedObjectsSpec.hasValidCondition(), false);
        List<String> relatedObjectIds = new ArrayList<>();
        for (Object value : propertyValues.values()) {
            if (value instanceof List<? extends Object> values) {
                for (Object obj : values) {
                    relatedObjectIds.add(obj.toString());
                }
            } else {
                relatedObjectIds.add(value.toString());
            }
        }
        return relatedObjectIds;
    }

    private void removeOversizedExtendedProperties(String objectId, Map<String, Object> extendedPropertyValues) {
        List<String> propertyNames = new ArrayList<>(extendedPropertyValues.keySet());
        for (String propertyName : propertyNames) {
            Object value = extendedPropertyValues.get(propertyName);
            if( value instanceof List<? extends Object>) {
                List<?> values = (List<?>) value;
                if(values != null && values.size() > maxSizeOfExtendedListValue) {
                    String message = String.format("Extended property '%s' has an over-sized array with %d items. Removed", propertyName, values.size());
                    this.jobStatus.addOrUpdateRecordStatus(objectId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, message);
                    extendedPropertyValues.remove(propertyName);
                }
            }
        }
    }

    private Map<String, Object> getExtendedPropertyValues(String extendedPropertyName, Map<String, Object> dataMap, ValueExtraction valueExtraction, boolean isExtractFirstMatch) {
        if (dataMap == null || dataMap.isEmpty() || valueExtraction == null || !valueExtraction.isValid())
            return new HashMap<>();

        if(augmenterFunctionFactory.isAugmenterFunction(valueExtraction)) {
            IAugmenterFunction augmenterFunction = augmenterFunctionFactory.getAugmenterFunction(valueExtraction);
            Map<String, Object> propertyValues = new HashMap<>();
            List<String> valuePaths = augmenterFunction.getValuePaths(valueExtraction);
            for(String valuePath : valuePaths) {
                Map<String, Object> propertyValue = getPropertyValues(dataMap, valuePath, valueExtraction, valueExtraction.hasValidCondition(), isExtractFirstMatch);
                propertyValues.putAll(propertyValue);
            }
            return augmenterFunction.getPropertyValues(extendedPropertyName, valueExtraction, propertyValues);
        }
        else {
            Map<String, Object> propertyValues = getPropertyValues(dataMap, valueExtraction.getValuePath(), valueExtraction, valueExtraction.hasValidCondition(), isExtractFirstMatch);
            return PropertyUtil.replacePropertyPaths(extendedPropertyName, valueExtraction.getValuePath(), propertyValues);
        }
    }

    private Map<String, Object> getPropertyValues(Map<String, Object> dataMap, String valuePath, RelatedCondition relatedCondition, boolean hasValidCondition, boolean isExtractFirstMatch) {
        valuePath = PropertyUtil.removeDataPrefix(valuePath);
        Map<String, Object> propertyValues = new HashMap<>();
        if (valuePath.contains(ARRAY_SYMBOL)) { // Nested
            propertyValues = getPropertyValuesFromNestedObjects(dataMap, valuePath, relatedCondition, hasValidCondition, isExtractFirstMatch);
        } else { // Flatten
            propertyValues = getPropertyValueOfNoneNestedProperty(dataMap, valuePath, relatedCondition, hasValidCondition);
            if(!isExtractFirstMatch) {
                Map<String, Object> tmpValues = new HashMap<>();
                for(Map.Entry<String, Object> entry : propertyValues.entrySet()) {
                    if (entry.getValue() instanceof List<? extends Object>) {
                        tmpValues.put(entry.getKey(), entry.getValue());
                    }
                    else {
                        List<Object> values = new ArrayList<>();
                        values.add(entry.getValue());
                        tmpValues.put(entry.getKey(), values);
                    }
                }
                propertyValues = tmpValues;
            }
        }

        return propertyValues;
    }

    private Map<String, Object> getPropertyValuesFromNestedObjects(Map<String, Object> dataMap, String valuePath, RelatedCondition relatedCondition, boolean hasCondition, boolean isExtractFirstMatch) {
        Map<String, Object> propertyValues = new HashMap<>();

        if (valuePath.contains(ARRAY_SYMBOL)) {
            int idx = valuePath.indexOf(NESTED_OBJECT_DELIMITER);
            String prePath = valuePath.substring(0, idx);
            String postPath = valuePath.substring(idx + NESTED_OBJECT_DELIMITER.length());
            try {
                if (dataMap.containsKey(prePath) && dataMap.get(prePath) != null) {
                    List<Map<String, Object>> nestedObjects = (List<Map<String, Object>>) dataMap.get(prePath);
                    for (Map<String, Object> nestedObject : nestedObjects) {
                        Map<String, Object> subPropertyValues = getPropertyValuesFromNestedObjects(nestedObject, postPath, relatedCondition, hasCondition, isExtractFirstMatch);
                        for (Map.Entry<String, Object> entry: subPropertyValues.entrySet()) {
                            String key = prePath + ARRAY_SYMBOL + PROPERTY_DELIMITER + entry.getKey();
                            if(isExtractFirstMatch) {
                                propertyValues.put(key, entry.getValue());
                            }
                            else {
                                List<Object> values = propertyValues.containsKey(key)
                                        ? (List<Object>)propertyValues.get(key)
                                        : new ArrayList<>();
                                if(entry.getValue() instanceof List<? extends Object> valueList) {
                                    values.addAll(valueList);
                                }
                                else {
                                    values.add(entry.getValue());
                                }
                                propertyValues.put(key, values);
                            }
                        }
                        if (!subPropertyValues.isEmpty() && isExtractFirstMatch)
                            break;
                    }
                }
            } catch (Exception ex) {
                //Ignore cast exception
            }
        } else {
            propertyValues = getPropertyValueOfNoneNestedProperty(dataMap, valuePath, relatedCondition, hasCondition);
        }
        return propertyValues;
    }

    private Map<String, Object> getPropertyValueOfNoneNestedProperty(Map<String, Object> dataMap, String valuePath, RelatedCondition relatedCondition, boolean hasCondition) {
        Map<String, Object> propertyValue = PropertyUtil.getValueOfNoneNestedProperty(valuePath, dataMap);
        if(!propertyValue.isEmpty() && hasCondition) {
            String conditionProperty = relatedCondition.getRelatedConditionProperty();
            int idx = conditionProperty.lastIndexOf(NESTED_OBJECT_DELIMITER);
            if(idx > 0)
                conditionProperty = conditionProperty.substring(idx + NESTED_OBJECT_DELIMITER.length());
            Map<String, Object> values = PropertyUtil.getValueOfNoneNestedProperty(conditionProperty, dataMap);
            boolean matched = false;
            if (values.containsKey(conditionProperty) && values.get(conditionProperty) != null) {
                String conditionPropertyValue = values.get(conditionProperty).toString();
                matched = relatedCondition.isMatch(conditionPropertyValue);
            }
            if(!matched) {
                // Reset the propertyValue if there is no match
                propertyValue = new HashMap<>();
            }
        }
        return propertyValue;
    }


    private List<String> getChildrenKinds(String parentKind) {
        final String parentKindWithMajor = PropertyUtil.getKindWithMajor(parentKind);
        ChildrenKinds childrenKinds = childrenKindsCache.get(parentKindWithMajor);
        if(childrenKinds == null) {
            childrenKinds = new ChildrenKinds();
            Set<String> kinds = new HashSet<>();
            for (AugmenterConfiguration propertyConfigurations: searchChildrenKindConfigurations(parentKindWithMajor)) {
                kinds.add(propertyConfigurations.getCode());
            }
            childrenKinds.setKinds(new ArrayList<>(kinds));
            childrenKindsCache.put(parentKindWithMajor, childrenKinds);
        }

        return childrenKinds.getKinds();
    }

    private ParentChildRelationshipSpecs getParentChildRelatedObjectsSpecs(String childKind) {
        final String childKindWithMajor = PropertyUtil.getKindWithMajor(childKind);

        ParentChildRelationshipSpecs specs = parentChildRelationshipSpecsCache.get(childKindWithMajor);
        if (specs == null) {
            List<ParentChildRelationshipSpec> specsList = new ArrayList<>();
            specs = new ParentChildRelationshipSpecs();
            specs.setSpecList(specsList);

            List<AugmenterConfiguration> augmenterConfigurations = searchParentKindConfigurations((childKindWithMajor));
            for (AugmenterConfiguration augmenterConfiguration : augmenterConfigurations) {
                for (PropertyConfiguration propertyConfiguration : augmenterConfiguration.getConfigurations()) {
                    List<PropertyPath> matchedPropertyPaths = propertyConfiguration.getPaths().stream().filter(p ->
                                            p.hasValidRelatedObjectsSpec() &&
                                            p.getRelatedObjectsSpec().isParentToChildren() &&
                                            p.getRelatedObjectsSpec().getRelatedObjectKind().contains(childKindWithMajor))
                            .toList();
                    for(PropertyPath propertyPath: matchedPropertyPaths) {
                        ParentChildRelationshipSpec spec = toParentChildRelationshipSpec(propertyPath, augmenterConfiguration.getCode(), childKindWithMajor);
                        boolean merged = false;
                        for(ParentChildRelationshipSpec sp: specsList) {
                            if(sp.equals(spec)) {
                                List<String> childValuePaths = sp.getChildValuePaths();
                                if(!childValuePaths.contains(spec.getChildValuePaths().get(0))) {
                                    childValuePaths.add(spec.getChildValuePaths().get(0));
                                }
                                merged = true;
                                break;
                            }
                        }
                        if(!merged) {
                            specsList.add(spec);
                        }
                    }
                }
            }

            parentChildRelationshipSpecsCache.put(childKindWithMajor, specs);
        }

        return specs;
    }

    private ParentChildRelationshipSpec toParentChildRelationshipSpec(PropertyPath propertyPath, String parentKind, String childKind) {
        ParentChildRelationshipSpec spec = new ParentChildRelationshipSpec();
        spec.setParentKind(parentKind);
        spec.setParentObjectIdPath(propertyPath.getRelatedObjectsSpec().getRelatedObjectID());
        spec.setChildKind(childKind);
        String valuePath = PropertyUtil.removeDataPrefix(propertyPath.getValueExtraction().getValuePath());
        spec.getChildValuePaths().add(valuePath);
        return spec;
    }

    private void updateAssociatedParentRecords(String ancestors, String childKind, List<RecordChangeInfo> childRecordChangeInfos, List<SearchRecord> deletedRecordsWithParent) {
        ParentChildRelationshipSpecs specs = getParentChildRelatedObjectsSpecs(childKind);
        Set<String> ancestorSet = new HashSet<>(Arrays.asList(ancestors.split(ANCESTRY_KINDS_DELIMITER)));
        for (ParentChildRelationshipSpec spec : specs.getSpecList()) {
            List<String> childRecordIds = getChildRecordIdsWithExtendedPropertiesChanged(spec, childRecordChangeInfos);

            List<String> parentIds = new ArrayList<>();
            if (!childRecordIds.isEmpty()) {
                parentIds = searchUniqueParentIds(childKind, childRecordIds, spec.getParentObjectIdPath());
            }
            List<String> parentIdsOfDeletedRecords = getUniqueParentIdsOfDeletedRecords(childKind, deletedRecordsWithParent, spec.getParentObjectIdPath());
            parentIds.addAll(parentIdsOfDeletedRecords);
            if (parentIds.isEmpty())
                continue;

            final int limit = configurationProperties.getStorageRecordsByKindBatchSize();
            Map<String, List<String>> parentKindIds = searchKindIds(spec.getParentKind(), parentIds);
            List<RecordInfo> recordInfos = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : parentKindIds.entrySet()) {
                if (ancestorSet.contains(entry.getKey()))
                    continue; // circular indexing found.

                for (String id : entry.getValue()) {
                    RecordInfo recordInfo = new RecordInfo();
                    recordInfo.setKind(entry.getKey());
                    recordInfo.setId(id);
                    recordInfo.setOp(OperationType.update.getValue());
                    recordInfos.add(recordInfo);

                    if (recordInfos.size() >= limit) {
                        createWorkerTask(ancestors, recordInfos);
                        recordInfos = new ArrayList<>();
                    }
                }
            }
            if (!recordInfos.isEmpty()) {
                createWorkerTask(ancestors, recordInfos);
            }
        }
    }

    private List<String> getChildRecordIdsWithExtendedPropertiesChanged(ParentChildRelationshipSpec spec, List<RecordChangeInfo> childRecordChangeInfos) {
        List<String> childRecordIds = new ArrayList<>();
        for (RecordChangeInfo recordChangeInfo : childRecordChangeInfos) {
            if (recordChangeInfo.getRecordInfo().getOp().equals(OperationType.update.getValue())) {
                String updatedExtendedProperty = recordChangeInfo.getUpdatedProperties().stream().filter(p -> {
                    for (String valuePath : spec.getChildValuePaths()) {
                        if (PropertyUtil.isPropertyPathMatched(valuePath, p) ||
                            PropertyUtil.isPropertyPathMatched(p, valuePath)) {
                            return true;
                        }
                    }
                    return false;
                }).findFirst().orElse(null);

                if (updatedExtendedProperty != null) {
                    // The parent property that is extended by the children was updated
                    childRecordIds.add(recordChangeInfo.getRecordInfo().getId());
                }
            }
            else {
                childRecordIds.add(recordChangeInfo.getRecordInfo().getId());
            }
        }
        return childRecordIds;
    }

    private boolean areExtendedPropertiesChanged(String childKind, List<RecordChangeInfo> parentRecordChangeInfos) {
        if (parentRecordChangeInfos.stream().filter(info -> !info.getRecordInfo().getOp().equals(OperationType.update.getValue())).findFirst().orElse(null) != null) {
            // If there is any OP of the parent record(s) that is not OperationType.update. It must be OperationType.delete in this case. Then the child record should be updated
            return true;
        }

        AugmenterConfiguration augmenterConfiguration = this.getConfiguration(childKind);
        if(augmenterConfiguration != null) {
            for (PropertyConfiguration propertyConfiguration : augmenterConfiguration.getConfigurations()) {
                for (PropertyPath propertyPath : propertyConfiguration.getPaths().stream().filter(
                        p -> p.hasValidValueExtraction() && p.hasValidRelatedObjectsSpec()).toList()) {
                    String relatedObjectKind = propertyPath.getRelatedObjectsSpec().getRelatedObjectKind();
                    String valuePath = PropertyUtil.removeDataPrefix(propertyPath.getValueExtraction().getValuePath());

                    // Find any parent record which has changed property that is extended by the child (kind)
                    RecordChangeInfo parentRecordChangeInfo = parentRecordChangeInfos.stream().filter(info -> {
                        if (PropertyUtil.hasSameMajorKind(info.getRecordInfo().getKind(), relatedObjectKind)) {
                            List<String> matchedProperties = info.getUpdatedProperties().stream().filter(
                                    p -> PropertyUtil.isPropertyPathMatched(p, valuePath) || PropertyUtil.isPropertyPathMatched(valuePath, p)).toList();
                            return !matchedProperties.isEmpty();
                        }
                        return false;
                    }).findFirst().orElse(null);
                    if (parentRecordChangeInfo != null) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void updateAssociatedChildrenRecords(String ancestors, String parentKind, List<RecordChangeInfo> recordChangeInfos) {
        List<String> childrenKinds = getChildrenKinds(parentKind);
        for (String ancestryKind : ancestors.split(ANCESTRY_KINDS_DELIMITER)) {
            // Exclude the kinds in the ancestryKinds to prevent circular chasing
            childrenKinds.removeIf(ancestryKind::contains);
        }
        if(childrenKinds.isEmpty()) {
            return;
        }

        List<String> multiKinds = new ArrayList<>();
        for(String kind: childrenKinds) {
            String kindWithMajor = PropertyUtil.getKindWithMajor(kind) + "*.*";
            multiKinds.add(kindWithMajor);
        }
        List<String> processedIds = recordChangeInfos.stream().map(recordChangeInfo -> recordChangeInfo.getRecordInfo().getId()).toList();
        String queryString = String.format("data.%s:(%s)", ASSOCIATED_IDENTITIES_PROPERTY, QueryUtil.createIdsFilter(processedIds));
        Query query = QueryUtil.createSimpleTextQuery(queryString);
        List<String> returnedFields = List.of("kind", "id", "data." + ASSOCIATED_IDENTITIES_PROPERTY);
        List<SearchRecord> records = this.search(multiKinds, query, null, returnedFields, NO_LIMIT);

        List<RecordInfo> recordInfos = new ArrayList<>();
        for (SearchRecord searchRecord : records) {
            Map<String, Object> data = searchRecord.getData();
            if (!data.containsKey(ASSOCIATED_IDENTITIES_PROPERTY) || data.get(ASSOCIATED_IDENTITIES_PROPERTY) == null)
                continue;

            List<String> associatedParentIds = (List<String>) data.get(ASSOCIATED_IDENTITIES_PROPERTY);
            List<RecordChangeInfo> associatedParentRecordChangeInfos = recordChangeInfos.stream().filter(
                    info -> associatedParentIds.contains(info.getRecordInfo().getId())).toList();
            if (areExtendedPropertiesChanged(searchRecord.getKind(), associatedParentRecordChangeInfos)) {
                RecordInfo recordInfo = new RecordInfo();
                recordInfo.setKind(searchRecord.getKind());
                recordInfo.setId(searchRecord.getId());
                recordInfo.setOp(OperationType.update.getValue());
                recordInfos.add(recordInfo);

                if (recordInfos.size() >= configurationProperties.getStorageRecordsByKindBatchSize()) {
                    createWorkerTask(ancestors, recordInfos);
                    recordInfos = new ArrayList<>();
                }
            }
        }
        if (!recordInfos.isEmpty()) {
            createWorkerTask(ancestors, recordInfos);
        }
    }

    private String getLatestVersionOfKind(String kindWithMajor) {
        List<String> concreteKinds = getConcreteKinds(kindWithMajor, true);
        if(concreteKinds.isEmpty())
            return null;
        else
            return concreteKinds.get(0);
    }

    private List<String> getConcreteKinds(String kindWithMajor, boolean latestVersion) {
        Kind kind = new Kind(kindWithMajor);
        String version = kind.getVersion();
        String[] subVersions = version.split("\\.");
        String majorVersion = subVersions[0];
        List<String> concreteKinds = new ArrayList<>();
        try {
            SchemaInfoResponse response = schemaService.getSchemaInfos(kind.getAuthority(), kind.getSource(), kind.getType(), majorVersion, null, null, latestVersion);
            if (response != null && !CollectionUtils.isEmpty(response.getSchemaInfos())) {
                for(SchemaInfo schemaInfo : response.getSchemaInfos()) {
                    SchemaIdentity schemaIdentity = schemaInfo.getSchemaIdentity();
                    String concreteKind = schemaIdentity.getAuthority() + ":" +
                            schemaIdentity.getSource() + ":" +
                            schemaIdentity.getEntityType() + ":" +
                            schemaIdentity.getSchemaVersionMajor() + "." +
                            schemaIdentity.getSchemaVersionMinor() + "." +
                            schemaIdentity.getSchemaVersionPatch();
                    concreteKinds.add(concreteKind);
                }
            }
        } catch (Exception e) {
            jaxRsDpsLog.error("failed to get schema info", e);
        }

        return concreteKinds;
    }

    private List<String> getUniqueParentIdsOfDeletedRecords(String childKind, List<SearchRecord> deletedRecords, String parentObjectIdPath) {
        Set<String> parentIds = new HashSet<>();
        parentObjectIdPath = PropertyUtil.removeDataPrefix(parentObjectIdPath);
        for (SearchRecord searchRecord : deletedRecords) {
            if (searchRecord.getKind().equals(childKind) && searchRecord.getData().containsKey(parentObjectIdPath)) {
                Object id = searchRecord.getData().get(parentObjectIdPath);
                if (id != null && !parentIds.contains(id)) {
                    parentIds.add(id.toString());
                }
            }
        }
        return new ArrayList<>(parentIds);
    }

    /****************************** search methods that use search service to get the data **************************************/
    private AugmenterConfiguration searchConfiguration(String kind) {
        String queryString = String.format("data.Code: \"%s\"", kind);
        Query query = QueryUtil.createSimpleTextQuery(queryString);
        // If there is more than PropertyConfigurations, pick the one that was last modified.
        // Given the property "modifyTime" is not set for new created record, we use property "version"
        // to sort the search result in descending order
        List<SortOptions> sortOptionsList = null;
        try {
            sortOptionsList = QueryUtil.createSortOptionsList(List.of(VERSION_PROPERTY), List.of(SortOrder.Desc));
        } catch(Exception ex) {
          // Should not reach here. Ignore
        }

        List<AugmenterConfiguration> augmenterConfigurations = searchConfigurations(query, sortOptionsList);
        if(!augmenterConfigurations.isEmpty()) {
            if(augmenterConfigurations.size() > 1) {
                jaxRsDpsLog.warning(String.format("There is more than one PropertyConfigurations for kind: %s", kind));
            }
            return augmenterConfigurations.get(0);
        }
        return null;
    }

    private List<AugmenterConfiguration> searchParentKindConfigurations(String childKind) {
        Query query = QueryUtil.createQueryForParentConfigs(childKind);
        return searchConfigurations(query, null);
    }

    private List<AugmenterConfiguration> searchChildrenKindConfigurations(String parentKind) {
        Query query = QueryUtil.createQueryForChildrenConfigs(parentKind);
        return searchConfigurations(query,null);
    }

    private List<AugmenterConfiguration> searchConfigurations(Query query, List<SortOptions> sortOptions) {
        List<AugmenterConfiguration> augmenterConfigurations = new ArrayList<>();
        List<SearchRecord> records = this.search(INDEX_PROPERTY_PATH_CONFIGURATION_KIND, query, sortOptions, null, NO_LIMIT);
        for (SearchRecord searchRecord : records) {
            try {
                String data = objectMapper.writeValueAsString(searchRecord.getData());
                AugmenterConfiguration configurations = objectMapper.readValue(data, AugmenterConfiguration.class);
                String kind = PropertyUtil.getKindWithMajor(configurations.getCode());
                augmenterConfigurationCache.put(kind, configurations);
                augmenterConfigurations.add(configurations);
            } catch (JsonProcessingException e) {
                jaxRsDpsLog.error("Augmenter(searchConfigurations): failed to deserialize PropertyConfigurations object", e);
            }
        }
        return augmenterConfigurations;
    }

    private List<SearchRecord> searchRelatedRecords(List<String> relatedObjectKinds, List<String> relatedObjectIds) {
        List<String> kinds = new ArrayList<>();
        for(String kind : relatedObjectKinds) {
            if(!PropertyUtil.isConcreteKind(kind))
                kind += "*";
            kinds.add(kind);
        }
        String queryString = createIdsQuery(relatedObjectIds);
        Query query = QueryUtil.createSimpleTextQuery(queryString);
        return this.search(kinds, query, null, null, NO_LIMIT);
    }

    private Map<String, List<String>> searchKindIds(String majorKind, List<String> ids) {
        String kind = PropertyUtil.isConcreteKind(majorKind) ? majorKind : majorKind + "*";
        String queryString = createIdsQuery(ids);
        Query query = QueryUtil.createSimpleTextQuery(queryString);
        List<String> returnedFields = List.of("kind", "id");

        Map<String, List<String>> kindIds = new HashMap<>();
        List<SearchRecord> records = this.search(kind, query, null, returnedFields, NO_LIMIT);
        for (SearchRecord searchRecord : records) {
            if (kindIds.containsKey(searchRecord.getKind())) {
                kindIds.get(searchRecord.getKind()).add(searchRecord.getId());
            } else {
                List<String> idList = new ArrayList<>();
                idList.add(searchRecord.getId());
                kindIds.put(searchRecord.getKind(), idList);
            }
        }
        return kindIds;
    }

    private List<String> searchUniqueParentIds(String childKind, List<String> childRecordIds, String parentObjectIdPath) {
        Set<String> parentIds = new HashSet<>();
        String queryString = createIdsQuery(childRecordIds);
        Query query = QueryUtil.createSimpleTextQuery(queryString);
        List<String> returnedFields = Arrays.asList(parentObjectIdPath);

        List<SearchRecord> searchRecords = this.search(childKind, query, null, returnedFields, NO_LIMIT);
        parentObjectIdPath = PropertyUtil.removeDataPrefix(parentObjectIdPath);
        Map<String, SearchRecord> idRecords = searchRecords.stream().collect(Collectors.toMap(SearchRecord::getId, record -> record));
        for(String childRecordId :  childRecordIds) {
            RecordData recordData = this.relatedObjectCache.get(childRecordId);
            Map<String, Object> data = null;
            if(recordData != null && recordData.getData() != null) {
                data = recordData.getData();
            }
            else {
                SearchRecord searchRecord = idRecords.getOrDefault(childRecordId, null);
                if(searchRecord != null) {
                    data = searchRecord.getData();
                }
            }
            if(data != null && data.containsKey(parentObjectIdPath)) {
                Object id = data.get(parentObjectIdPath);
                if (id != null && !parentIds.contains(id)) {
                    parentIds.add(id.toString());
                }
            }
        }
        return new ArrayList<>(parentIds);
    }

    private List<SearchRecord> searchChildrenRecords(String childrenObjectKind, String childrenObjectField, String parentId) {
        String kind = PropertyUtil.isConcreteKind(childrenObjectKind) ? childrenObjectKind : childrenObjectKind + "*";
        String queryString = String.format("%s: \"%s\"", childrenObjectField, parentId);
        Query query = QueryUtil.createSimpleTextQuery(queryString);
        return this.search(kind, query, null, null, NO_LIMIT);
    }

    private SearchRecord searchFirstRecord(String kind, Query query) {
        List<SearchRecord> results = this.search(kind, query, null, null, 1);
        if (!results.isEmpty()) {
            return results.get(0);
        }
        return null;
    }

    private List<SearchRecord> search(String kind, Query query, List<SortOptions> sortOptions, List<String> returnedFields, int limit) {
        try {
            return searchClient.search(kind, query, sortOptions, returnedFields, limit);
        }
        catch (Exception ex) {
            this.jaxRsDpsLog.error(SEARCH_GENERAL_ERROR, ex);
        }
        return new ArrayList<>();
    }

    private List<SearchRecord> search(List<String> kinds, Query query, List<SortOptions> sortOptions, List<String> returnedFields, int limit) {
        try {
            return searchClient.search(kinds, query, sortOptions, returnedFields, limit);
        }
        catch (Exception ex) {
            this.jaxRsDpsLog.error(SEARCH_GENERAL_ERROR, ex);
        }
        return new ArrayList<>();
    }
}
