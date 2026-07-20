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

package org.opengroup.osdu.indexer.util.geo.decimator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.opengroup.osdu.indexer.model.GeoJsonObject;
import org.opengroup.osdu.indexer.model.geojson.*;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@RunWith(SpringRunner.class)
public class GeoShapeDecimatorTest {
    private final Gson gson = new Gson();

    @InjectMocks
    GeoShapeDecimator sut;

    @Mock
    private GeometryDecimator decimator;


    private final Answer<Boolean> answer = invocation -> {
        GeometryCollection geometryCollection = invocation.getArgument(0);
        if(geometryCollection == null || geometryCollection.getGeometries() == null)
            return false;

        boolean decimated = false;
        for(GeoJsonObject geoJsonObject: geometryCollection.getGeometries()) {
            decimated |=  ( geoJsonObject instanceof MultiLineString ||
                            geoJsonObject instanceof LineString ||
                            geoJsonObject instanceof MultiPolygon ||
                            geoJsonObject instanceof Polygon);
        }
        return decimated;
    };

    @Before
    public void setup() {
        doAnswer(answer).when(decimator).decimate(any());
    }

    @Test
    public void should_decimate_polyline() throws JsonProcessingException {
        Map<String, Object> shapeObj = getShapeObj("geometrycollection_linestring.json");
        DecimatedResult result = sut.decimateShapeObj(shapeObj);
        assertTrue(result.isDecimated);
    }

    @Test
    public void should_decimate_multipolyline() throws JsonProcessingException {
        Map<String, Object> shapeObj = getShapeObj("geometrycollection_multilinestring.json");
        DecimatedResult result = sut.decimateShapeObj(shapeObj);
        assertTrue(result.isDecimated);
    }

    @Test
    public void should_decimate_polygon() throws JsonProcessingException {
        Map<String, Object> shapeObj = getShapeObj("geometrycollection_polygon.json");
        DecimatedResult result = sut.decimateShapeObj(shapeObj);
        assertTrue(result.isDecimated);
    }

    @Test
    public void should_decimate_multipolygon() throws JsonProcessingException {
        Map<String, Object> shapeObj = getShapeObj("geometrycollection_multipolygon.json");
        DecimatedResult result = sut.decimateShapeObj(shapeObj);
        assertTrue(result.isDecimated);
    }

    @Test
    public void should_not_decimate_point() throws JsonProcessingException {
        Map<String, Object> shapeObj = getShapeObj("geometrycollection_point.json");
        DecimatedResult result = sut.decimateShapeObj(shapeObj);
        assertFalse(result.isDecimated);
    }

    @Test
    public void should_not_decimate_multipoint() throws JsonProcessingException {
        Map<String, Object> shapeObj = getShapeObj("geometrycollection_multipoint.json");
        DecimatedResult result = sut.decimateShapeObj(shapeObj);
        assertFalse(result.isDecimated);
    }

    private Map<String, Object>  getShapeObj(String file) {
        String shapeJson = getGeoShapeFromFile(file);
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> shapeObj = gson.fromJson(shapeJson, type);
        return shapeObj;
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
