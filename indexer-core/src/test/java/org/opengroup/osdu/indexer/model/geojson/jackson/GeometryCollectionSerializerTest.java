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

package org.opengroup.osdu.indexer.model.geojson.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengroup.osdu.indexer.model.geojson.*;
import org.springframework.test.context.junit4.SpringRunner;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

@RunWith(SpringRunner.class)
public class GeometryCollectionSerializerTest {
    private static ObjectMapper objectMapper;

    @BeforeClass
    public static void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerSubtypes(new NamedType(GeometryCollection.class, GeoJsonConstants.GEOMETRY_COLLECTION));
        objectMapper.registerSubtypes(new NamedType(Polygon.class, GeoJsonConstants.POLYGON));
        objectMapper.registerSubtypes(new NamedType(MultiPolygon.class, GeoJsonConstants.MULTI_POLYGON));
        objectMapper.registerSubtypes(new NamedType(LineString.class, GeoJsonConstants.LINE_STRING));
        objectMapper.registerSubtypes(new NamedType(MultiLineString.class, GeoJsonConstants.MULTI_LINE_STRING));
        objectMapper.registerSubtypes(new NamedType(Point.class, GeoJsonConstants.POINT));
        objectMapper.registerSubtypes(new NamedType(MultiPoint.class, GeoJsonConstants.MULTI_POINT));
    }

    @Test
    public void deserialize_polyline() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("geometrycollection_linestring.json");
        GeometryCollection geometryCollection = objectMapper.readValue(shapeJson, GeometryCollection.class);
        Assert.assertNotNull(geometryCollection);
        Assert.assertNotNull(geometryCollection.getGeometries());
        Assert.assertTrue(geometryCollection.getGeometries().size() > 0);
        Assert.assertTrue(geometryCollection.getGeometries().get(0) instanceof LineString);
    }

    @Test
    public void deserialize_multipolyline() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("geometrycollection_multilinestring.json");
        GeometryCollection geometryCollection = objectMapper.readValue(shapeJson, GeometryCollection.class);
        Assert.assertNotNull(geometryCollection);
        Assert.assertNotNull(geometryCollection.getGeometries());
        Assert.assertTrue(geometryCollection.getGeometries().size() > 0);
        Assert.assertTrue(geometryCollection.getGeometries().get(0) instanceof MultiLineString);
    }

    @Test
    public void deserialize_polygon() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("geometrycollection_polygon.json");
        GeometryCollection geometryCollection = objectMapper.readValue(shapeJson, GeometryCollection.class);
        Assert.assertNotNull(geometryCollection);
        Assert.assertNotNull(geometryCollection.getGeometries());
        Assert.assertTrue(geometryCollection.getGeometries().size() > 0);
        Assert.assertTrue(geometryCollection.getGeometries().get(0) instanceof Polygon);
    }

    @Test
    public void deserialize_multipolygon() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("geometrycollection_multipolygon.json");
        GeometryCollection geometryCollection = objectMapper.readValue(shapeJson, GeometryCollection.class);
        Assert.assertNotNull(geometryCollection);
        Assert.assertNotNull(geometryCollection.getGeometries());
        Assert.assertTrue(geometryCollection.getGeometries().size() > 0);
        Assert.assertTrue(geometryCollection.getGeometries().get(0) instanceof MultiPolygon);
    }

    @Test
    public void deserialize_point() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("geometrycollection_point.json");
        GeometryCollection geometryCollection = objectMapper.readValue(shapeJson, GeometryCollection.class);
        Assert.assertNotNull(geometryCollection);
        Assert.assertNotNull(geometryCollection.getGeometries());
        Assert.assertTrue(geometryCollection.getGeometries().size() > 0);
        Assert.assertTrue(geometryCollection.getGeometries().get(0) instanceof Point);
    }

    @Test
    public void deserialize_multipoint() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("geometrycollection_multipoint.json");
        GeometryCollection geometryCollection = objectMapper.readValue(shapeJson, GeometryCollection.class);
        Assert.assertNotNull(geometryCollection);
        Assert.assertNotNull(geometryCollection.getGeometries());
        Assert.assertTrue(geometryCollection.getGeometries().size() > 0);
        Assert.assertTrue(geometryCollection.getGeometries().get(0) instanceof MultiPoint);
    }

    @SneakyThrows
    private String getGeoShapeFromFile(String file) {
        InputStream inStream = this.getClass().getResourceAsStream("/geo/decimator/" + file);
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
