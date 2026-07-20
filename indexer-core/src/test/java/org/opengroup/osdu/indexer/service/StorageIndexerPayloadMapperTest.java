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

package org.opengroup.osdu.indexer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.IndexSchema;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.indexer.cache.partitionsafe.FeatureFlagCache;
import org.opengroup.osdu.indexer.cache.partitionsafe.VirtualPropertiesSchemaCache;
import org.opengroup.osdu.indexer.schema.converter.config.SchemaConverterPropertiesConfig;
import org.opengroup.osdu.indexer.schema.converter.exeption.SchemaProcessingException;
import org.opengroup.osdu.indexer.schema.converter.tags.SchemaRoot;
import org.opengroup.osdu.indexer.schema.converter.tags.VirtualProperties;
import org.opengroup.osdu.indexer.service.mock.RequestInfoMock;
import org.opengroup.osdu.indexer.service.mock.ServiceAccountJwtClientMock;
import org.opengroup.osdu.indexer.service.mock.VirtualPropertiesSchemaCacheMock;
import org.opengroup.osdu.indexer.util.geo.decimator.DouglasPeuckerReducer;
import org.opengroup.osdu.indexer.util.geo.decimator.GeoShapeDecimator;
import org.opengroup.osdu.indexer.util.geo.decimator.GeometryDecimator;
import org.opengroup.osdu.indexer.util.geo.extractor.PointExtractor;
import org.opengroup.osdu.indexer.util.parser.*;
import org.opengroup.osdu.indexer.util.BooleanFeatureFlagClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.MAP_BOOL2STRING_FEATURE_NAME;
import static org.opengroup.osdu.indexer.model.Constants.AS_INGESTED_COORDINATES_FEATURE_NAME;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {StorageIndexerPayloadMapper.class, AttributeParsingServiceImpl.class, NumberParser.class,
        BooleanParser.class, DateTimeParser.class, GeoShapeParser.class, DouglasPeuckerReducer.class, GeoShapeDecimator.class,
        GeometryDecimator.class, PointExtractor.class, GeometryConversionService.class, FeatureFlagCache.class,
        DpsHeaders.class, JobStatus.class, SchemaConverterPropertiesConfig.class, JaxRsDpsLog.class,
        ServiceAccountJwtClientMock.class, VirtualPropertiesSchemaCacheMock.class, VirtualPropertiesSchemaCache.class, RequestInfoMock.class,
        IFeatureFlag.class, StringParser.class, BooleanFeatureFlagClient.class}
)
public class StorageIndexerPayloadMapperTest {

    public static final String FIRST_OBJECT_INNER_PROPERTY = "FirstObjectInnerProperty";
    public static final String SECOND_OBJECT_INNER_PROPERTY = "SecondObjectInnerProperty";
    public static final String FIRST_OBJECT_TEST_VALUE = "first-object-test-value";
    public static final String SECOND_OBJECT_TEST_VALUE = "second-object-test-value";
    public static final String OBJECT_PROPERTY = "ObjectProperty";
    public static final String NESTED_PROPERTY = "NestedProperty";
    public static final String EMPTY_NESTED_PROPERTY = "EmptyNestedProperty";
    public static final String FIRST_NESTED_INNER_PROPERTY = "FirstNestedInnerProperty";
    public static final String SECOND_NESTED_INNER_PROPERTY = "SecondNestedInnerProperty";
    public static final String FIRST_NESTED_VALUE = "first-nested-value";
    public static final String SECOND_NESTED_VALUE = "second-nested-value";
    public static final String FLATTENED_PROPERTY = "FlattenedProperty";
    public static final String FIRST_FLATTENED_INNER_PROPERTY = "FirstFlattenedInnerProperty";
    public static final String SECOND_FLATTENED_INNER_PROPERTY = "SecondFlattenedInnerProperty";
    public static final String FIRST_FLATTENED_TEST_VALUE = "first-flattened-test-value";
    public static final String SECOND_FLATTENED_TEST_VALUE = "second-flattened-test-value";
    public static final String RECORD_TEST_ID = "test-id";

