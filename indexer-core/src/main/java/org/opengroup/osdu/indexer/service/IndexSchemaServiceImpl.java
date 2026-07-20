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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.RequestStatus;
import org.opengroup.osdu.core.common.model.indexer.ElasticType;
import org.opengroup.osdu.core.common.model.indexer.IndexSchema;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.search.RecordMetaAttribute;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.indexer.cache.partitionsafe.FlattenedSchemaCache;
import org.opengroup.osdu.indexer.cache.partitionsafe.SchemaCache;
import org.opengroup.osdu.indexer.cache.partitionsafe.VirtualPropertiesSchemaCache;
import org.opengroup.osdu.indexer.model.Kind;
import org.opengroup.osdu.indexer.model.indexproperty.AugmenterConfiguration;
import org.opengroup.osdu.indexer.schema.converter.exeption.SchemaProcessingException;
import org.opengroup.osdu.indexer.service.exception.ElasticsearchMappingException;
import org.opengroup.osdu.indexer.util.AugmenterSetting;
import org.opengroup.osdu.indexer.util.ElasticClientHandler;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;
import org.opengroup.osdu.indexer.util.TypeMapper;
import org.springframework.stereotype.Service;

@Service
public class IndexSchemaServiceImpl implements IndexSchemaService {

    private final Gson gson = new Gson();

    @Inject
    private JaxRsDpsLog log;
    @Inject
    private SchemaService schemaProvider;
    @Inject
    private ElasticClientHandler elasticClientHandler;
    @Inject
    private ElasticIndexNameResolver elasticIndexNameResolver;
    @Inject
    private IMappingService mappingService;
    @Inject
    private IndicesService indicesService;
    @Inject
    private SchemaCache schemaCache;
    @Inject
    private FlattenedSchemaCache flattenedSchemaCache;
    @Inject
    private VirtualPropertiesSchemaCache virtualPropertiesSchemaCache;
    @Inject
    private AugmenterConfigurationService augmenterConfigurationService;
    @Inject
    private AugmenterSetting augmenterSetting;
    @Inject
    private RequestScopedElasticsearchClient requestScopedClient;

