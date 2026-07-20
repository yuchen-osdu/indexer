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
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.lettuce.core.RedisException;
import jakarta.inject.Inject;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.indexer.IndexSchema;
import org.opengroup.osdu.core.common.model.search.RecordMetaAttribute;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.core.common.search.Preconditions;
import org.opengroup.osdu.indexer.cache.partitionsafe.IndexCache;
import org.opengroup.osdu.indexer.model.Kind;
import org.opengroup.osdu.indexer.model.XcollaborationHolder;
import org.opengroup.osdu.indexer.service.exception.ElasticsearchMappingException;
import org.opengroup.osdu.indexer.util.CustomIndexAnalyzerSetting;
import org.opengroup.osdu.indexer.util.TypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.opengroup.osdu.core.common.model.search.RecordMetaAttribute.BAG_OF_WORDS;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.BAG_OF_WORDS_FEATURE_NAME;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.KEYWORD_LOWER_FEATURE_NAME;
import static org.opengroup.osdu.indexer.model.XcollaborationHolder.X_COLLABORATION;

@Service
public class IndexerMappingServiceImpl extends MappingServiceImpl implements IMappingService {

    private static final Time REQUEST_TIMEOUT = Time.of(builder -> builder.time("1m"));
    @Inject
    private JaxRsDpsLog log;
    @Autowired
    private IndexCache indexCache;
    @Autowired
    private ElasticIndexNameResolver elasticIndexNameResolver;
    @Autowired
    private IFeatureFlag featureFlagChecker;
    @Autowired
    private XcollaborationHolder xcollaborationHolder;
    @Autowired
    private CustomIndexAnalyzerSetting customIndexAnalyzerSetting;


    /**
     * Create a new type in Elasticsearch
     *
     * @param client Elasticsearch client
     * @param index  Index name
     * @param merge  Try to merge mapping if type already exists
     * @throws IOException if cannot create mapping
     */
    public String createMapping(ElasticsearchClient client, IndexSchema schema, String index, boolean merge) throws IOException {

        Map<String, Object> mappingMap = this.getIndexMappingFromRecordSchema(schema);

        // When merging, exclude bagOfWords field to avoid analyzer conflicts on existing indices.
        // The bagOfWords field uses a completion type with an analyzer that cannot be changed on
        // existing indices. Schema-derived copy_to attributes on data fields are still included.
        if (merge) {
            Object propertiesObject = mappingMap.get(Constants.PROPERTIES);
            if (propertiesObject instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> props = (Map<String, Object>) propertiesObject;
                if (props.containsKey(BAG_OF_WORDS.getValue())) {
                    props.remove(BAG_OF_WORDS.getValue());
                    log.info("Excluded bagOfWords from mapping merge to avoid potential analyzer conflicts");
                }
            } else {
                log.warning("Expected a Map for properties but found: " + (propertiesObject != null ? propertiesObject.getClass().getName() : "null"));
            }
        }

        String mapping = new Gson().toJson(mappingMap, Map.class);
        this.createMappingWithJson(client, index, schema.getType(), mapping, merge);
        return mapping;
    }