    private static final ArrayList<String> emptyAsIngestedCoordinatesPaths = new ArrayList<>();

    private static IndexSchema indexSchema;
    private static Map<String, Object> storageRecordData;
    private Gson gson = new Gson();

    @Autowired
    private StorageIndexerPayloadMapper payloadMapper;

    @Autowired
    private VirtualPropertiesSchemaCache virtualPropertiesSchemaCache;

    @MockBean
    protected IFeatureFlag featureFlagChecker;

    @MockBean
    protected BooleanFeatureFlagClient partitionFlagChecker;

    @BeforeClass
    public static void setUp() {
        HashMap<String, Object> dataMap = new HashMap<>();
        dataMap.put("TextProperty", "text");
        dataMap.put("TextArrayProperty", "text_array");
        dataMap.put("DoubleProperty", "double");
        dataMap.put(OBJECT_PROPERTY, "object");
        dataMap.put(FLATTENED_PROPERTY, "flattened");
        dataMap.put(NESTED_PROPERTY, ImmutableMap.of(
                Constants.TYPE, "nested",
                Constants.PROPERTIES, ImmutableMap.of(
                        FIRST_NESTED_INNER_PROPERTY, "text",
                        SECOND_NESTED_INNER_PROPERTY, "double")
        ));
        dataMap.put(EMPTY_NESTED_PROPERTY, ImmutableMap.of(
            Constants.TYPE, "nested",
            Constants.PROPERTIES, ImmutableMap.of(
                FIRST_NESTED_INNER_PROPERTY, "text",
                SECOND_NESTED_INNER_PROPERTY, "double")
        ));
        dataMap.put("DateProperty", "date");
        indexSchema = IndexSchema.builder().kind("kind").type(Constants.TYPE).dataSchema(dataMap).build();

        storageRecordData = new HashMap<>();
        storageRecordData.put("TextProperty", "Testing");
        storageRecordData.put("TextArrayProperty", Arrays.asList("test", "test-value"));
        storageRecordData.put("DoubleProperty", "0.1");

        storageRecordData.put(OBJECT_PROPERTY, Arrays.asList(
                ImmutableMap.of(FIRST_OBJECT_INNER_PROPERTY, FIRST_OBJECT_TEST_VALUE),
                ImmutableMap.of(SECOND_OBJECT_INNER_PROPERTY, SECOND_OBJECT_TEST_VALUE)
        ));

        storageRecordData.put(FLATTENED_PROPERTY, Arrays.asList(
                ImmutableMap.of(FIRST_FLATTENED_INNER_PROPERTY, FIRST_FLATTENED_TEST_VALUE),
                ImmutableMap.of(SECOND_FLATTENED_INNER_PROPERTY, SECOND_FLATTENED_TEST_VALUE)
        ));

        storageRecordData.put(NESTED_PROPERTY, Arrays.asList(
                ImmutableMap.of(FIRST_NESTED_INNER_PROPERTY, FIRST_NESTED_VALUE, SECOND_NESTED_INNER_PROPERTY, "0.1"),
                ImmutableMap.of(FIRST_NESTED_INNER_PROPERTY, SECOND_NESTED_VALUE, SECOND_NESTED_INNER_PROPERTY, "0.2"),
                null
        ));

        List<Object> emptyList = new ArrayList<>();
        emptyList.add(null);
        storageRecordData.put(EMPTY_NESTED_PROPERTY, emptyList);
        storageRecordData.put("DateProperty", "2021-03-02T00:17:20.640Z");
    }

