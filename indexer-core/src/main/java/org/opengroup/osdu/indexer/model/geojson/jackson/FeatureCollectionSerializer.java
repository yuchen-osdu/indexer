// Copyright © Schlumberger
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

package org.opengroup.osdu.indexer.model.geojson.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import lombok.Data;
import org.opengroup.osdu.indexer.model.GeoJsonObject;
import org.opengroup.osdu.indexer.model.geojson.*;

import java.io.IOException;

@Data
public class FeatureCollectionSerializer extends JsonSerializer<FeatureCollection> {

    @Override
    public void serialize(FeatureCollection value, JsonGenerator jsonGenerator, SerializerProvider provider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(GeoJsonConstants.TYPE, GeoJsonConstants.GEOMETRY_COLLECTION);

        jsonGenerator.writeArrayFieldStart(GeoJsonConstants.GEOMETRIES);
        for (Feature feature : value.getFeatures()) {
            if (feature.getGeometry() instanceof GeometryCollection) {
                GeometryCollection geometryCollection = (GeometryCollection) feature.getGeometry();
                for (GeoJsonObject shape : geometryCollection.getGeometries()) {
                    serializeGeoShape(shape, jsonGenerator);
                }
            } else {
                serializeGeoShape(feature.getGeometry(), jsonGenerator);
            }
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeEndObject();
    }

    @Override
    public void serializeWithType(FeatureCollection value, JsonGenerator jsonGenerator, SerializerProvider provider, TypeSerializer typeSerializer)
            throws IOException {

        serialize(value, jsonGenerator, provider);
    }

    private void serializeGeoShape(GeoJsonObject geoJsonObject, JsonGenerator jsonGenerator) throws IOException {
        if (geoJsonObject instanceof Point) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField(GeoJsonConstants.TYPE, GeoJsonConstants.POINT);
            jsonGenerator.writeObjectField(GeoJsonConstants.COORDINATES, ((Point) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        } else if (geoJsonObject instanceof LineString) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField(GeoJsonConstants.TYPE, GeoJsonConstants.LINE_STRING);
            jsonGenerator.writeObjectField(GeoJsonConstants.COORDINATES, ((LineString) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        } else if (geoJsonObject instanceof Polygon) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField(GeoJsonConstants.TYPE, GeoJsonConstants.POLYGON);
            jsonGenerator.writeObjectField(GeoJsonConstants.COORDINATES, ((Polygon) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        } else if (geoJsonObject instanceof MultiPoint) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField(GeoJsonConstants.TYPE, GeoJsonConstants.MULTI_POINT);
            jsonGenerator.writeObjectField(GeoJsonConstants.COORDINATES, ((MultiPoint) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        } else if (geoJsonObject instanceof MultiLineString) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField(GeoJsonConstants.TYPE, GeoJsonConstants.MULTI_LINE_STRING);
            jsonGenerator.writeObjectField(GeoJsonConstants.COORDINATES, ((MultiLineString) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        } else if (geoJsonObject instanceof MultiPolygon) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField(GeoJsonConstants.TYPE, GeoJsonConstants.MULTI_POLYGON);
            jsonGenerator.writeObjectField(GeoJsonConstants.COORDINATES, ((MultiPolygon) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        }
    }
}
