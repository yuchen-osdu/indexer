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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.beanutils.NestedNullException;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.indexer.ElasticType;
import org.opengroup.osdu.core.common.model.indexer.IndexSchema;
import org.opengroup.osdu.core.common.model.indexer.IndexingStatus;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.indexer.cache.partitionsafe.VirtualPropertiesSchemaCache;
import org.opengroup.osdu.indexer.schema.converter.config.SchemaConverterConfig;
import org.opengroup.osdu.indexer.schema.converter.tags.Priority;
import org.opengroup.osdu.indexer.schema.converter.tags.VirtualProperties;
import org.opengroup.osdu.indexer.schema.converter.tags.VirtualProperty;
import org.opengroup.osdu.indexer.util.PropertyUtil;
import org.opengroup.osdu.indexer.util.geo.decimator.DecimatedResult;
import org.opengroup.osdu.indexer.util.geo.decimator.GeoShapeDecimator;
import org.opengroup.osdu.indexer.util.geo.extractor.PointExtractor;
import org.opengroup.osdu.indexer.util.BooleanFeatureFlagClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

import static org.opengroup.osdu.indexer.model.Constants.AS_INGESTED_COORDINATES_FEATURE_NAME;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.MAP_BOOL2STRING_FEATURE_NAME;

@Component
public class StorageIndexerPayloadMapper {
    private static final String SPATIAL_LOCATION_WGS84 = "SpatialLocation.Wgs84Coordinates";
    private static final String SPATIAL_AREA_WGS84 = "SpatialArea.Wgs84Coordinates";
    private static final String PERSISTABLE_REFERENCE_VERTICAL_CRS = "persistableReferenceVerticalCrs";

    @Inject
    private JaxRsDpsLog log;
    @Inject
    private IAttributeParsingService attributeParsingService;
    @Inject
    private JobStatus jobStatus;
    @Inject
    private SchemaConverterConfig schemaConfig;
    @Inject
    private VirtualPropertiesSchemaCache virtualPropertiesSchemaCache;
    @Inject
    private GeoShapeDecimator decimator;
    @Inject
    private PointExtractor pointExtractor;

    @Autowired
    private IFeatureFlag featureFlagChecker;

    @Autowired
    private BooleanFeatureFlagClient booleanFeatureFlagClient;

    public Map<String, Object> mapDataPayload(ArrayList<String> asIngestedCoordinatesPaths, IndexSchema storageSchema, Map<String, Object> storageRecordData,
                                              String recordId) {

        Map<String, Object> dataCollectorMap = new HashMap<>();

        if (storageSchema.isDataSchemaMissing()) {
            this.log.warning(String.format("record-id: %s | schema mismatching: %s ", recordId, storageSchema.getKind()));
            return dataCollectorMap;
        }

        mapDataPayload(storageSchema.getDataSchema(), storageRecordData, recordId, dataCollectorMap);
        mapVirtualPropertiesPayload(storageSchema, recordId, dataCollectorMap);
        if (this.featureFlagChecker.isFeatureEnabled(AS_INGESTED_COORDINATES_FEATURE_NAME)) {
            mapAsIngestedCoordinatesPayload(recordId, asIngestedCoordinatesPaths, storageRecordData, dataCollectorMap);
        }

        return dataCollectorMap;
    }

