// Copyright 2017-2019, Schlumberger
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

import static org.opengroup.osdu.indexer.model.Constants.AS_INGESTED_COORDINATES_FEATURE_NAME;
import static org.opengroup.osdu.indexer.util.ElasticsearchBulkErrorUtils.DOCUMENT_PARSING_EXCEPTION;
import static org.opengroup.osdu.indexer.util.ElasticsearchBulkErrorUtils.MAPPER_PARSING_EXCEPTION;
import static org.opengroup.osdu.indexer.util.ElasticsearchBulkErrorUtils.buildErrorReason;
import static org.opengroup.osdu.indexer.util.ElasticsearchBulkErrorUtils.incrementParsingExceptionFallbackCounter;
import static org.opengroup.osdu.indexer.util.ElasticsearchBulkErrorUtils.isParsingException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.RequestStatus;
import org.opengroup.osdu.core.common.model.indexer.IndexSchema;
import org.opengroup.osdu.core.common.model.indexer.IndexingStatus;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.indexer.RecordIndexerPayload;
import org.opengroup.osdu.core.common.model.indexer.RecordIndexerPayload.Record;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.indexer.RecordStatus;
import org.opengroup.osdu.core.common.model.indexer.Records;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.core.common.model.search.RecordMetaAttribute;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;
import org.opengroup.osdu.indexer.logging.AuditLogger;
import org.opengroup.osdu.indexer.model.BulkRequestResult;
import org.opengroup.osdu.indexer.model.SearchRecord;
import org.opengroup.osdu.indexer.model.XcollaborationHolder;
import org.opengroup.osdu.indexer.model.indexproperty.AugmenterConfiguration;
import org.opengroup.osdu.indexer.provider.interfaces.IPublisher;
import org.opengroup.osdu.indexer.service.exception.ElasticsearchMappingException;
import org.opengroup.osdu.indexer.util.AugmenterSetting;
import org.opengroup.osdu.indexer.util.IndexerQueueTaskBuilder;
import org.opengroup.osdu.indexer.util.PropertyUtil;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class IndexerServiceImpl implements IndexerService {

    private static final Time BULK_REQUEST_TIMEOUT = Time.of(builder -> builder.time("1m"));

    private static final List<Integer> RETRY_ELASTIC_EXCEPTION = List.of(
        HttpStatus.SC_TOO_MANY_REQUESTS,
        HttpStatus.SC_BAD_GATEWAY,
        HttpStatus.SC_SERVICE_UNAVAILABLE,
        HttpStatus.SC_FORBIDDEN
    );

    private static final String MAPPER_PARSING_EXCEPTION_TYPE = "type=mapper_parsing_exception";
    private static final String RECORD_ID_LOG = "record-id: %s | %s";
    private static final String ELASTIC_ERROR = "Elastic error";
    public static final String X_COLLABORATION = "x-collaboration";

    private final Gson gson = new GsonBuilder().serializeNulls().create();

    // we index a normalized kind (authority + source + entity type + major version) as a tags attribute for all records
    private static final String NORMALIZATION_KIND_TAG_ATTRIBUTE_NAME = "normalizedKind";
    private static final String VERTICAL_COORDINATE_REFERENCE_SYSTEM_ID = "VerticalCoordinateReferenceSystemID";
    private static final String INDEX_PROPERTY_PATH_CONFIGURATION_KIND_WITH_MAJOR = "osdu:wks:reference-data--IndexPropertyPathConfiguration:1.";

    @Inject
    private JaxRsDpsLog jaxRsDpsLog;
    @Inject
    private AuditLogger auditLogger;
    @Inject
    private StorageService storageService;
    @Inject
    private IndexSchemaService schemaService;
    @Inject
    private IndicesService indicesService;
    @Inject
    private IMappingService mappingService;
    @Inject
    private IPublisher progressPublisher;
    @Autowired
    private RequestScopedElasticsearchClient requestScopedClient;
    @Inject
    private IndexerQueueTaskBuilder indexerQueueTaskBuilder;
    @Inject
    private ElasticIndexNameResolver elasticIndexNameResolver;
    @Inject
    private StorageIndexerPayloadMapper storageIndexerPayloadMapper;
    @Inject
    private IRequestInfo requestInfo;
    @Inject
    private JobStatus jobStatus;
    @Inject
    private AugmenterConfigurationService augmenterConfigurationService;
    @Autowired(required = false)
    private MeterRegistry meterRegistry;
    @Inject
    private AugmenterSetting augmenterSetting;

    @Autowired
    private IFeatureFlag asIngestedCoordinatesFeatureFlag;

    private DpsHeaders headers;

    @Autowired
    private XcollaborationHolder xcollaborationHolder;

    @Override
    public JobStatus processRecordChangedMessages(RecordChangedMessages message, List<RecordInfo> recordInfos) throws Exception {

        // extract x-collaboration value to a request scoped special holder
        String xCollaboration = message.getAttributes().get(DpsHeaders.COLLABORATION);
        xcollaborationHolder.setxCollaborationHeader(xCollaboration);

        // this should not happen
        if (recordInfos.isEmpty()) return null;

        String errorMessage = "";
        List<String> retryRecordIds = new LinkedList<>();

        // get auth header with service account Authorization
        this.headers = this.requestInfo.getHeadersWithDwdAuthZ();

        // initialize status for all messages.
        this.jobStatus.initialize(recordInfos);

        try {
            auditLogger.indexStarted(recordInfos.stream()
                    .map(entry -> String.format("id=%s kind=%s operationType=%s", entry.getId(), entry.getKind(), entry.getOp()))
                    .collect(Collectors.toList()));

            // get upsert records
            Map<String, Map<String, OperationType>> upsertRecordMap = RecordInfo.getUpsertRecordIds(recordInfos);
            if (upsertRecordMap != null && !upsertRecordMap.isEmpty()) {
                List<String> upsertFailureRecordIds = processUpsertRecords(upsertRecordMap, recordInfos);
                retryRecordIds.addAll(upsertFailureRecordIds);
            }

            // get delete records
            Map<String, List<String>> deleteRecordMap = RecordInfo.getDeleteRecordIds(recordInfos);
            List<SearchRecord> deletedRecordsWithParentReferred = new ArrayList<>();
            if (this.augmenterSetting.isEnabled()) {
                deletedRecordsWithParentReferred = augmenterConfigurationService.getAllRecordsReferredByParentRecords(deleteRecordMap);
            }
            if (deleteRecordMap != null && !deleteRecordMap.isEmpty()) {
                List<String> deleteFailureRecordIds = processDeleteRecords(deleteRecordMap);
                retryRecordIds.addAll(deleteFailureRecordIds);
            }

            // process legacy storage schema change messages
            Map<String, OperationType> schemaMsgs = RecordInfo.getSchemaMsgs(recordInfos);
            if (schemaMsgs != null && !schemaMsgs.isEmpty()) {
                this.schemaService.processSchemaMessages(schemaMsgs);
            }

            // process failed records
            if (!retryRecordIds.isEmpty()) {
                retryAndEnqueueFailedRecords(recordInfos, retryRecordIds, message);
            }

            if (this.augmenterSetting.isEnabled()) {
                Map<String, List<String>> upsertKindIds = null;
                Map<String, List<String>> deleteKindIds = null;
                try {
                    upsertKindIds = getUpsertRecordIdsForConfigurationsEnabledKinds(upsertRecordMap, retryRecordIds);
                    deleteKindIds = getDeleteRecordIdsForConfigurationsEnabledKinds(deleteRecordMap, retryRecordIds);
                    if (!upsertKindIds.isEmpty() || !deleteKindIds.isEmpty()) {
                        augmenterConfigurationService.updateAssociatedRecords(message, upsertKindIds, deleteKindIds, deletedRecordsWithParentReferred);
                    }
                    List<String> configurationIds = upsertRecordMap.entrySet().stream()
                            .filter(entry -> isIndexPropertyPathConfigurationKind(entry.getKey()))
                            .flatMap(entry -> entry.getValue().keySet().stream())
                            .filter(id -> !retryRecordIds.contains(id))
                            .distinct()
                            .toList();
                    if (!configurationIds.isEmpty()) {
                        updateSchemaMappingOfRelatedKinds(configurationIds);
                    }
                }
                catch(Exception ex) {
                    List<String> ids = new ArrayList<>();
                    if(upsertKindIds != null) {
                        upsertKindIds.values().forEach(ids::addAll);
                    }
                    if(deleteKindIds != null) {
                        deleteKindIds.values().forEach(ids::addAll);
                    }
                    jaxRsDpsLog.error(String.format("Augmenter: Failed to update associated records of the records:[%s]", String.join(",", ids)), ex);
                }
            }
        } catch (IOException e) {
            errorMessage = e.getMessage();
            throw new AppException(HttpStatus.SC_GATEWAY_TIMEOUT, "Internal communication failure", errorMessage, e);
        } catch (AppException e) {
            errorMessage = e.getMessage();
            throw e;
        } catch (Exception e) {
            errorMessage = "Error indexing records: " + e.getMessage();
            this.jobStatus.getDebugInfos().add(e.toString());
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unknown error", "An unknown error has occurred.", e);
        } finally {
            this.jobStatus.finalizeRecordStatus(errorMessage);
            this.updateAuditLog();
            this.progressPublisher.publishStatusChangedTagsToTopic(this.headers, this.jobStatus);
        }

        return jobStatus;
    }

    @Override
    public void processSchemaMessages(List<RecordInfo> recordInfos) throws IOException {
        Map<String, OperationType> schemaMsgs = RecordInfo.getSchemaMsgs(recordInfos);
        if (!schemaMsgs.isEmpty()) {
            final ElasticsearchClient restClient = requestScopedClient.getClient();
            schemaMsgs.entrySet().forEach(msg -> {
                try {
                    processSchemaEvents(restClient, msg);
                } catch (IOException | ElasticsearchException e) {
                    throw new AppException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "unable to process schema delete", e.getMessage());
                }
            });
        }
    }

    private Map<String, List<String>> getUpsertRecordIdsForConfigurationsEnabledKinds(Map<String, Map<String, OperationType>> upsertRecordMap, List<String> retryRecordIds) {
        Map<String, List<String>> upsertKindIds = new HashMap<>();
        for (Map.Entry<String, Map<String, OperationType>> entry : upsertRecordMap.entrySet()) {
            String kind = entry.getKey();
            if(augmenterConfigurationService.isConfigurationEnabled(kind)) {
                List<String> processedIds = entry.getValue().keySet().stream().filter(id -> !retryRecordIds.contains(id)).collect(Collectors.toList());
                if (!processedIds.isEmpty()) {
                    upsertKindIds.put(kind, processedIds);
                }
            }
        }
        return upsertKindIds;
    }

    private Map<String, List<String>> getDeleteRecordIdsForConfigurationsEnabledKinds(Map<String, List<String>> deleteRecordMap, List<String> retryRecordIds) {
        Map<String, List<String>> deletedRecordKindIdsMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : deleteRecordMap.entrySet()) {
            String kind = entry.getKey();
            if(augmenterConfigurationService.isConfigurationEnabled(kind)) {
                List<String> processedIds = entry.getValue().stream().filter(id -> !retryRecordIds.contains(id)).collect(Collectors.toList());
                if (!processedIds.isEmpty()) {
                    deletedRecordKindIdsMap.put(kind, processedIds);
                }
            }
        }
        return deletedRecordKindIdsMap;
    }

    private void updateSchemaMappingOfRelatedKinds(List<String> configurationIds) {
        List<String> relatedKinds = augmenterConfigurationService.getRelatedKindsOfConfigurations(configurationIds);
        if(!relatedKinds.isEmpty()) {
            try{
                ElasticsearchClient restClient = requestScopedClient.getClient();
                for (String kind : relatedKinds) {
                    try {
                        this.schemaService.processSchemaUpsertEvent(restClient, kind);
                    }
                    catch(Exception e) {
                        jaxRsDpsLog.error(String.format("Augmenter: Failed to update schema mapping for kind %s", kind), e);
                    }
                }
            } catch(Exception ex) {
                jaxRsDpsLog.error(String.format("Augmenter: Failed to update schema mapping for related kinds of configurations: %s",
                                String.join(",", configurationIds)), ex);
            }
        }
    }

    private void processSchemaEvents(ElasticsearchClient restClient, Map.Entry<String, OperationType> msg)
        throws IOException, ElasticsearchException {
        String kind = msg.getKey();
        String index = elasticIndexNameResolver.getIndexNameFromKind(kind);

        boolean indexExist = indicesService.isIndexExist(restClient, index);
        if (indexExist && msg.getValue() == OperationType.purge_schema) {
            indicesService.deleteIndex(restClient, index);
            schemaService.invalidateSchemaCache(kind);
        }
    }

    private List<String> processUpsertRecords(Map<String, Map<String, OperationType>> upsertRecordMap, List<RecordInfo> recordChangedInfos) throws Exception {
        // get schema for kind
        Map<String, IndexSchema> schemas = this.getSchema(upsertRecordMap);

        if (schemas.isEmpty()) return new LinkedList<>();

        // get recordIds with valid upsert index-status
        List<String> recordIds = this.jobStatus.getIdsByValidUpsertIndexingStatus();

        if (recordIds.isEmpty()) return new LinkedList<>();

        // get records via storage api
        Records storageRecords = this.storageService.getStorageRecords(recordIds, recordChangedInfos);
        List<String> failedOrRetryRecordIds = new LinkedList<>(storageRecords.getMissingRetryRecords());

        // map storage records to indexer payload
        RecordIndexerPayload recordIndexerPayload = this.getIndexerPayload(upsertRecordMap, schemas, storageRecords);

        jaxRsDpsLog.info(String.format("records change messages received : %s | valid storage bulk records: %s | valid index payload: %s", recordIds.size(), storageRecords.getRecords().size(), recordIndexerPayload.getRecords().size()));

        // index records
        failedOrRetryRecordIds.addAll(processElasticMappingAndUpsertRecords(recordIndexerPayload));

        return failedOrRetryRecordIds;
    }

    private Map<String, IndexSchema> getSchema(Map<String, Map<String, OperationType>> upsertRecordMap) {

        Map<String, IndexSchema> schemas = new HashMap<>();

        try {
            for (Map.Entry<String, Map<String, OperationType>> entry : upsertRecordMap.entrySet()) {

                String kind = entry.getKey();
                List<String> errors = new ArrayList<>();
                IndexSchema schemaObj = this.schemaService.getIndexerInputSchema(kind, errors);
                String error = errors.isEmpty() ? "" : String.join("|", errors);
                String debugInfo = errors.isEmpty() ? String.format("kind: %s", kind) : String.format("kind: %s | errors: %s", kind, error);
                if (schemaObj.isDataSchemaMissing()) {
                    this.jobStatus.addOrUpdateRecordStatus(entry.getValue().keySet(), IndexingStatus.WARN, HttpStatus.SC_NOT_FOUND, errors.isEmpty() ? "schema not found" : String.format("schema not found | %s", error), String.format("schema not found | %s", debugInfo));
                } else if (!errors.isEmpty()) {
                    this.jobStatus.addOrUpdateRecordStatus(entry.getValue().keySet(), IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, error, debugInfo);
                }

                schemas.put(kind, schemaObj);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Get schema error", "An error has occurred while getting schema", e);
        }

        return schemas;
    }

    private RecordIndexerPayload getIndexerPayload(Map<String, Map<String, OperationType>> upsertRecordMap, Map<String, IndexSchema> kindSchemaMap, Records records) {
        List<Records.Entity> storageValidRecords = records.getRecords();
        List<RecordIndexerPayload.Record> indexerPayload = new ArrayList<>();
        Set<IndexSchema> schemasSet = new LinkedHashSet<>();

        for (Records.Entity storageRecord : storageValidRecords) {

            Map<String, OperationType> idOperationMap = upsertRecordMap.get(storageRecord.getKind());

            // skip if storage returned record with same id but different kind
            if (idOperationMap == null) {
                String message = String.format("storage service returned incorrect record | requested kind: %s | received kind: %s", this.jobStatus.getRecordKindById(storageRecord.getId()), storageRecord.getKind());
                this.jobStatus.addOrUpdateRecordStatus(storageRecord.getId(), IndexingStatus.SKIP, RequestStatus.STORAGE_CONFLICT, message, String.format("%s | record-id: %s", message, storageRecord.getId()));
                continue;
            }

            IndexSchema schema = kindSchemaMap.get(storageRecord.getKind());

            ArrayList<String> asIngestedCoordinatesPaths = null;
            if (this.asIngestedCoordinatesFeatureFlag.isFeatureEnabled(AS_INGESTED_COORDINATES_FEATURE_NAME)) {
                // enrich schema with AsIngestedCoordinates properties
                asIngestedCoordinatesPaths = findAsIngestedCoordinatesPaths(storageRecord.getData(), "");
                addAsIngestedCoordinatesFieldsToSchema(schema, asIngestedCoordinatesPaths);
            }
            schemasSet.add(schema);

            // skip indexing of records if data block is empty
            RecordIndexerPayload.Record document = prepareIndexerPayload(schema, storageRecord, idOperationMap, asIngestedCoordinatesPaths);
            if (document != null) {
                indexerPayload.add(document);
            }
        }

        // this should only happen if storage service returned WRONG records with kind for all the records in the messages
        if (indexerPayload.isEmpty()) {
            throw new AppException(RequestStatus.STORAGE_CONFLICT, "Indexer error", "upsert record failed, storage service returned incorrect records");
        }

        return RecordIndexerPayload.builder().records(indexerPayload).schemas(new ArrayList<>(schemasSet)).build();
    }

    private ArrayList<String> findAsIngestedCoordinatesPaths(Map<String, Object> dataMap, String path) {
        ArrayList<String> paths = new ArrayList<>();
        for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
            if (entry.getKey().equals(Constants.AS_INGESTED_COORDINATES)) {
                paths.add(path + entry.getKey());
                break;
            }
            if (entry.getValue() instanceof Map nested) {
                paths.addAll(findAsIngestedCoordinatesPaths(nested, path + entry.getKey() + "."));
            }
        }
        return paths;
    }

    private void addAsIngestedCoordinatesFieldsToSchema(IndexSchema schemaObj, ArrayList<String> asIngestedCoordinatesPaths) {
        Map<String, String> asIngestedProperties = new HashMap<>();

        Map<String, String> propertyKeyTypeMap = new HashMap<>();
        propertyKeyTypeMap.put("FirstPoint.X", "double");
        propertyKeyTypeMap.put("FirstPoint.Y", "double");
        propertyKeyTypeMap.put("FirstPoint.Z", "double");
        propertyKeyTypeMap.put(Constants.COORDINATE_REFERENCE_SYSTEM_ID, "text");
        propertyKeyTypeMap.put(VERTICAL_COORDINATE_REFERENCE_SYSTEM_ID, "text");
        propertyKeyTypeMap.put(Constants.VERTICAL_UNIT_ID, "text");

        for(String path: asIngestedCoordinatesPaths){
            for(Map.Entry<String,String> propertyKeyTypeEntry: propertyKeyTypeMap.entrySet()) {
                String pathPropertyKey = path + "." + propertyKeyTypeEntry.getKey();
                String propertyValue = propertyKeyTypeEntry.getValue();
                asIngestedProperties.put(pathPropertyKey, propertyValue);
            }
        }
        schemaObj.getDataSchema().putAll(asIngestedProperties);
    }

    private RecordIndexerPayload.Record prepareIndexerPayload(IndexSchema schemaObj, Records.Entity storageRecord, Map<String, OperationType> idToOperationMap, ArrayList<String> asIngestedCoordinatesPaths) {

        RecordIndexerPayload.Record document = null;

        try {
            Map<String, Object> storageRecordData = storageRecord.getData();
            document = new RecordIndexerPayload.Record();
            if (storageRecordData == null || storageRecordData.isEmpty()) {
                String message = "empty or null data block found in the storage record";
                this.jobStatus.addOrUpdateRecordStatus(storageRecord.getId(), IndexingStatus.WARN, HttpStatus.SC_NOT_FOUND, message, String.format(
                    RECORD_ID_LOG, storageRecord.getId(), message));
            } else if (schemaObj.isDataSchemaMissing()) {
                document.setSchemaMissing(true);
            } else {
                Map<String, Object> dataMap = this.storageIndexerPayloadMapper.mapDataPayload(asIngestedCoordinatesPaths, schemaObj, storageRecordData, storageRecord.getId());
                if (dataMap.isEmpty()) {
                    document.setMappingMismatch(true);
                    String message = String.format("complete schema mismatch: none of the data attribute can be mapped | data: %s", storageRecordData);
                    this.jobStatus.addOrUpdateRecordStatus(storageRecord.getId(), IndexingStatus.WARN, HttpStatus.SC_NOT_FOUND, message, String.format(
                        RECORD_ID_LOG, storageRecord.getId(), message));
                }

                if(this.augmenterSetting.isEnabled()) {
                    try {
                        if (augmenterConfigurationService.isConfigurationEnabled(storageRecord.getKind())) {
                            AugmenterConfiguration augmenterConfiguration = augmenterConfigurationService.getConfiguration(storageRecord.getKind());
                            if (augmenterConfiguration != null) {
                                // Merge extended properties
                                dataMap = mergeDataFromPropertyConfiguration(storageRecord.getId(), dataMap, augmenterConfiguration);
                            }
                            // We cache the dataMap in case the update of this object will trigger update of the related objects.
                            augmenterConfigurationService.cacheDataRecord(storageRecord.getId(), storageRecord.getKind(), dataMap);
                        }
                        else if (isIndexPropertyPathConfigurationKind(storageRecord.getKind())) {
                            // We cache the dataMap that will be used to update the schema mapping of the related kinds
                            augmenterConfigurationService.cacheDataRecord(storageRecord.getId(), storageRecord.getKind(), dataMap);
                        }
                    }
                    catch(Exception ex) {
                        String message = String.format("Augmenter error: %s", ex.getMessage());
                        this.jobStatus.addOrUpdateRecordStatus(storageRecord.getId(), IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, message);
                        jaxRsDpsLog.error(String.format("Augmenter: Failed to merge extended properties of the record with id: '%s' and kind: '%s'", storageRecord.getId(), storageRecord.getKind()), ex);
                    }
                }

                document.setData(dataMap);
            }
        } catch (AppException e) {
            this.jobStatus.addOrUpdateRecordStatus(storageRecord.getId(), IndexingStatus.FAIL, HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            jaxRsDpsLog.warning(String.format(RECORD_ID_LOG, storageRecord.getId(), e.getMessage()), e);
        } catch (Exception e) {
            this.jobStatus.addOrUpdateRecordStatus(storageRecord.getId(), IndexingStatus.FAIL, HttpStatus.SC_INTERNAL_SERVER_ERROR, String.format("error parsing records against schema, error-message: %s", e.getMessage()));
            jaxRsDpsLog.error(String.format("record-id: %s | error parsing records against schema, error-message: %s", storageRecord.getId(), e.getMessage()), e);
        }

        try {
            // index individual parts of kind
            String[] kindParts = storageRecord.getKind().split(":");
            String authority = kindParts[0];
            String source = kindParts[1];
            String type = kindParts[2];
            String[] versionParts = kindParts[3].split("\\.");
            document.setKind(storageRecord.getKind());
            document.setNamespace(authority + ":" + source);
            document.setAuthority(authority);
            document.setSource(source);
            document.setType(type);
            document.setId(storageRecord.getId());
            document.setVersion(storageRecord.getVersion());
            document.setAcl(storageRecord.getAcl());
            document.setLegal(storageRecord.getLegal());
            if (storageRecord.getTags() == null) {
                storageRecord.setTags(new HashMap<>());
            }
            storageRecord.getTags().put(IndexerServiceImpl.NORMALIZATION_KIND_TAG_ATTRIBUTE_NAME, String.format("%s:%s:%s:%s", authority, source, type, versionParts[0]));
            document.setTags(storageRecord.getTags());
            document.setCreateUser(storageRecord.getCreateUser());
            document.setCreateTime(storageRecord.getCreateTime());
            if (!Strings.isNullOrEmpty(storageRecord.getModifyUser())) {
                document.setModifyUser(storageRecord.getModifyUser());
            }
            if (!Strings.isNullOrEmpty(storageRecord.getModifyTime())) {
                document.setModifyTime(storageRecord.getModifyTime());
            }
            RecordStatus recordStatus = this.jobStatus.getJobStatusByRecordId(storageRecord.getId());
            if (recordStatus.getIndexProgress().getStatusCode() == 0) {
                recordStatus.getIndexProgress().setStatusCode(HttpStatus.SC_OK);
            }
            document.setIndexProgress(recordStatus.getIndexProgress());
            if (storageRecord.getAncestry() != null) document.setAncestry(storageRecord.getAncestry());
            document.setOperationType(idToOperationMap.get(storageRecord.getId()));
        } catch (Exception e) {
            this.jobStatus.addOrUpdateRecordStatus(storageRecord.getId(), IndexingStatus.FAIL, HttpStatus.SC_INTERNAL_SERVER_ERROR, String.format("error parsing meta data, error-message: %s", e.getMessage()));
            jaxRsDpsLog.error(String.format("record-id: %s | error parsing meta data, error-message: %s", storageRecord.getId(), e.getMessage()), e);
        }
        return document;
    }

    private Map<String, Object> mergeDataFromPropertyConfiguration(String objectId, Map<String, Object> originalDataMap, AugmenterConfiguration augmenterConfiguration) {
        Map<String, Object> extendedDataMap = augmenterConfigurationService.getExtendedProperties(objectId, originalDataMap, augmenterConfiguration);
        if (!extendedDataMap.isEmpty()) {
            originalDataMap.putAll(extendedDataMap);
        }

        return originalDataMap;
    }

    private List<String> processElasticMappingAndUpsertRecords(RecordIndexerPayload recordIndexerPayload)
        throws Exception {
        if (recordIndexerPayload.getSchemas() == null || recordIndexerPayload.getSchemas().isEmpty()) {
            return new LinkedList<>();
        }

        ElasticsearchClient restClient = requestScopedClient.getClient();
        
        // process the schema
        this.cacheOrCreateElasticMapping(recordIndexerPayload, restClient);

        // process the records
        List<RecordIndexerPayload.Record> records = recordIndexerPayload.getRecords();
        BulkRequestResult bulkRequestResult = this.upsertRecords(records, restClient);
        List<String> failedRecordIds = bulkRequestResult.getFailureRecordIds();

        processRetryUpsertRecords(restClient, records, bulkRequestResult, failedRecordIds);

        return failedRecordIds;
    }

    private void processRetryUpsertRecords(ElasticsearchClient restClient, List<Record> records,
        BulkRequestResult bulkRequestResult, List<String> failedRecordIds) {
        List<String> retryUpsertRecordIds = bulkRequestResult.getRetryUpsertRecordIds();
        if (!retryUpsertRecordIds.isEmpty()) {
            List<Record> retryUpsertRecords = records.stream()
                .filter(retryRecord -> retryUpsertRecordIds.contains(retryRecord.getId()))
                .toList();
            retryUpsertRecords.forEach(retryRecord -> {
                retryRecord.setData(Collections.emptyMap());
                retryRecord.setTags(Collections.emptyMap());
            });
            bulkRequestResult = upsertRecords(retryUpsertRecords, restClient);
            failedRecordIds.addAll(bulkRequestResult.getFailureRecordIds());
        }
    }

    private void cacheOrCreateElasticMapping(RecordIndexerPayload recordIndexerPayload, ElasticsearchClient restClient) throws Exception {
        List<IndexSchema> schemas = recordIndexerPayload.getSchemas();

        for (IndexSchema schema : schemas) {
            String index = this.elasticIndexNameResolver.getIndexNameFromKind(schema.getKind());

            // check if index exist and sync meta attribute schema if required
            if (this.indicesService.isIndexReady(restClient, index)) {
                try {
                    this.mappingService.syncMetaAttributeIndexMappingIfRequired(restClient, schema);
                } catch (ElasticsearchMappingException e) {
                    List<Record> schemaRecords = recordIndexerPayload.getRecords()
                        .stream()
                        .filter(schemaRecord -> Objects.equals(schemaRecord.getKind(), schema.getKind()))
                        .toList();
                    for (Record schemaRecord : schemaRecords) {
                        this.jobStatus.addOrUpdateRecordStatus(schemaRecord.getId(), IndexingStatus.FAIL, e.getStatus(), String.format("Error reconciling index mapping with kind schema from schema-service: %s", e.getMessage()));
                        schemaRecord.setData(Collections.emptyMap());
                    }
                }
                continue;
            }

            // create index
            Map<String, Object> mapping = this.mappingService.getIndexMappingFromRecordSchema(schema);
            if (!this.indicesService.createIndex(restClient, index, null, mapping)) {
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ELASTIC_ERROR, "Error creating index.", String.format("Failed to get confirmation from elastic server for index: %s", index));
            }
        }
    }

    private BulkRequestResult upsertRecords(List<RecordIndexerPayload.Record> records, ElasticsearchClient client) throws AppException {
        if (records == null || records.isEmpty()) return new BulkRequestResult(Collections.emptyList(), Collections.emptyList());

        BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
        bulkRequestBuilder.timeout(BULK_REQUEST_TIMEOUT);

        for (RecordIndexerPayload.Record payloadRecord : records) {
            if ((payloadRecord.getData() == null || payloadRecord.getData().isEmpty()) && !payloadRecord.skippedDataIndexing()) {
                // it will come here when schema is missing
                jaxRsDpsLog.warning(String.format("data not found for record: %s", payloadRecord));
            }

            Map<String, Object> sourceMap = getSourceMap(payloadRecord);
            String index = this.elasticIndexNameResolver.getIndexNameFromKind(payloadRecord.getKind());

            // For index to be indexed we are using id that record has (osdu version).
            // If the id (which is of type string) is not set, then Elasticsearch will automatically generate it.
            String indexId;
            if (xcollaborationHolder.isFeatureEnabledAndHeaderExists()){
                indexId = CollaborationContextUtil
                    .composeIdWithNamespace(payloadRecord.getId(), xcollaborationHolder.getCollaborationContext());
            }else {
                indexId = payloadRecord.getId();
            }

            IndexOperation<Map<String, Object>> indexOperation = new IndexOperation.Builder<Map<String, Object>>()
                .index(index)
                .id(indexId)
                .document(sourceMap)
                .build();

            bulkRequestBuilder.operations(new BulkOperation.Builder().index(indexOperation).build());
        }

        return processBulkRequest(client, bulkRequestBuilder.build());
    }

    private List<String> processDeleteRecords(Map<String, List<String>> deleteRecordMap){
        BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
        bulkRequestBuilder.timeout(BULK_REQUEST_TIMEOUT);
        for (Map.Entry<String, List<String>> deleteRecord : deleteRecordMap.entrySet()) {

            String index = this.elasticIndexNameResolver.getIndexNameFromKind(deleteRecord.getKey());

            for (String id : deleteRecord.getValue()) {
                DeleteOperation deleteOperation = DeleteOperation.of(builder -> builder.index(index).id(id));
                bulkRequestBuilder.operations(new BulkOperation.Builder().delete(deleteOperation).build());
            }
        }

        ElasticsearchClient restClient = requestScopedClient.getClient();
        return processBulkRequest(restClient, bulkRequestBuilder.build()).getFailureRecordIds();
    }

    private BulkRequestResult processBulkRequest(ElasticsearchClient client, BulkRequest bulkRequest) throws AppException {

        if (bulkRequest.operations().isEmpty()) {
            return new BulkRequestResult(Collections.emptyList(), Collections.emptyList());
        }

        List<String> failureRecordIds = new LinkedList<>();
        List<String> retryUpsertRecordIds = new LinkedList<>();
        int failedRequestStatus = 500;
        Exception failedRequestCause = null;

        try {
            long startTime = System.currentTimeMillis();
            BulkResponse bulkResponse = client.bulk(bulkRequest);
            long stopTime = System.currentTimeMillis();

            // log failed bulk requests
            ArrayList<String> bulkFailures = new ArrayList<>();
            int succeededResponses = 0;
            int failedResponses = 0;
            for (BulkResponseItem bulkItemResponse : bulkResponse.items()) {
                if (bulkItemResponse.error() != null) {
                    String failureMessage = String.format("elasticsearch bulk service status: %s | id: %s | message: %s",
                        bulkItemResponse.status(),
                        bulkItemResponse.id(),
                        buildErrorReason(bulkItemResponse.error()));
                    bulkFailures.add(failureMessage);
                    this.jobStatus.addOrUpdateRecordStatus(bulkItemResponse.id(), IndexingStatus.FAIL, bulkItemResponse.status(), buildErrorReason(bulkItemResponse.error()));

                    if (bulkItemResponse.status() == HttpStatus.SC_BAD_REQUEST && isParsingException(bulkItemResponse.error())) {
                        retryUpsertRecordIds.add(bulkItemResponse.id());
                    } else if (canIndexerRetry(bulkItemResponse)) {
                        failureRecordIds.add(bulkItemResponse.id());

                        if (failedRequestCause == null) {
                            failedRequestCause = new Exception(bulkItemResponse.error().reason());
                            failedRequestStatus = bulkItemResponse.status();
                        }
                    }

                    failedResponses++;
                } else {
                    succeededResponses++;

                    String elasticSearchNativeId = bulkItemResponse.id();

                    if (xcollaborationHolder.isFeatureEnabledAndHeaderExists()) {
                        // need to strip x-collaboration header from Bulkresponse because
                        // addOrUpdateRecordStatus() will do comparison if id of the project is the same as _id of Elasticsearch.
                        // In case they are not the same then addOrUpdateRecordStatus() will create new RecordStatus
                        // with operationType == null. Because of that null later log writer will fail with NPE.
                        elasticSearchNativeId = xcollaborationHolder.removeXcollaborationValue(elasticSearchNativeId);
                    }

                    this.jobStatus.addOrUpdateRecordStatus(elasticSearchNativeId, IndexingStatus.SUCCESS, HttpStatus.SC_OK, "Indexed Successfully");
                }
            }
            if (!bulkFailures.isEmpty()) {
                this.jaxRsDpsLog.warning(bulkFailures);
            }

            jaxRsDpsLog.info(String.format("records in elasticsearch service bulk request: %s | successful: %s | failed: %s | time taken for bulk request: %d milliseconds",
                bulkRequest.operations().size(), succeededResponses, failedResponses, stopTime - startTime));

            // retry entire message if all records are failing
            if (bulkRequest.operations().size() == failureRecordIds.size()) {
                throw new AppException(
                    failedRequestStatus,
                    ELASTIC_ERROR,
                    failedRequestCause == null ? "Unknown error" : failedRequestCause.getMessage(),
                    failedRequestCause);
            }
        } catch (IOException e) {
            // throw explicit 504 for IOException
            throw new AppException(HttpStatus.SC_GATEWAY_TIMEOUT, ELASTIC_ERROR, "Request cannot be completed in specified time.", e);
        } catch (ElasticsearchException e) {
            throw new AppException(e.status(), ELASTIC_ERROR, e.getMessage(), e);
        }catch (AppException e){
            throw e;
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ELASTIC_ERROR, e.getMessage(), e);
        }
        return new BulkRequestResult(failureRecordIds, retryUpsertRecordIds);
    }

    private Map<String, Object> getSourceMap(RecordIndexerPayload.Record payloadRecord) {

        Map<String, Object> indexerPayload = new HashMap<>();

        // get the key and get the corresponding object from the individualRecord object
        if (payloadRecord.getData() != null) {
            Map<String, Object> data = new HashMap<>();
            for (Map.Entry<String, Object> entry : payloadRecord.getData().entrySet()) {
                data.put(entry.getKey(), entry.getValue());
            }
            indexerPayload.put(Constants.DATA, data);
        }

        indexerPayload.put(RecordMetaAttribute.ID.getValue(), payloadRecord.getId());
        indexerPayload.put(RecordMetaAttribute.KIND.getValue(), payloadRecord.getKind());
        indexerPayload.put(RecordMetaAttribute.AUTHORITY.getValue(), payloadRecord.getAuthority());
        indexerPayload.put(RecordMetaAttribute.SOURCE.getValue(), payloadRecord.getSource());
        indexerPayload.put(RecordMetaAttribute.NAMESPACE.getValue(), payloadRecord.getNamespace());
        indexerPayload.put(RecordMetaAttribute.TYPE.getValue(), payloadRecord.getType());
        indexerPayload.put(RecordMetaAttribute.VERSION.getValue(), payloadRecord.getVersion());
        indexerPayload.put(RecordMetaAttribute.ACL.getValue(), payloadRecord.getAcl());
        indexerPayload.put(RecordMetaAttribute.TAGS.getValue(), payloadRecord.getTags());
        indexerPayload.put(RecordMetaAttribute.X_ACL.getValue(), Acl.flattenAcl(payloadRecord.getAcl()));
        indexerPayload.put(RecordMetaAttribute.LEGAL.getValue(), payloadRecord.getLegal());
        indexerPayload.put(RecordMetaAttribute.INDEX_STATUS.getValue(), payloadRecord.getIndexProgress());
        if (payloadRecord.getAncestry() != null) {
            indexerPayload.put(RecordMetaAttribute.ANCESTRY.getValue(), payloadRecord.getAncestry());
        }
        indexerPayload.put(RecordMetaAttribute.CREATE_USER.getValue(), payloadRecord.getCreateUser());
        indexerPayload.put(RecordMetaAttribute.CREATE_TIME.getValue(), payloadRecord.getCreateTime());
        if (!Strings.isNullOrEmpty(payloadRecord.getModifyUser())) {
            indexerPayload.put(RecordMetaAttribute.MODIFY_USER.getValue(), payloadRecord.getModifyUser());
        }
        if (!Strings.isNullOrEmpty(payloadRecord.getModifyTime())) {
            indexerPayload.put(RecordMetaAttribute.MODIFY_TIME.getValue(), payloadRecord.getModifyTime());
        }
        if (xcollaborationHolder.isFeatureEnabledAndHeaderExists()) {
            indexerPayload.computeIfAbsent(XcollaborationHolder.X_COLLABORATION,
                k -> xcollaborationHolder.getCollaborationContext().orElseThrow().getId());
        }
        return indexerPayload;
    }

    private boolean canIndexerRetry(BulkResponseItem bulkItemResponse) {
        if (RETRY_ELASTIC_EXCEPTION.contains(bulkItemResponse.status())) {
            return true;
        }

        return (bulkItemResponse.operationType() == co.elastic.clients.elasticsearch.core.bulk.OperationType.Create ||
            bulkItemResponse.operationType() == co.elastic.clients.elasticsearch.core.bulk.OperationType.Update) &&
            bulkItemResponse.status() == HttpStatus.SC_NOT_FOUND;
    }

    private void retryAndEnqueueFailedRecords(List<RecordInfo> recordInfos, List<String> failuresRecordIds, RecordChangedMessages message) throws IOException {

        jaxRsDpsLog.info(String.format("queuing bulk failed records back to task-queue for retry | count: %s | records: %s", failuresRecordIds.size(), failuresRecordIds));
        List<RecordInfo> retryRecordInfos = new LinkedList<>();
        for (String recordId : failuresRecordIds) {
            for (RecordInfo origMessage : recordInfos) {
                if (origMessage.getId().equalsIgnoreCase(recordId)) {
                    retryRecordInfos.add(origMessage);
                }
            }
        }

        RecordChangedMessages newMessage = RecordChangedMessages.builder()
                .messageId(message.getMessageId())
                .publishTime(message.getPublishTime())
                .data(this.gson.toJson(retryRecordInfos))
                .attributes(message.getAttributes()).build();

        String payLoad = this.gson.toJson(newMessage);
        this.indexerQueueTaskBuilder.createWorkerTask(payLoad, this.headers);
    }

    private void updateAuditLog() {
        logAuditEvents(OperationType.create, this.auditLogger::indexCreateRecordSuccess, this.auditLogger::indexCreateRecordPartialSuccess, this.auditLogger::indexCreateRecordFail);
        logAuditEvents(OperationType.update, this.auditLogger::indexUpdateRecordSuccess, this.auditLogger::indexUpdateRecordPartialSuccess, this.auditLogger::indexUpdateRecordFail);
        logAuditEvents(OperationType.purge, this.auditLogger::indexPurgeRecordSuccess, null, this.auditLogger::indexPurgeRecordFail);
        logAuditEvents(OperationType.delete, this.auditLogger::indexDeleteRecordSuccess, null, this.auditLogger::indexDeleteRecordFail);
    }

    private void logAuditEvents(OperationType operationType, Consumer<List<String>> successEvent, Consumer<List<String>> partialSuccessEvent, Consumer<List<String>> failedEvent) {
        List<RecordStatus> succeededRecords = this.jobStatus.getRecordStatuses(IndexingStatus.SUCCESS, operationType);
        if (!succeededRecords.isEmpty()) {
            successEvent.accept(succeededRecords.stream().map(RecordStatus::succeededAuditLogMessage).collect(Collectors.toList()));
        }
        List<RecordStatus> partiallySuccessfulRecords = this.jobStatus.getRecordStatuses(IndexingStatus.WARN, operationType);
        if (!partiallySuccessfulRecords.isEmpty() && partialSuccessEvent != null) {
            partialSuccessEvent.accept(partiallySuccessfulRecords.stream().map(RecordStatus::partiallySucceededAuditLogMessage).collect(Collectors.toList()));
        }
        List<RecordStatus> skippedRecords = this.jobStatus.getRecordStatuses(IndexingStatus.SKIP, operationType);
        List<RecordStatus> failedRecords = this.jobStatus.getRecordStatuses(IndexingStatus.FAIL, operationType);
        failedRecords.addAll(skippedRecords);
        if (!failedRecords.isEmpty()) {
            failedEvent.accept(failedRecords.stream().map(RecordStatus::failedAuditLogMessage).collect(Collectors.toList()));
        }
    }

    private boolean isIndexPropertyPathConfigurationKind(String kind) {
        return INDEX_PROPERTY_PATH_CONFIGURATION_KIND_WITH_MAJOR.equals(PropertyUtil.getKindWithMajor(kind));
    }

}
