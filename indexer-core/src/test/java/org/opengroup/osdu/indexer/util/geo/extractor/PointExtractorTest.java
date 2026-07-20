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

package org.opengroup.osdu.indexer.util.geo.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.indexer.model.geojson.*;
import org.opengroup.osdu.indexer.util.geo.extractor.PointExtractor;
import org.springframework.test.context.junit4.SpringRunner;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

@RunWith(SpringRunner.class)
public class PointExtractorTest {
    private static ObjectMapper objectMapper;

    @InjectMocks
    private PointExtractor extractor;

    @Mock
    private JaxRsDpsLog log;

    @BeforeClass
    public static void setup() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void extractFirstPointFromPointTest() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("valid_point.json");
        Map<String, Object> geometryCollection = objectMapper.readValue(shapeJson, Map.class);
        
        ArrayList<Double> point = extractor.extractFirstPointFromFeatureCollection(geometryCollection);

        Assert.assertEquals(point.size(), 2);
        Assert.assertEquals(point.get(0), -105.01621, 0.0001);
        Assert.assertEquals(point.get(1), 39.57422, 0.0001);
    }

    @Test
    public void extractFirstPointFromLineStringTest() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("valid_line_string.json");
        Map<String, Object> geometryCollection = objectMapper.readValue(shapeJson, Map.class);
        
        ArrayList<Double> point = extractor.extractFirstPointFromFeatureCollection(geometryCollection);

        Assert.assertEquals(point.size(), 2);
        Assert.assertEquals(point.get(0), -101.744384, 0.0001);
        Assert.assertEquals(point.get(1), 39.32155, 0.0001);
    }

    @Test
    public void extractFirstPointFromPolygonTest() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("valid_polygon.json");
        Map<String, Object> geometryCollection = objectMapper.readValue(shapeJson, Map.class);
        
        ArrayList<Double> point = extractor.extractFirstPointFromFeatureCollection(geometryCollection);

        Assert.assertEquals(point.size(), 2);
        Assert.assertEquals(point.get(0), 100, 0.0001);
        Assert.assertEquals(point.get(1), 0, 0.0001);
    }

    @Test
    public void extractFirstPointFromMultiPointTest() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("valid_multi_point.json");
        Map<String, Object> geometryCollection = objectMapper.readValue(shapeJson, Map.class);
        
        ArrayList<Double> point = extractor.extractFirstPointFromFeatureCollection(geometryCollection);

        Assert.assertEquals(point.size(), 2);
        Assert.assertEquals(point.get(0), -105.01621, 0.0001);
        Assert.assertEquals(point.get(1), 39.57422, 0.0001);
    }

    @Test
    public void extractFirstPointFromMultiLineTest() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("valid_milti_line_string_with_z_coordinate.json");
        Map<String, Object> geometryCollection = objectMapper.readValue(shapeJson, Map.class);
        
        ArrayList<Double> point = extractor.extractFirstPointFromFeatureCollection(geometryCollection);

        Assert.assertEquals(point.size(), 3);
        Assert.assertEquals(point.get(0), -105.021443, 0.0001);
        Assert.assertEquals(point.get(1), 39.578057, 0.0001);
        Assert.assertEquals(point.get(2), 7.0, 0.0001);
    }

    @Test
    public void extractFirstPointFromMultiPolygonTest() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("valid_multi_polygon.json");
        Map<String, Object> geometryCollection = objectMapper.readValue(shapeJson, Map.class);
        
        ArrayList<Double> point = extractor.extractFirstPointFromFeatureCollection(geometryCollection);

        Assert.assertEquals(point.size(), 2);
        Assert.assertEquals(point.get(0), 107, 0.0001);
        Assert.assertEquals(point.get(1), 7, 0.0001);
    }

    @Test
    public void extractFirstPointFromGeometryCollectionTest() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("valid_geometry_collection.json");
        Map<String, Object> geometryCollection = objectMapper.readValue(shapeJson, Map.class);
        
        ArrayList<Double> point = extractor.extractFirstPointFromFeatureCollection(geometryCollection);

        Assert.assertEquals(point.size(), 2);
        Assert.assertEquals(point.get(0), -80.660805, 0.0001);
        Assert.assertEquals(point.get(1), 35.049392, 0.0001);
    }

    @Test
    public void extractFirstPointFromInvalidShapeTest() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("invalid_shape.json");
        Map<String, Object> geometryCollection = objectMapper.readValue(shapeJson, Map.class);
        
        ArrayList<Double> point = extractor.extractFirstPointFromFeatureCollection(geometryCollection);

        Assert.assertEquals(point.size(), 0);
    }

    @Test
    public void extractFirstPointFromPolygonMalformedLatitudeTest() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("invalid_polygon_malformed_latitude.json");
        Map<String, Object> geometryCollection = objectMapper.readValue(shapeJson, Map.class);
        
        ArrayList<Double> point = extractor.extractFirstPointFromFeatureCollection(geometryCollection);

        Assert.assertEquals(point.size(), 0);
    }

    @Test
    public void extractFirstPointFromPointMissingLatitudeTest() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("invalid_point_missing_latitude.json");
        Map<String, Object> geometryCollection = objectMapper.readValue(shapeJson, Map.class);
        
        ArrayList<Double> point = extractor.extractFirstPointFromFeatureCollection(geometryCollection);

        Assert.assertEquals(point.size(), 0);
    }

    @Test
    public void extractFirstPointFromInvalidFeatureCollectionTest() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("invalid_feature_collection.json");
        Map<String, Object> geometryCollection = objectMapper.readValue(shapeJson, Map.class);
        
        ArrayList<Double> point = extractor.extractFirstPointFromFeatureCollection(geometryCollection);

        Assert.assertEquals(point.size(), 0);
    }

    @SneakyThrows
    private String getGeoShapeFromFile(String file) {
        InputStream inStream = this.getClass().getResourceAsStream("/geojson/parsing/input/" + file);
        BufferedReader br = new BufferedReader(new InputStreamReader(inStream));
        StringBuilder stringBuilder = new StringBuilder();
        String sCurrentLine;
        while ((sCurrentLine = br.readLine()) != null)
        {
            stringBuilder.append(sCurrentLine).append("\n");
        }
        return stringBuilder.toString();
    }
}