    /*
     * Read schema mapping
     *
     * @param schema Index schema
     * @param type   Mapping type
     * @return String JSON representation of type and elastic type
     *
     * sample index mapping:
     * "properties": {
     *      all meta attributes
     *      "acl": {
     *          "properties": {
                    mapping of all roles
     *          }
     *      },
     *      "legal": {
     *          "properties": {
     *              mapping of all legal properties
     *          }
     *      }
     *      "data": {
     *          "properties": {
     *              all data-source attributes
     *           }
     *       }
     *  }
     * */
    public Map<String, Object> getIndexMappingFromRecordSchema(IndexSchema schema) {
        // entire property block
        Map<String, Object> properties = new HashMap<>();

        // meta  attribute
        Map<String, Object> metaMapping = this.getMetaMapping(schema);

        // data-source attributes
        Map<String, Object> dataMapping = this.getDataMapping(schema);
        if (!dataMapping.isEmpty()) {
            // inner properties.data.properties block
            Map<String, Object> dataProperties = new HashMap<>();
            dataProperties.put(Constants.PROPERTIES, dataMapping);

            // data & meta block
            properties.put(Constants.DATA, dataProperties);

            // Add collaborationId if feature enabled and header exists
            if (xcollaborationHolder.isFeatureEnabledAndHeaderExists()){
                properties.put(X_COLLABORATION, TypeMapper.getMetaAttributeIndexerMapping(X_COLLABORATION, null));
            }
        }
        properties.putAll(metaMapping);

        // entire document properties block
        Map<String, Object> documentMapping = new HashMap<>();
        documentMapping.put(Constants.PROPERTIES, properties);

        // don't add dynamic mapping
        documentMapping.put("dynamic", false);
        return documentMapping;
    }

    private Map<String, Object> getMetaMapping(IndexSchema schema) {
        Map<String, Object> metaMapping = new HashMap<>();
        Kind kind = new Kind(schema.getKind());

        boolean bagOfWordsEnabled = this.featureFlagChecker.isFeatureEnabled(BAG_OF_WORDS_FEATURE_NAME);
        if(bagOfWordsEnabled){
            schema.getMetaSchema().put(BAG_OF_WORDS.getValue(), null);
        }

        for (Map.Entry<String, Object> entry : schema.getMetaSchema().entrySet()) {
            if (entry.getKey().equals(RecordMetaAttribute.AUTHORITY.getValue())) {
                metaMapping.put(entry.getKey(), TypeMapper.getMetaAttributeIndexerMapping(entry.getKey(), kind.getAuthority()));
            } else if (entry.getKey().equals(RecordMetaAttribute.SOURCE.getValue())) {
                metaMapping.put(entry.getKey(), TypeMapper.getMetaAttributeIndexerMapping(entry.getKey(), kind.getSource()));
            } else {
                metaMapping.put(entry.getKey(), TypeMapper.getMetaAttributeIndexerMapping(entry.getKey(), null));
            }
        }
        return metaMapping;
    }

    private Map<String, Object> getDataMapping(IndexSchema schema) {
        Map<String, Object> dataMapping = new HashMap<>();
        boolean keywordLowerEnabled = this.featureFlagChecker.isFeatureEnabled(KEYWORD_LOWER_FEATURE_NAME);
        boolean bagOfWordsEnabled = this.featureFlagChecker.isFeatureEnabled(BAG_OF_WORDS_FEATURE_NAME);
        boolean customIndexAnalyzerEnabled = this.customIndexAnalyzerSetting.isEnabled();

        if (schema.getDataSchema() == null || schema.getDataSchema().isEmpty()) return dataMapping;

        for (Map.Entry<String, Object> entry : schema.getDataSchema().entrySet()) {
            dataMapping.put(entry.getKey(), TypeMapper.getDataAttributeIndexerMapping(entry.getValue(), keywordLowerEnabled, bagOfWordsEnabled, customIndexAnalyzerEnabled));
        }
        return dataMapping;
    }