    @Test
    public void mapDataPayloadTestNested() {
        Map<String, Object> stringObjectMap = payloadMapper.mapDataPayload(emptyAsIngestedCoordinatesPaths, indexSchema, storageRecordData,
                RECORD_TEST_ID);
        Object nestedProperty = stringObjectMap.get(NESTED_PROPERTY);

        assertTrue(nestedProperty instanceof List);
        List<Map<String, Object>> nestedProperty1 = (List<Map<String, Object>>) nestedProperty;
        Object firstNestedInnerProperty = nestedProperty1.get(0).get(FIRST_NESTED_INNER_PROPERTY);
        assertEquals(FIRST_NESTED_VALUE, firstNestedInnerProperty);
        Object secondNestedInnerProperty = nestedProperty1.get(0).get(SECOND_NESTED_INNER_PROPERTY);
        assertEquals(0.1, secondNestedInnerProperty);
        Object firstNestedInnerProperty1 = nestedProperty1.get(1).get(FIRST_NESTED_INNER_PROPERTY);
        assertEquals(SECOND_NESTED_VALUE, firstNestedInnerProperty1);
        Object secondNestedInnerProperty1 = nestedProperty1.get(1).get(SECOND_NESTED_INNER_PROPERTY);
        assertEquals(0.2, secondNestedInnerProperty1);
    }

    @Test
    public void mapDataPayloadTestFlattened() {
        Map<String, Object> stringObjectMap = payloadMapper.mapDataPayload(emptyAsIngestedCoordinatesPaths, indexSchema, storageRecordData,
                RECORD_TEST_ID);
        Object objectProperty = stringObjectMap.get(FLATTENED_PROPERTY);

        assertTrue(objectProperty instanceof List);
        List<Map<String, Object>> objectProperties = (List<Map<String, Object>>) objectProperty;
        Object firstInnerProperty = objectProperties.get(0).get(FIRST_FLATTENED_INNER_PROPERTY);
        assertEquals(FIRST_FLATTENED_TEST_VALUE, firstInnerProperty);
        Object secondInnerProperty = objectProperties.get(1).get(SECOND_FLATTENED_INNER_PROPERTY);
        assertEquals(SECOND_FLATTENED_TEST_VALUE, secondInnerProperty);
    }

    @Test
    public void mapDataPayloadTestEmptyNested() {
        Map<String, Object> stringObjectMap = payloadMapper.mapDataPayload(emptyAsIngestedCoordinatesPaths, indexSchema, storageRecordData,
            RECORD_TEST_ID);
        Object objectProperty = stringObjectMap.get(EMPTY_NESTED_PROPERTY);

        assertTrue(objectProperty instanceof List);
        List<Map<String, Object>> objectProperties = (List<Map<String, Object>>) objectProperty;
        assertTrue(objectProperties.isEmpty());
    }

    @Test
    public void mapDataPayloadTestObject() {
        Map<String, Object> stringObjectMap = payloadMapper.mapDataPayload(emptyAsIngestedCoordinatesPaths, indexSchema, storageRecordData,
                RECORD_TEST_ID);
        Object objectProperty = stringObjectMap.get(OBJECT_PROPERTY);

        assertTrue(objectProperty instanceof List);
        List<Map<String, Object>> objectProperties = (List<Map<String, Object>>) objectProperty;
        Object firstInnerProperty = objectProperties.get(0).get(FIRST_OBJECT_INNER_PROPERTY);
        assertEquals(FIRST_OBJECT_TEST_VALUE, firstInnerProperty);
        Object secondInnerProperty = objectProperties.get(1).get(SECOND_OBJECT_INNER_PROPERTY);
        assertEquals(SECOND_OBJECT_TEST_VALUE, secondInnerProperty);
    }

