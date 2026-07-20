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
import org.opengroup.osdu.indexer.model.geojson.Point;
import org.springframework.test.context.junit4.SpringRunner;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

@RunWith(SpringRunner.class)
public class PointDeserializerTest {
    private static ObjectMapper objectMapper;

    @BeforeClass
    public static void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerSubtypes(new NamedType(Point.class, GeoJsonConstants.POINT));
    }

    @Test
    public void deserialize_2d_point() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("point_2d.json");
        Point point = objectMapper.readValue(shapeJson, Point.class);
        Assert.assertNotNull(point);
        Assert.assertFalse(Double.isNaN(point.getCoordinates().getLongitude()));
        Assert.assertFalse(Double.isNaN(point.getCoordinates().getLatitude()));
        Assert.assertTrue(Double.isNaN(point.getCoordinates().getAltitude()));
    }

    @Test
    public void deserialize_3d_point() throws JsonProcessingException {
        String shapeJson = getGeoShapeFromFile("point_3d.json");
        Point point = objectMapper.readValue(shapeJson, Point.class);
        Assert.assertNotNull(point);
        Assert.assertFalse(Double.isNaN(point.getCoordinates().getLongitude()));
        Assert.assertFalse(Double.isNaN(point.getCoordinates().getLatitude()));
        Assert.assertFalse(Double.isNaN(point.getCoordinates().getAltitude()));
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