    @Override
    public void syncMetaAttributeIndexMappingIfRequired(ElasticsearchClient restClient, IndexSchema schema) throws Exception {
        String index = this.elasticIndexNameResolver.getIndexNameFromKind(schema.getKind());

        // want to distinguish two types of project: collaboration and not-collaboration
        final String cacheKey;
        if (xcollaborationHolder.isFeatureEnabledAndHeaderExists()) {
            cacheKey = String.format("metaCollaborationAttributeMappingSynced-%s", index);
        } else {
            cacheKey = String.format("metaAttributeMappingSynced-%s", index);
        }

        try {
            Boolean mappingSynced = this.indexCache.get(cacheKey);
            if (mappingSynced != null && mappingSynced) return;
        } catch (RedisException ex) {
            //In case the format of cache changes then clean the cache
            this.indexCache.delete(cacheKey);
        }
        log.info(String.format("Syncing Index Mapping for kind %s, index %s", schema.getKind(), index));

        // retrieve a mapping for a given index
        String jsonResponse = this.getIndexMapping(restClient, index);
        Type type = new TypeToken<Map<String, Object>>() {
        }.getType();
        Map<String, Object> mappings = new Gson().fromJson(jsonResponse, type);

        if (mappings == null || mappings.isEmpty()) return;

        Map<String, Object> props = (Map<String, Object>) mappings.get("properties");

        if (props == null || props.isEmpty()) return;

        // Let's gather fields that are missingFields in mapping that came from elastic
        List<String> missingFields = getMissingFields(props);
        postProcessMissingFields(missingFields);

        if (missingFields.isEmpty()) {
            this.indexCache.put(cacheKey, true);
            return;
        }

        Map<String, Object> properties = new HashMap<>();
        Kind kind = new Kind(schema.getKind());
        for (String attribute : missingFields) {
            if (RecordMetaAttribute.AUTHORITY.getValue().equals(attribute)) {
                properties.put(attribute, TypeMapper.getMetaAttributeIndexerMapping(attribute, kind.getAuthority()));
                log.info(String.format("Syncing Index Mapping for kind %s, Authority %s, index %s", schema.getKind(), kind.getAuthority(), index));
            } else if (RecordMetaAttribute.SOURCE.getValue().equals(attribute)) {
                properties.put(attribute, TypeMapper.getMetaAttributeIndexerMapping(attribute, kind.getSource()));
            } else {
                properties.put(attribute, TypeMapper.getMetaAttributeIndexerMapping(attribute, null));
            }
        }
        boolean bagOfWordsEnabled = this.featureFlagChecker.isFeatureEnabled(BAG_OF_WORDS_FEATURE_NAME);
        if (bagOfWordsEnabled) {
            // sync data-source attributes, but only text/keyword fields that use copy_to for bag-of-words
            // numeric, date, and other types don't benefit from bag-of-words and shouldn't be synced
            // to avoid mapping conflicts when field types change (e.g., long -> double)
            Map<String, Object> filteredDataSchema = filterTextAndKeywordFields(schema.getDataSchema());
            if (!filteredDataSchema.isEmpty()) {
                IndexSchema filteredSchema = IndexSchema.builder()
                    .kind(schema.getKind())
                    .type(schema.getType())
                    .dataSchema(filteredDataSchema)
                    .metaSchema(new HashMap<>())
                    .build();

                Map<String, Object> dataMapping = this.getDataMapping(filteredSchema);
                if (!dataMapping.isEmpty()) {
                    // inner properties.data.properties block
                    Map<String, Object> dataProperties = new HashMap<>();
                    dataProperties.put(Constants.PROPERTIES, dataMapping);

                    // data & meta block
                    properties.put(Constants.DATA, dataProperties);
                }
            }
        }

        if (properties.isEmpty()) {
            this.indexCache.put(cacheKey, true);
            return;
        }

        Map<String, Object> documentMapping = new HashMap<>();
        documentMapping.put(Constants.PROPERTIES, properties);

        String mapping = new Gson().toJson(documentMapping, Map.class);
        this.createMappingWithJson(restClient, index, "_doc", mapping, true);
        log.info(String.format("Creating Mapping for index %s, %s", index, mapping));

        this.indexCache.put(cacheKey, true);
    }

    private void postProcessMissingFields(List<String> missing) {
        if (xcollaborationHolder.isFeatureEnabled() && xcollaborationHolder.getCollaborationContext().isEmpty()) {
            removeXcollaborationField(missing);
        }
    }

    private void removeXcollaborationField(List<String> missing) {
        missing.remove(X_COLLABORATION);
    }