    public void processSchemaMessages(Map<String, OperationType> schemaMsgs) throws IOException {
        ElasticsearchClient restClient = this.requestScopedClient.getClient();
        schemaMsgs.entrySet().forEach(msg -> {
            try {
                processSchemaEvents(restClient, msg);
            } catch (IOException | ElasticsearchException | URISyntaxException e) {
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "unable to process schema update", e.getMessage());
            }
        });
    }

    private void processSchemaEvents(ElasticsearchClient restClient, Map.Entry<String, OperationType> msg) throws IOException, ElasticsearchException, URISyntaxException {
        String kind = msg.getKey();
        String index = this.elasticIndexNameResolver.getIndexNameFromKind(kind);

        boolean indexExist = this.indicesService.isIndexExist(restClient, index);

        if (msg.getValue() == OperationType.create_schema) {
            this.processSchemaUpsertEvent(restClient, kind);
        } else if (msg.getValue() == OperationType.purge_schema) {
            if (indexExist) {
                // reset schema cache
                this.invalidateCache(kind);
            } else {
                // log warning
                log.warning(String.format("Kind: %s not found", kind));
            }
        }
    }

    @Override
    public void processSchemaUpsert(String kind) throws AppException {
        try {
            ElasticsearchClient restClient = this.requestScopedClient.getClient();
            processSchemaUpsertEvent(restClient, kind);
        } catch (IOException | ElasticsearchException | URISyntaxException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "unable to process schema update", e.getMessage());
        }
    }

    @Override
    public void processSchemaUpsertEvent(ElasticsearchClient restClient, String kind) throws IOException, ElasticsearchException, URISyntaxException {
        String index = this.elasticIndexNameResolver.getIndexNameFromKind(kind);
        boolean indexExist = this.indicesService.isIndexExist(restClient, index);

        // reset cache and get new schema
        this.invalidateCache(kind);
        IndexSchema schemaObj = this.getIndexerInputSchema(kind, true);
        if (schemaObj.isDataSchemaMissing()) {
            log.warning(String.format("schema not found for kind: %s", kind));
            return;
        }

        if (indexExist) {
            try {
                // merge the mapping
                this.mappingService.createMapping(restClient, schemaObj, index, true);
            } catch (ElasticsearchMappingException e) {
                // acknowledge for TaskQueue and not retry
                if (e.getStatus() == HttpStatus.SC_BAD_REQUEST) {
                    throw new AppException(RequestStatus.SCHEMA_CONFLICT, e.getMessage(), "error creating or merging index mapping");
                }
                throw e;
            }
        } else {
            // create index with mapping
            Map<String, Object> mapping = this.mappingService.getIndexMappingFromRecordSchema(schemaObj);
            this.indicesService.createIndex(restClient, index, null, mapping);
        }
    }

    @Override
    public IndexSchema getIndexerInputSchema(String kind, List<String> errors) throws AppException, UnsupportedEncodingException, URISyntaxException {
        try {
            return getIndexerInputSchema(kind, false);
        } catch (SchemaProcessingException ex) {
            log.error(ex.getMessage(), ex);
            errors.add(ex.getMessage());
        } catch (RuntimeException ex) {
            String msg = String.format("Failed to get the schema from the Schema service, kind: %s | message: %s", kind, ex.getMessage());
            log.error(msg, ex);
            errors.add(msg);
        }
        return this.getEmptySchema(kind);
    }

    @Override
    public IndexSchema getIndexerInputSchema(String kind, boolean invalidateCached) throws AppException, UnsupportedEncodingException, URISyntaxException {
        if (invalidateCached) {
            this.invalidateCache(kind);
        }

        String schema = this.schemaCache.get(kind);
        if (Strings.isNullOrEmpty(schema)) {
            // get from storage
            schema = this.getSchema(kind, "");
            if (Strings.isNullOrEmpty(schema)) {
                return this.getEmptySchema(kind);
            } else {
                return cacheAndGetFlattenedSchema(kind, schema);
            }
        } else {
            // search flattened schema in memcache
            String flattenedSchema = this.flattenedSchemaCache.get(kind);
            if (Strings.isNullOrEmpty(flattenedSchema)) {
                schema = this.getSchema(kind, "");
                return cacheAndGetFlattenedSchema(kind, schema);
            }
            return this.gson.fromJson(flattenedSchema, IndexSchema.class);
        }
    }

    private String getSchema(String kind, String accessors) throws AppException, UnsupportedEncodingException, URISyntaxException {
        if (!Strings.isNullOrEmpty(accessors)) {
            String schema = this.schemaCache.get(kind);
            if (!Strings.isNullOrEmpty(schema)) {
                return schema;
            }
        }

        // accessors used to prevent infinite loop
        if (accessors.contains(kind)) {
            return null;
        } else {
            accessors += ";" + kind;
        }
        String schema = this.schemaProvider.getSchema(kind);
        boolean augmented = false;
        if (!Strings.isNullOrEmpty(schema) && augmenterSetting.isEnabled()) {
            try {
                // Merge schema of the extended properties if needed
                AugmenterConfiguration augmenterConfiguration = augmenterConfigurationService.getConfiguration(kind);
                if (augmenterConfiguration != null) {
                    augmented = true;
                    schema = mergeSchemaFromPropertyConfiguration(schema, augmenterConfiguration, accessors);
                }
            } catch (Exception ex) {
                log.error(String.format("Augmenter: Failed to merge schema of the extended properties for kind: '%s'", kind), ex);
            }
        }
        if (!augmented) {
            // augmented schema could be incomplete because of infinite loop prevention
            cacheAndGetFlattenedSchema(kind, schema);
        }
        return schema;
    }

    private IndexSchema cacheAndGetFlattenedSchema(String kind, String schema) {
        // cache the schema
        this.schemaCache.put(kind, schema);
        // get flatten schema and cache it
        IndexSchema flatSchemaObj = normalizeSchema(schema);
        if (flatSchemaObj != null) {
            this.flattenedSchemaCache.put(kind, gson.toJson(flatSchemaObj));
        }
        return flatSchemaObj;
    }

    private String mergeSchemaFromPropertyConfiguration(String originalSchemaStr, AugmenterConfiguration augmenterConfiguration, String accessors) throws UnsupportedEncodingException, URISyntaxException {
        Map<String, Schema> relatedObjectKindSchemas = getSchemaOfRelatedObjectKinds(augmenterConfiguration, accessors);
        Schema originalSchema = gson.fromJson(originalSchemaStr, Schema.class);
        List<SchemaItem> extendedSchemaItems = augmenterConfigurationService.getExtendedSchemaItems(originalSchema, relatedObjectKindSchemas, augmenterConfiguration);
        if (!extendedSchemaItems.isEmpty()) {
            List<SchemaItem> originalSchemaItems = new ArrayList<>(Arrays.asList(originalSchema.getSchema()));
            originalSchemaItems.addAll(extendedSchemaItems);
            originalSchema.setSchema(originalSchemaItems.toArray(new SchemaItem[0]));
            return gson.toJson(originalSchema);
        } else {
            return originalSchemaStr;
        }
    }

    private Map<String, Schema> getSchemaOfRelatedObjectKinds(AugmenterConfiguration augmenterConfiguration, String accessors) throws UnsupportedEncodingException, URISyntaxException {
        List<String> relatedObjectKinds = augmenterConfiguration.getUniqueRelatedObjectKinds();
        Map<String, Schema> relatedObjectKindSchemas = new HashMap<>();
        for (String relatedObjectKind : relatedObjectKinds) {
            // The relatedObjectKind defined in property configuration can be kind having major version only
            // e.g. "RelatedObjectKind": "osdu:wks:master-data--Wellbore:1."
            String concreteRelatedObjectKind = augmenterConfigurationService.resolveConcreteKind(relatedObjectKind);
            if (Strings.isNullOrEmpty(concreteRelatedObjectKind))
                continue;

            String relatedObjectKindSchema = this.schemaCache.get(concreteRelatedObjectKind);
            if (Strings.isNullOrEmpty(relatedObjectKindSchema)) {
                relatedObjectKindSchema = this.getSchema(concreteRelatedObjectKind, accessors);
            }

            if (!Strings.isNullOrEmpty(relatedObjectKindSchema)) {
                Schema schema = gson.fromJson(relatedObjectKindSchema, Schema.class);
                relatedObjectKindSchemas.put(relatedObjectKind, schema);
            }
        }
        return relatedObjectKindSchemas;
    }

    private IndexSchema getEmptySchema(String kind) {
        Schema basicSchema = Schema.builder().kind(kind).build();
        return normalizeSchema(gson.toJson(basicSchema));
    }

    public void syncIndexMappingWithStorageSchema(String kind) throws ElasticsearchException, IOException, AppException, URISyntaxException {
        String index = this.elasticIndexNameResolver.getIndexNameFromKind(kind);
        ElasticsearchClient restClient = requestScopedClient.getClient();
        if (this.indicesService.isIndexExist(restClient, index)) {
                this.indicesService.deleteIndex(restClient, index);
                this.log.info(String.format("deleted index: %s", index));
        }
        IndexSchema schemaObj = this.getIndexerInputSchema(kind, true);
        Map<String, Object> mapping = this.mappingService.getIndexMappingFromRecordSchema(schemaObj);
        this.indicesService.createIndex(restClient, index, null, mapping);
    }

    public boolean isStorageSchemaSyncRequired(String kind, boolean forceClean) throws IOException {
        String index = this.elasticIndexNameResolver.getIndexNameFromKind(kind);
        ElasticsearchClient restClient = requestScopedClient.getClient();
        boolean indexExist = this.indicesService.isIndexExist(restClient, index);
        return !indexExist || forceClean;
    }

    @Override
    public void invalidateSchemaCache(String kind) {
        this.invalidateCache(kind);
    }

    private void invalidateCache(String kind) {
        this.schemaCache.delete(kind);
        this.flattenedSchemaCache.delete(kind);
        this.virtualPropertiesSchemaCache.delete(kind);
    }

    private IndexSchema normalizeSchema(String schemaStr) throws AppException {

        try {
            Schema schemaObj = this.gson.fromJson(schemaStr, Schema.class);

            if (schemaObj == null) return null;

            Map<String, Object> data = new HashMap<>();
            Map<String, Object> meta = new HashMap<>();

            if (schemaObj.getSchema() != null && schemaObj.getSchema().length > 0) {
                for (SchemaItem schemaItem : schemaObj.getSchema()) {
                    String dataType = schemaItem.getKind();
                    Object elasticDataType = TypeMapper.getIndexerType(dataType, ElasticType.TEXT.getValue());
                    if (schemaItem.getProperties() != null) {
                        HashMap<String, Object> propertiesMap = normalizeInnerProperties(schemaItem);
                        elasticDataType = TypeMapper.getObjectsArrayMapping(dataType, propertiesMap);
                    }
                    data.put(schemaItem.getPath(), elasticDataType);
                }
            }

            Kind kind = new Kind(schemaObj.getKind());

            // mandatory attributes
            meta.put(RecordMetaAttribute.ID.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.ID));
            meta.put(RecordMetaAttribute.NAMESPACE.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.NAMESPACE));
            meta.put(RecordMetaAttribute.VERSION.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.VERSION));
            meta.put(RecordMetaAttribute.KIND.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.KIND));
            meta.put(RecordMetaAttribute.TYPE.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.TYPE));
            meta.put(RecordMetaAttribute.ACL.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.ACL));
            meta.put(RecordMetaAttribute.X_ACL.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.X_ACL));
            meta.put(RecordMetaAttribute.TAGS.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.TAGS));
            meta.put(RecordMetaAttribute.LEGAL.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.LEGAL));
            meta.put(RecordMetaAttribute.ANCESTRY.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.ANCESTRY));
            meta.put(RecordMetaAttribute.INDEX_STATUS.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.INDEX_STATUS));
            meta.put(RecordMetaAttribute.AUTHORITY.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.AUTHORITY));
            meta.put(RecordMetaAttribute.SOURCE.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.SOURCE));
            meta.put(RecordMetaAttribute.CREATE_USER.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.CREATE_USER));
            meta.put(RecordMetaAttribute.CREATE_TIME.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.CREATE_TIME));
            meta.put(RecordMetaAttribute.MODIFY_USER.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.MODIFY_USER));
            meta.put(RecordMetaAttribute.MODIFY_TIME.getValue(), TypeMapper.getIndexerType(RecordMetaAttribute.MODIFY_TIME));

            return IndexSchema.builder().dataSchema(data).metaSchema(meta).kind(schemaObj.getKind()).type(kind.getType()).build();

        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Schema normalization error", "An error has occurred while normalizing the schema.", e);
        }
    }

    private HashMap<String, Object> normalizeInnerProperties(SchemaItem schemaItem) {
        HashMap<String, Object> propertiesMap = new HashMap<>();
        for (SchemaItem propertiesItem : schemaItem.getProperties()) {
            String propertiesItemKind = propertiesItem.getKind();
            Object propertiesElasticType = TypeMapper.getIndexerType(propertiesItemKind, ElasticType.TEXT.getValue());
            if (propertiesItem.getProperties() != null) {
                HashMap<String, Object> innerProperties = normalizeInnerProperties(propertiesItem);
                propertiesElasticType = TypeMapper.getObjectsArrayMapping(propertiesItemKind, innerProperties);
            }
            propertiesMap.put(propertiesItem.getPath(), propertiesElasticType);
        }
        return propertiesMap;
    }

}
