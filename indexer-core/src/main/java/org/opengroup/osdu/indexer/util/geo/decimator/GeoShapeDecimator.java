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

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.NamedType;
import org.opengroup.osdu.indexer.model.geojson.*;
import org.opengroup.osdu.indexer.model.geojson.jackson.GeoJsonConstants;
import org.springframework.stereotype.Component;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Component
public class GeoShapeDecimator {
    private final ObjectMapper deserializerMapper;
    private final ObjectMapper serializerMapper;

    @Inject
    private GeometryDecimator decimator;

    public GeoShapeDecimator() {
        serializerMapper = JsonMapper.builderWithJackson2Defaults().build();
        deserializerMapper = createDeserializerMapper();
    }

    public DecimatedResult decimateShapeObj(Map<String, Object> shapeObj) throws JacksonException {
        DecimatedResult result = new DecimatedResult();
        String type = (String)shapeObj.getOrDefault(GeoJsonConstants.TYPE, null);
        if(type != null && type.equals(GeoJsonConstants.GEOMETRY_COLLECTION)) {
            GeometryCollection geometryCollection = deserializerMapper.readValue(deserializerMapper.writeValueAsString(shapeObj), GeometryCollection.class);
            boolean decimated = decimator.decimate(geometryCollection);
            result.setDecimated(decimated);
            if(decimated) {
                Map<String, Object> decimatedShapeObj = serializerMapper.readValue(serializerMapper.writeValueAsString(geometryCollection), new TypeReference<Map<String, Object>>() {});
                result.setDecimatedShapeObj(decimatedShapeObj);
            }
        }

        return result;
    }

    @NotNull
    private static ObjectMapper createDeserializerMapper() {
        return JsonMapper.builderWithJackson2Defaults()
                .registerSubtypes(new NamedType(GeometryCollection.class, GeoJsonConstants.GEOMETRY_COLLECTION))
                .registerSubtypes(new NamedType(Polygon.class, GeoJsonConstants.POLYGON))
                .registerSubtypes(new NamedType(MultiPolygon.class, GeoJsonConstants.MULTI_POLYGON))
                .registerSubtypes(new NamedType(LineString.class, GeoJsonConstants.LINE_STRING))
                .registerSubtypes(new NamedType(MultiLineString.class, GeoJsonConstants.MULTI_LINE_STRING))
                .registerSubtypes(new NamedType(Point.class, GeoJsonConstants.POINT))
                .registerSubtypes(new NamedType(MultiPoint.class, GeoJsonConstants.MULTI_POINT))
                .build();
    }
}