    @Test
    public void mapDataPayloadTestVirtualProperties() {
        final String kind = "osdu:wks:master-data--Wellbore:1.0.0";
        String schema = readResourceFile("/converter/index-virtual-properties/virtual-properties-schema.json");
        SchemaRoot schemaRoot = parserJsonString(schema);
        virtualPropertiesSchemaCache.put(kind, schemaRoot.getVirtualProperties());

        Map<String, Object> storageRecordData = new HashMap<>();
        storageRecordData = loadObject("/converter/index-virtual-properties/storageRecordData.json", storageRecordData.getClass());
        assertFalse(storageRecordData.containsKey("VirtualProperties.DefaultLocation.Wgs84Coordinates"));
        assertFalse(storageRecordData.containsKey("VirtualProperties.DefaultName"));

        IndexSchema indexSchema = loadObject("/converter/index-virtual-properties/storageSchema.json", IndexSchema.class);
        Map<String, Object> dataCollectorMap = payloadMapper.mapDataPayload(emptyAsIngestedCoordinatesPaths, indexSchema, storageRecordData, RECORD_TEST_ID);
        assertTrue(dataCollectorMap.containsKey("VirtualProperties.DefaultLocation.Wgs84Coordinates"));
        assertNotNull(dataCollectorMap.get("VirtualProperties.DefaultLocation.Wgs84Coordinates"));
        assertTrue(dataCollectorMap.containsKey("VirtualProperties.DefaultName"));
        assertNotNull(dataCollectorMap.get("VirtualProperties.DefaultName"));
    }

    @Test
    public void mapDataPayloadTestVirtualPropertiesWithoutMatchedProperties() {
        final String kind = "osdu:wks:master-data--Wellbore:1.0.0";
        String schema = readResourceFile("/converter/index-virtual-properties/virtual-properties-schema.json");
        SchemaRoot schemaRoot = parserJsonString(schema);
        virtualPropertiesSchemaCache.put(kind, schemaRoot.getVirtualProperties());

        Map<String, Object> storageRecordData = new HashMap<>();
        // The mapped properties do not exist in the storageRecordData
        storageRecordData = loadObject("/converter/index-virtual-properties/unmatched-payload-storageRecordData.json", storageRecordData.getClass());
        assertFalse(storageRecordData.containsKey("VirtualProperties.DefaultLocation.Wgs84Coordinates"));
        assertFalse(storageRecordData.containsKey("VirtualProperties.DefaultName"));

        IndexSchema indexSchema = loadObject("/converter/index-virtual-properties/storageSchema.json", IndexSchema.class);
        Map<String, Object> dataCollectorMap = payloadMapper.mapDataPayload(emptyAsIngestedCoordinatesPaths, indexSchema, storageRecordData, RECORD_TEST_ID);
        assertFalse(dataCollectorMap.containsKey("VirtualProperties.DefaultLocation.Wgs84Coordinates"));
        assertTrue(dataCollectorMap.containsKey("VirtualProperties.DefaultName"));
        assertNull(dataCollectorMap.get("VirtualProperties.DefaultName"));
    }

    @Test
    public void geoshape_decimation_is_executed_with_virtual_spatial_location() {
        final String kind = "osdu:wks:master-data--SeismicAcquisitionSurvey:1.0.0";
        final String record_id = "opendes:master-data--SeismicAcquisitionSurvey:WD86-BO_WD86-PR1228-FS-11";
        VirtualProperties virtualProperties = loadObject("/converter/index-virtual-properties/virtual-properties.json", VirtualProperties.class);;
        virtualPropertiesSchemaCache.put(kind, virtualProperties);

        IndexSchema indexSchema = loadObject("/converter/index-virtual-properties/survey_storage_schema.json", IndexSchema.class);
        Map<String, Object> storageRecordData = new HashMap<>();
        storageRecordData = loadObject("/converter/index-virtual-properties/survey_storage_data.json", storageRecordData.getClass());

        Map<String, Object> dataCollectorMap = payloadMapper.mapDataPayload(emptyAsIngestedCoordinatesPaths, indexSchema, storageRecordData, record_id);
        assertTrue(dataCollectorMap.containsKey("VirtualProperties.DefaultLocation.Wgs84Coordinates"));
        assertTrue(dataCollectorMap.containsKey("SpatialLocation.Wgs84Coordinates"));

        List<Object> defaultLocationCoordinates =getCoordinates("VirtualProperties.DefaultLocation.Wgs84Coordinates", dataCollectorMap);
        List<Object> spatialLocationCoordinates =getCoordinates("SpatialLocation.Wgs84Coordinates", dataCollectorMap);

        assertEquals(11, defaultLocationCoordinates.size());
        assertEquals(11, spatialLocationCoordinates.size());
    }

