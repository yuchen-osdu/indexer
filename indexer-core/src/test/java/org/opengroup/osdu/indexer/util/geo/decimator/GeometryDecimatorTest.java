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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.opengroup.osdu.indexer.model.geojson.*;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.validation.constraints.NotNull;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

@RunWith(SpringRunner.class)
public class GeometryDecimatorTest {
    private final Gson gson = new Gson();
    private ObjectMapper deserializerMapper;

    @InjectMocks
    private GeometryDecimator decimator;

    @Mock
    private DouglasPeuckerReducer reducer;

    private final Answer<List<Integer>> answer = invocation -> {
        List<Position> coordinates = invocation.getArgument(0);
        List<Integer> indexes = new ArrayList<>();
        if(coordinates.size() < 6) {
            for(int i = 0; i < coordinates.size(); i++) {
                indexes.add(0);
            }
        }
        else {
            indexes.add(0);
            indexes.add(coordinates.size() -1);
        }
        return indexes;
    };


    @Before
    public void setup() {
        deserializerMapper = createDeserializerMapper();
        doAnswer(answer).when(reducer).getPointIndexesToKeep(anyList(), anyDouble(), anyDouble());
    }

    @Test
    public void should_decimate_polyline() throws JsonProcessingException {
        GeometryCollection geometryCollection = getGeometryCollection("geometrycollection_linestring.json");
        boolean decimated = decimator.decimate(geometryCollection);
        assertTrue(decimated);
    }

    @Test
    public void should_decimate_multipolyline() throws JsonProcessingException {
        GeometryCollection geometryCollection = getGeometryCollection("geometrycollection_multilinestring.json");
        boolean decimated = decimator.decimate(geometryCollection);
        assertTrue(decimated);
    }

    @Test
    public void should_decimate_polygon() throws JsonProcessingException {
        GeometryCollection geometryCollection = getGeometryCollection("geometrycollection_polygon.json");
        boolean decimated = decimator.decimate(geometryCollection);
        assertTrue(decimated);
    }

    @Test
    public void should_decimate_multipolygon() throws JsonProcessingException {
        GeometryCollection geometryCollection = getGeometryCollection("geometrycollection_multipolygon.json");
        boolean decimated = decimator.decimate(geometryCollection);
        assertTrue(decimated);
    }

    @Test
    public void should_not_decimate_point() throws JsonProcessingException {
        GeometryCollection geometryCollection = getGeometryCollection("geometrycollection_point.json");
        boolean decimated = decimator.decimate(geometryCollection);
        assertFalse(decimated);
    }

    @Test
    public void should_not_decimate_multipoint() throws JsonProcessingException {
        GeometryCollection geometryCollection = getGeometryCollection("geometrycollection_multipoint.json");
        boolean decimated = decimator.decimate(geometryCollection);
        assertFalse(decimated);
    }

    @Test
    public void should_not_decimate_small_geometry() throws JsonProcessingException {
        GeometryCollection geometryCollection = getGeometryCollection("geometrycollection_small_multilinestring.json");
        boolean decimated = decimator.decimate(geometryCollection);
        assertFalse(decimated);
    }

    private GeometryCollection getGeometryCollection(String file) throws JsonProcessingException  {
        String shapeJson = getGeoShapeFromFile(file);
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> shapeObj = gson.fromJson(shapeJson, type);
        GeometryCollection geometryCollection = deserializerMapper.readValue(deserializerMapper.writeValueAsString(shapeObj), GeometryCollection.class);
        return geometryCollection;
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

    @NotNull
    private static ObjectMapper createDeserializerMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerSubtypes(new NamedType(GeometryCollection.class, "geometrycollection"));
        mapper.registerSubtypes(new NamedType(Polygon.class, "polygon"));
        mapper.registerSubtypes(new NamedType(MultiPolygon.class, "multipolygon"));
        mapper.registerSubtypes(new NamedType(LineString.class, "linestring"));
        mapper.registerSubtypes(new NamedType(MultiLineString.class, "multilinestring"));
        mapper.registerSubtypes(new NamedType(Point.class, "point"));
        mapper.registerSubtypes(new NamedType(MultiPoint.class, "multipoint"));
        return mapper;
    }
}