    private Map<String, Object> mapDataPayload(Map<String, Object> dataSchema, Map<String, Object> storageRecordData,
                                               String recordId, Map<String, Object> dataCollectorMap) {

        // get the key and get the corresponding object from the storageRecord object
        for (Map.Entry<String, Object> entry : dataSchema.entrySet()) {
            String schemaPropertyName = entry.getKey();
            Object storageRecordValue = getPropertyValue(recordId, storageRecordData, schemaPropertyName);
            ElasticType elasticType = defineElasticType(entry.getValue());

            if (Objects.isNull(elasticType)) {
                this.jobStatus
                        .addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST,
                                String.format("record-id: %s | %s for entry %s", recordId, "Not resolvable elastic type", schemaPropertyName));
                continue;
            }

            if (schemaConfig.getProcessedArraysTypes().contains(elasticType.getValue().toLowerCase()) && Objects.nonNull(storageRecordValue)) {
                processInnerProperties(recordId, dataCollectorMap, entry.getValue(), schemaPropertyName, (List<Map>) storageRecordValue);
            }

            if (storageRecordValue == null && !nullIndexedValueSupported(elasticType)) {
                continue;
            }

            switch (elasticType) {
                case KEYWORD:
                case TEXT:
                    this.attributeParsingService.tryParseString(recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case KEYWORD_ARRAY:
                case TEXT_ARRAY:
                    this.attributeParsingService.tryParseValueArray(String.class, recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case INTEGER_ARRAY:
                    this.attributeParsingService.tryParseValueArray(Integer.class, recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case INTEGER:
                    this.attributeParsingService.tryParseInteger(recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case LONG_ARRAY:
                    this.attributeParsingService.tryParseValueArray(Long.class, recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case LONG:
                    this.attributeParsingService.tryParseLong(recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case FLOAT_ARRAY:
                    this.attributeParsingService.tryParseValueArray(Float.class, recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case FLOAT:
                    this.attributeParsingService.tryParseFloat(recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case DOUBLE_ARRAY:
                    this.attributeParsingService.tryParseValueArray(Double.class, recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case DOUBLE:
                    this.attributeParsingService.tryParseDouble(recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case BOOLEAN_ARRAY:
                    this.attributeParsingService.tryParseValueArray(Boolean.class, recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case BOOLEAN:
                    this.attributeParsingService.tryParseBoolean(recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case DATE_ARRAY:
                    this.attributeParsingService.tryParseValueArray(Date.class, recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case DATE:
                    this.attributeParsingService.tryParseDate(recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case GEO_POINT:
                    this.attributeParsingService.tryParseGeopoint(recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case GEO_SHAPE:
                    this.attributeParsingService.tryParseGeojson(recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case FLATTENED:
                    // flattened type inner properties will be added "as is" without parsing as they types not present in schema
                    this.attributeParsingService.tryParseFlattened(recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case OBJECT:
                    // object type inner properties will be added "as is" without parsing as they types not present in schema
                    this.attributeParsingService.tryParseObject(recordId, schemaPropertyName, storageRecordValue, dataCollectorMap);
                    break;
                case UNDEFINED:
                    // don't do anything for now
                    break;
            }
        }

        return dataCollectorMap;
    }

    private void processInnerProperties(String recordId, Map<String, Object> dataCollectorMap, Object schemaPropertyWithInnerProperties,
                                        String name, List<Map> storageRecordValue) {
        Map schemaPropertyMap = (Map) schemaPropertyWithInnerProperties;
        Map innerProperties = (Map) schemaPropertyMap.get(Constants.PROPERTIES);
        ArrayList<Map> innerPropertiesMappingCollector = new ArrayList<>();
        storageRecordValue.stream()
            .filter(Objects::nonNull)
            .forEach(recordData -> innerPropertiesMappingCollector.add(mapDataPayload(innerProperties, recordData, recordId, new HashMap<>())));
        dataCollectorMap.put(name, innerPropertiesMappingCollector);
    }

    private ElasticType defineElasticType(Object entryValue) {
        ElasticType elasticType = null;
        if (entryValue instanceof String) {
            elasticType = ElasticType.forValue(entryValue.toString());
        } else if (entryValue instanceof Map) {
            Map map = (Map) entryValue;
            elasticType = ElasticType.forValue(map.get(Constants.TYPE).toString());
        }
        return elasticType;
    }

    public Object getPropertyValue(String recordId, Map<String, Object> storageRecordData, String propertyKey) {

        try {
            // try getting first level property using optimized collection
            Object propertyVal = storageRecordData.get(propertyKey);
            if (propertyVal != null) return propertyVal;

            // use apache utils to get nested property
            return PropertyUtils.getProperty(storageRecordData, propertyKey);
        } catch (NestedNullException ignored) {
            // property not found in record
        } catch (NoSuchMethodException e) {
            this.log.warning(String.format("record-id: %s | error fetching property: %s | error: %s", recordId, propertyKey, e.getMessage()));
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            this.log.warning(String.format("record-id: %s | error fetching property: %s | error: %s", recordId, propertyKey, e.getMessage()));
        }
        return null;
    }

    private boolean nullIndexedValueSupported(ElasticType type) {
        return type == ElasticType.TEXT;
    }

    private void mapVirtualPropertiesPayload(IndexSchema storageSchema, String recordId, Map<String, Object> dataCollectorMap) {
        if (dataCollectorMap.isEmpty()) {
            return;
        }

        String originalGeoShapeProperty = null;
        VirtualProperties virtualProperties = this.virtualPropertiesSchemaCache.get(storageSchema.getKind());
        if(virtualProperties != null) {
            for (Map.Entry<String, VirtualProperty> entry : virtualProperties.getProperties().entrySet()) {
                if (entry.getValue().getPriorities() == null || entry.getValue().getPriorities().size() == 0) {
                    continue;
                }
                Priority priority = chooseOriginalProperty(entry.getKey(), entry.getValue().getPriorities(), dataCollectorMap);
                String virtualPropertyPath = PropertyUtil.removeDataPrefix(entry.getKey());
                String originalPropertyPath = PropertyUtil.removeDataPrefix(priority.getPath());

                // Populate the virtual property values from the chosen original property
                List<String> originalPropertyNames = dataCollectorMap.keySet().stream()
                        .filter(originalPropertyName -> PropertyUtil.isPropertyPathMatched(originalPropertyName, originalPropertyPath))
                        .collect(Collectors.toList());
                originalPropertyNames.forEach(originalPropertyName -> {
                    String virtualPropertyName = virtualPropertyPath + originalPropertyName.substring(originalPropertyPath.length());
                    dataCollectorMap.put(virtualPropertyName, dataCollectorMap.get(originalPropertyName));
                });

                if(virtualPropertyPath.equals(PropertyUtil.VIRTUAL_DEFAULT_LOCATION) &&
                        dataCollectorMap.containsKey(PropertyUtil.VIRTUAL_DEFAULT_LOCATION_WGS84_PATH)) {
                    originalGeoShapeProperty = originalPropertyPath + PropertyUtil.FIELD_WGS84_COORDINATES;
                }
            }
        }

        // No VirtualProperties.DefaultLocation.Wgs84Coordinates defined, use the default geo-shape property
        if (originalGeoShapeProperty == null)
            originalGeoShapeProperty = getDefaultGeoShapeProperty(dataCollectorMap);
        if(originalGeoShapeProperty != null) {
            try {
                decimateGeoShape(originalGeoShapeProperty, dataCollectorMap);
            } catch (JsonProcessingException ex) {
                this.log.warning(String.format("record-id: %s | error decimating geoshape | error: %s", recordId, ex.getMessage()));
            }
        }
    }

    private String getDefaultGeoShapeProperty(Map<String, Object> dataCollectorMap) {
        if(dataCollectorMap.containsKey(SPATIAL_LOCATION_WGS84))
            return SPATIAL_LOCATION_WGS84;
        if(dataCollectorMap.containsKey(SPATIAL_AREA_WGS84))
            return SPATIAL_AREA_WGS84;
        return null;
    }

    private void decimateGeoShape(String originalGeoShapeProperty, Map<String, Object> dataCollectorMap) throws JsonProcessingException {
        if(originalGeoShapeProperty == null || !dataCollectorMap.containsKey(originalGeoShapeProperty))
            return;

        Map<String, Object> shapeObj = (Map<String, Object>)dataCollectorMap.get(originalGeoShapeProperty);
        if(shapeObj == null)
            return;

        DecimatedResult result = decimator.decimateShapeObj(shapeObj);
        if(result.isDecimated()) {
            dataCollectorMap.put(originalGeoShapeProperty, result.getDecimatedShapeObj());
            if(dataCollectorMap.containsKey(PropertyUtil.VIRTUAL_DEFAULT_LOCATION_WGS84_PATH)) {
                dataCollectorMap.put(PropertyUtil.VIRTUAL_DEFAULT_LOCATION_WGS84_PATH, result.getDecimatedShapeObj());
            }
        }
        if(dataCollectorMap.containsKey(PropertyUtil.VIRTUAL_DEFAULT_LOCATION_WGS84_PATH)) {
            dataCollectorMap.put(PropertyUtil.VIRTUAL_DEFAULT_LOCATION_IS_DECIMATED_PATH, result.isDecimated());
        }
    }

    private Priority chooseOriginalProperty(String virtualPropertyPath, List<Priority> priorities, Map<String, Object> dataCollectorMap) {
        if (PropertyUtil.VIRTUAL_DEFAULT_LOCATION.equals(virtualPropertyPath) || PropertyUtil.DATA_VIRTUAL_DEFAULT_LOCATION.equals(virtualPropertyPath)) {
            // Specially handle "data.VirtualProperties.DefaultLocation" -- check the value of the field "wgs84Coordinates"
            for (Priority priority : priorities) {
                String originalPropertyPath = PropertyUtil.removeDataPrefix(priority.getPath());
                String wgs84PropertyField = originalPropertyPath + PropertyUtil.FIELD_WGS84_COORDINATES;
                if (dataCollectorMap.containsKey(wgs84PropertyField) && dataCollectorMap.get(wgs84PropertyField) != null)
                    return priority;
            }
        }

        for (Priority priority : priorities) {
            String originalPropertyPath = PropertyUtil.removeDataPrefix(priority.getPath());
            List<String> originalPropertyNames = dataCollectorMap.keySet().stream()
                    .filter(name -> PropertyUtil.isPropertyPathMatched(name, originalPropertyPath))
                    .collect(Collectors.toList());
            for (String originalPropertyName : originalPropertyNames) {
                if (dataCollectorMap.containsKey(originalPropertyName) && dataCollectorMap.get(originalPropertyName) != null)
                    return priority;
            }
        }

        // None of the original properties has value, return the default one
        return priorities.get(0);
    }

    private void mapAsIngestedCoordinatesPayload(String recordId, ArrayList<String> asIngestedCoordinatesPaths, Map<String, Object> storageRecordData, Map<String, Object> dataCollectorMap) {
        for (String path : asIngestedCoordinatesPaths) {
            mapFirstPointFromAsIngestedFeatureCollection(recordId, storageRecordData, path, dataCollectorMap);
            mapPropertyString(recordId, storageRecordData, dataCollectorMap, path + "." + Constants.PERSISTABLE_REFERENCE_CRS);
            mapPropertyString(recordId, storageRecordData, dataCollectorMap, path + "." + PERSISTABLE_REFERENCE_VERTICAL_CRS);
            mapPropertyString(recordId, storageRecordData, dataCollectorMap, path + "." + Constants.PERSISTABLE_REFERENCE_UNIT_Z);
        }
    }

    private void mapFirstPointFromAsIngestedFeatureCollection(String recordId, Map<String, Object> storageRecordData, String path, Map<String, Object> dataCollectorMap) {
        Map<String, Object> asIngestedCoordinates = (Map) getPropertyValue(recordId, storageRecordData, path);
        ArrayList<Double> firstPoint = pointExtractor.extractFirstPointFromFeatureCollection(asIngestedCoordinates);
        if (!firstPoint.isEmpty()) {
            dataCollectorMap.put(path + ".FirstPoint.X", firstPoint.get(0));
            dataCollectorMap.put(path + ".FirstPoint.Y", firstPoint.get(1));
            if (firstPoint.size() == 3) {
                dataCollectorMap.put(path + ".FirstPoint.Z", firstPoint.get(2));
            }
        } else {
            this.jobStatus
                    .addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST,
                            String.format("record-id: %s | %s", recordId, "Could not extract first point from as ingested feature collection"));
        }
    }

    private void mapPropertyString(String recordId, Map<String, Object> storageRecordData, Map<String, Object> dataCollectorMap, String propertyKey) {
        String value = (String) getPropertyValue(recordId, storageRecordData, propertyKey);
        if (value != null) {
            dataCollectorMap.put(propertyKey, value);
        }
    }

}