    @Test
    public void geoshape_decimation_is_executed_without_virtual_spatial_location() {
        virtualPropertiesSchemaCache.clearAll();
        final String record_id = "opendes:master-data--SeismicAcquisitionSurvey:WD86-BO_WD86-PR1228-FS-11";

        IndexSchema indexSchema = loadObject("/converter/index-virtual-properties/survey_storage_schema.json", IndexSchema.class);
        Map<String, Object> storageRecordData = new HashMap<>();
        storageRecordData = loadObject("/converter/index-virtual-properties/survey_storage_data.json", storageRecordData.getClass());

        Map<String, Object> dataCollectorMap = payloadMapper.mapDataPayload(emptyAsIngestedCoordinatesPaths, indexSchema, storageRecordData, record_id);
        assertFalse(dataCollectorMap.containsKey("VirtualProperties.DefaultLocation.Wgs84Coordinates"));
        assertTrue(dataCollectorMap.containsKey("SpatialLocation.Wgs84Coordinates"));

        List<Object> spatialLocationCoordinates =getCoordinates("SpatialLocation.Wgs84Coordinates", dataCollectorMap);

        assertEquals(11, spatialLocationCoordinates.size());
    }

    private List<Object> getCoordinates(String key, Map<String, Object> dataCollectorMap) {
        Map<String, Object> spatialLocation =(Map<String, Object>)dataCollectorMap.get(key);
        List<Object> geometries =(List<Object>)spatialLocation.get("geometries");
        Map<String, Object> firstGeometry = (Map<String, Object>)geometries.get(0);
        List<Object> coordinates =  (List<Object>)firstGeometry.get("coordinates");
        return coordinates;
    }