    /**
     * Let's gather fields, that are missing in mapping that came from elastic,
     * but present in hardcoded TypeMapper.class.
     * Using TypeMapper.class as source of list for fields that should be presented in mapping on ES side.
     */
    private List<String> getMissingFields(Map<String, Object> props) {
      return TypeMapper.getMetaAttributesKeys().stream()
            .filter(attribute -> !props.containsKey(attribute))
            .collect(Collectors.toList());
    }

    /**
     * Create a new type in Elasticsearch
     *
     * @param client  Elasticsearch client
     * @param index   Index name
     * @param type    Type name
     * @param mapping Mapping if any, null if no specific mapping
     * @param merge   Try to merge mapping if type already exists
     * @throws IOException if cannot create index mapping with input json
     */
    private void createMappingWithJson(ElasticsearchClient client, String index, String type, String mapping, boolean merge)
            throws IOException {

        boolean mappingExist = isTypeExist(client, index, type);
        if (merge || !mappingExist) {
            createTypeWithMappingInElasticsearch(client, index, mapping);
        }
    }

    /**
     * Check if a type (mapping) already exists
     *
     * @param client Elasticsearch client
     * @param index  Index name
     * @param type   Type (mapping) name
     * @return true if type (mapping) already exists
     * @throws IOException in case Elasticsearch responded with a status code that indicated an error
     */
    public boolean isTypeExist(ElasticsearchClient client, String index, String type) throws IOException {
        GetMappingRequest request = new GetMappingRequest.Builder()
            .index(index)
            .build();

        GetMappingResponse response = client.indices().getMapping(request);

        Map<String, IndexMappingRecord> mappings = response.result();

        if (mappings.containsKey(index)) {
            IndexMappingRecord mappingRecord = mappings.get(index);
            return mappingRecord.mappings().properties().containsKey(type);
        }

        return false;
    }

    /**
     * Create a new type in Elasticsearch
     *
     * @param client  Elasticsearch client
     * @param index   Index name
     * @param mapping Mapping if any, null if no specific mapping
     * @throws IOException if mapping cannot be created
     */
    private Boolean createTypeWithMappingInElasticsearch(ElasticsearchClient client, String index, String mapping) throws IOException {
        Preconditions.checkNotNull(client, "client cannot be null");
        Preconditions.checkNotNull(index, "index cannot be null");

        try {
            if (mapping != null) {
                PutMappingRequest request = PutMappingRequest.of(b -> b
                    .index(index)
                    .timeout(REQUEST_TIMEOUT)
                    .withJson(new StringReader(mapping))
                );
                PutMappingResponse response = client.indices().putMapping(request);
                return response.acknowledged();
            }
        } catch (ElasticsearchException e) {
            throw new ElasticsearchMappingException("Failed to create mapping: " + e.getMessage(), e.status());
        }
        return false;
    }

    /**
     * Filter data schema to only include text and keyword fields.
     * Only text and keyword fields benefit from bag-of-words copy_to functionality.
     * Numeric, date, and other types don't use copy_to, so syncing them is unnecessary
     * and can cause mapping conflicts when field types change.
     *
     * @param dataSchema The data schema map from IndexSchema
     * @return Filtered map containing only text and keyword type fields
     */
    private Map<String, Object> filterTextAndKeywordFields(Map<String, Object> dataSchema) {
        if (dataSchema == null || dataSchema.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> filtered = new HashMap<>();
        for (Map.Entry<String, Object> entry : dataSchema.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            String valueStr = value.toString().toLowerCase();
            // Include text, keyword, and their array variants
            // These are the only types that get copy_to in TypeMapper.getDataAttributeIndexerMapping
            if (valueStr.equals("text") ||
                valueStr.equals("keyword") ||
                valueStr.equals("text_array") ||
                valueStr.equals("keyword_array")) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }

        return filtered;
    }
}