    @Test
    public void mapDataPayloadTestAsIngestedCoordinates() {
        when(this.featureFlagChecker.isFeatureEnabled(AS_INGESTED_COORDINATES_FEATURE_NAME)).thenReturn(true);

        ArrayList<String> asIngestedCoordinatesPaths = new ArrayList<>(Arrays.asList("SpatialLocation.AsIngestedCoordinates"));
        Map<String, Object> storageRecordData = new HashMap<>();

        storageRecordData = loadObject("/converter/index-as-ingested-coordinates/wellStorageRecordData.json", storageRecordData.getClass());

        IndexSchema indexSchema = loadObject("/converter/index-as-ingested-coordinates/wellStorageSchema.json", IndexSchema.class);
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.FirstPoint.X", "long");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.FirstPoint.Y", "long");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.FirstPoint.Z", "long");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.CoordinateReferenceSystemID", "text");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.VerticalCoordinateReferenceSystemID", "text");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.VerticalUnitID", "text");

        Map<String, Object> dataCollectorMap = payloadMapper.mapDataPayload(asIngestedCoordinatesPaths, indexSchema, storageRecordData, RECORD_TEST_ID);

        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.FirstPoint.X"));
        assertEquals(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.FirstPoint.X"), 30.0);
        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.FirstPoint.Y"));
        assertEquals(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.FirstPoint.Y"), 10.0);
        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.FirstPoint.Z"));
        assertEquals(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.FirstPoint.Z"), 60.0);

        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.CoordinateReferenceSystemID"));
        assertNotNull(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.CoordinateReferenceSystemID"));

        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.VerticalCoordinateReferenceSystemID"));
        assertNotNull(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.VerticalCoordinateReferenceSystemID"));

        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.VerticalUnitID"));
        assertNotNull(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.VerticalUnitID"));
    }

    @Test
    public void mapDataPayloadTestAsIngestedCoordinatesGeographicBottomHoleLocationAndSpatialLocation() {
        when(this.featureFlagChecker.isFeatureEnabled(AS_INGESTED_COORDINATES_FEATURE_NAME)).thenReturn(true);

        ArrayList<String> asIngestedCoordinatesPaths = new ArrayList<>(Arrays.asList("GeographicBottomHoleLocation.AsIngestedCoordinates", "SpatialLocation.AsIngestedCoordinates"));
        Map<String, Object> storageRecordData = new HashMap<>();
        storageRecordData = loadObject("/converter/index-virtual-properties/storageRecordData.json", storageRecordData.getClass());

        IndexSchema indexSchema = loadObject("/converter/index-virtual-properties/storageSchema.json", IndexSchema.class);
        indexSchema.getDataSchema().put("GeographicBottomHoleLocation.AsIngestedCoordinates.FirstPoint.X", "long");
        indexSchema.getDataSchema().put("GeographicBottomHoleLocation.AsIngestedCoordinates.FirstPoint.Y", "long");
        indexSchema.getDataSchema().put("GeographicBottomHoleLocation.AsIngestedCoordinates.FirstPoint.Z", "long");
        indexSchema.getDataSchema().put("GeographicBottomHoleLocation.AsIngestedCoordinates.CoordinateReferenceSystemID", "text");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.FirstPoint.X", "long");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.FirstPoint.Y", "long");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.FirstPoint.Z", "long");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.CoordinateReferenceSystemID", "text");

        Map<String, Object> dataCollectorMap = payloadMapper.mapDataPayload(asIngestedCoordinatesPaths, indexSchema, storageRecordData, RECORD_TEST_ID);

        assertTrue(dataCollectorMap.containsKey("GeographicBottomHoleLocation.AsIngestedCoordinates.FirstPoint.X"));
        assertEquals(dataCollectorMap.get("GeographicBottomHoleLocation.AsIngestedCoordinates.FirstPoint.X"), 2504888.13869565);
        assertTrue(dataCollectorMap.containsKey("GeographicBottomHoleLocation.AsIngestedCoordinates.FirstPoint.Y"));
        assertEquals(dataCollectorMap.get("GeographicBottomHoleLocation.AsIngestedCoordinates.FirstPoint.Y"), -3525752.63921785);
        assertTrue(dataCollectorMap.containsKey("GeographicBottomHoleLocation.AsIngestedCoordinates.FirstPoint.Z"));
        assertEquals(dataCollectorMap.get("GeographicBottomHoleLocation.AsIngestedCoordinates.FirstPoint.Z"), 13.0);

        assertTrue(dataCollectorMap.containsKey("GeographicBottomHoleLocation.AsIngestedCoordinates.CoordinateReferenceSystemID"));
        assertNotNull(dataCollectorMap.get("GeographicBottomHoleLocation.AsIngestedCoordinates.CoordinateReferenceSystemID"));

        assertFalse(dataCollectorMap.containsKey("GeographicBottomHoleLocation.AsIngestedCoordinates.VerticalCoordinateReferenceSystemID"));
        assertNull(dataCollectorMap.get("GeographicBottomHoleLocation.AsIngestedCoordinates.VerticalCoordinateReferenceSystemID"));

        assertFalse(dataCollectorMap.containsKey("GeographicBottomHoleLocation.AsIngestedCoordinates.VerticalUnitID"));
        assertNull(dataCollectorMap.get("GeographicBottomHoleLocation.AsIngestedCoordinates.VerticalUnitID"));

        assertTrue(dataCollectorMap.containsKey("GeographicBottomHoleLocation.AsIngestedCoordinates.persistableReferenceCrs"));
        assertNotNull(dataCollectorMap.get("GeographicBottomHoleLocation.AsIngestedCoordinates.persistableReferenceCrs"));
        assertTrue(dataCollectorMap.containsKey("GeographicBottomHoleLocation.AsIngestedCoordinates.persistableReferenceUnitZ"));
        assertNotNull(dataCollectorMap.get("GeographicBottomHoleLocation.AsIngestedCoordinates.persistableReferenceUnitZ"));

        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.FirstPoint.X"));
        assertEquals(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.FirstPoint.X"), 2504888.13869565);
        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.FirstPoint.Y"));
        assertEquals(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.FirstPoint.Y"), -3525752.63921785);
        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.FirstPoint.Z"));
        assertEquals(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.FirstPoint.Z"), 13.0);

        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.CoordinateReferenceSystemID"));
        assertNotNull(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.CoordinateReferenceSystemID"));

        assertFalse(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.VerticalCoordinateReferenceSystemID"));
        assertNull(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.VerticalCoordinateReferenceSystemID"));

        assertFalse(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.VerticalUnitID"));
        assertNull(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.VerticalUnitID"));

        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.persistableReferenceCrs"));
        assertNotNull(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.persistableReferenceCrs"));
        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.persistableReferenceUnitZ"));
        assertNotNull(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.persistableReferenceUnitZ"));
    }

    @Test
    public void mapDataPayloadTestAsIngestedCoordinatesWithEmptyZCoordinate() {
        when(this.featureFlagChecker.isFeatureEnabled(AS_INGESTED_COORDINATES_FEATURE_NAME)).thenReturn(true);

        ArrayList<String> asIngestedCoordinatesPaths = new ArrayList<>(Arrays.asList("SpatialLocation.AsIngestedCoordinates"));
        Map<String, Object> storageRecordData = new HashMap<>();
        storageRecordData = loadObject("/converter/index-as-ingested-coordinates/wellStorageRecordData-v2.json", storageRecordData.getClass());

        IndexSchema indexSchema = loadObject("/converter/index-as-ingested-coordinates/wellStorageSchema.json", IndexSchema.class);
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.FirstPoint.X", "double");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.FirstPoint.Y", "double");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.FirstPoint.Z", "double");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.CoordinateReferenceSystemID", "text");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.VerticalCoordinateReferenceSystemID", "text");
        indexSchema.getDataSchema().put("SpatialLocation.AsIngestedCoordinates.VerticalUnitID", "text");

        Map<String, Object> dataCollectorMap = payloadMapper.mapDataPayload(asIngestedCoordinatesPaths, indexSchema, storageRecordData, RECORD_TEST_ID);

        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.FirstPoint.X"));
        assertEquals(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.FirstPoint.X"), 30.0);
        assertTrue(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.FirstPoint.Y"));
        assertEquals(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.FirstPoint.Y"), 10.0);
        assertFalse(dataCollectorMap.containsKey("SpatialLocation.AsIngestedCoordinates.FirstPoint.Z"));
        assertNull(dataCollectorMap.get("SpatialLocation.AsIngestedCoordinates.FirstPoint.Z"));
    }

    @Test
    public void mapDataPayloadTestAutoconversionsBooleanConversionOn() {
        when(this.featureFlagChecker.isFeatureEnabled(MAP_BOOL2STRING_FEATURE_NAME)).thenReturn(true);
        Map<String, Object> storageRecordLocalData = loadObject("/converter/index-autoconversions/wellStorageRecordData.json", storageRecordData.getClass());
        IndexSchema indexLocalSchema = loadObject("/converter/index-autoconversions/wellStorageSchema.json", IndexSchema.class);
        Map<String, Object> dataCollectorMap = payloadMapper.mapDataPayload(emptyAsIngestedCoordinatesPaths, indexLocalSchema, storageRecordLocalData, RECORD_TEST_ID);

        assertTrue(dataCollectorMap.containsKey("SomeKeywordField"));
        assertEquals(dataCollectorMap.get("SomeKeywordField"), "42.0");
        assertTrue(dataCollectorMap.containsKey("SomeTextField"));
        assertEquals(dataCollectorMap.get("SomeTextField"), "43.0");
        assertTrue(dataCollectorMap.containsKey("SomeOtherTextField"));
        assertEquals(dataCollectorMap.get("SomeOtherTextField"), "true");
        assertTrue(dataCollectorMap.containsKey("SomeKeywordArrayField"));
        assertEquals(((String[]) dataCollectorMap.get("SomeKeywordArrayField"))[0], "44.0");
        assertTrue(dataCollectorMap.containsKey("SomeTextArrayField"));
        assertEquals(((String[]) dataCollectorMap.get("SomeTextArrayField"))[0], "46.1");
        assertTrue(dataCollectorMap.containsKey("SomeIntegerArrayField"));
        assertEquals(((Integer[]) dataCollectorMap.get("SomeIntegerArrayField"))[0].intValue(), 48);
        assertTrue(dataCollectorMap.containsKey("SomeIntegerField"));
        assertEquals(dataCollectorMap.get("SomeIntegerField"), 50);
        assertTrue(dataCollectorMap.containsKey("SomeLongArrayField"));
        assertEquals(((Long[]) dataCollectorMap.get("SomeLongArrayField"))[0].longValue(), 510000000000001L);
        assertTrue(dataCollectorMap.containsKey("SomeLongField"));
        assertEquals(dataCollectorMap.get("SomeLongField"), 530000000000001L);
        assertTrue(dataCollectorMap.containsKey("SomeFloatArrayField"));
        assertEquals(((Float[]) dataCollectorMap.get("SomeFloatArrayField"))[0].floatValue(), 54.11111, 0.0001);
        assertTrue(dataCollectorMap.containsKey("SomeFloatField"));
        assertEquals((Float) dataCollectorMap.get("SomeFloatField"), 56.11111, 0.0001);
        assertTrue(dataCollectorMap.containsKey("SomeDoubleArrayField"));
        assertEquals(((Double[]) dataCollectorMap.get("SomeDoubleArrayField"))[0].doubleValue(), 56.11111111111111D, 0.00000000001D);
        assertTrue(dataCollectorMap.containsKey("SomeDoubleField"));
        assertEquals((Double) dataCollectorMap.get("SomeDoubleField"), 58.11111111111111D, 0.00000000001D);
        assertTrue(dataCollectorMap.containsKey("SomeBooleanArrayField"));
        assertEquals(((Boolean[]) dataCollectorMap.get("SomeBooleanArrayField"))[0], true);
        assertTrue(dataCollectorMap.containsKey("SomeBooleanField"));
        assertEquals((Boolean) dataCollectorMap.get("SomeBooleanField"), false);
        assertTrue(dataCollectorMap.containsKey("SomeDateArrayField"));
        assertEquals(((String[]) dataCollectorMap.get("SomeDateArrayField"))[0], "2024-01-01T00:00:00+0000");
        assertTrue(dataCollectorMap.containsKey("SomeDateField"));
        assertEquals(dataCollectorMap.get("SomeDateField"), "2024-01-03T00:00:00+0000");
    }
    
    private <T> T loadObject(String file, Class<T> valueType) {
        String jsonString = readResourceFile(file);
        return this.gson.fromJson(jsonString, valueType);
    }

    private SchemaRoot parserJsonString(final String schemaServiceFormat) {
        try {
            ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
            return objectMapper.readValue(schemaServiceFormat, SchemaRoot.class);
        } catch (JsonProcessingException e) {
            throw new SchemaProcessingException("Failed to parse the schema");
        }
    }

    private String readResourceFile(String file) {
        try {
            return new String(Files.readAllBytes(
                    Paths.get(this.getClass().getResource(file).toURI())), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            fail("Failed to read file:" + file);
        }
        return null;
    }
}
