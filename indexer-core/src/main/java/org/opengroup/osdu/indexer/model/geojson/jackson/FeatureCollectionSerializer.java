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

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.TypeSerializer;
import lombok.Data;
import org.opengroup.osdu.indexer.model.GeoJsonObject;
import org.opengroup.osdu.indexer.model.geojson.*;


@Data
public class FeatureCollectionSerializer extends ValueSerializer<FeatureCollection> {

    @Override
    public void serialize(FeatureCollection value, JsonGenerator jsonGenerator, SerializationContext provider) {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringProperty(GeoJsonConstants.TYPE, GeoJsonConstants.GEOMETRY_COLLECTION);

        jsonGenerator.writeArrayPropertyStart(GeoJsonConstants.GEOMETRIES);
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
    public void serializeWithType(FeatureCollection value, JsonGenerator jsonGenerator, SerializationContext provider, TypeSerializer typeSerializer) {

        serialize(value, jsonGenerator, provider);
    }

    private void serializeGeoShape(GeoJsonObject geoJsonObject, JsonGenerator jsonGenerator) {
        if (geoJsonObject instanceof Point) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringProperty(GeoJsonConstants.TYPE, GeoJsonConstants.POINT);
            jsonGenerator.writePOJOProperty(GeoJsonConstants.COORDINATES, ((Point) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        } else if (geoJsonObject instanceof LineString) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringProperty(GeoJsonConstants.TYPE, GeoJsonConstants.LINE_STRING);
            jsonGenerator.writePOJOProperty(GeoJsonConstants.COORDINATES, ((LineString) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        } else if (geoJsonObject instanceof Polygon) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringProperty(GeoJsonConstants.TYPE, GeoJsonConstants.POLYGON);
            jsonGenerator.writePOJOProperty(GeoJsonConstants.COORDINATES, ((Polygon) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        } else if (geoJsonObject instanceof MultiPoint) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringProperty(GeoJsonConstants.TYPE, GeoJsonConstants.MULTI_POINT);
            jsonGenerator.writePOJOProperty(GeoJsonConstants.COORDINATES, ((MultiPoint) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        } else if (geoJsonObject instanceof MultiLineString) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringProperty(GeoJsonConstants.TYPE, GeoJsonConstants.MULTI_LINE_STRING);
            jsonGenerator.writePOJOProperty(GeoJsonConstants.COORDINATES, ((MultiLineString) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        } else if (geoJsonObject instanceof MultiPolygon) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringProperty(GeoJsonConstants.TYPE, GeoJsonConstants.MULTI_POLYGON);
            jsonGenerator.writePOJOProperty(GeoJsonConstants.COORDINATES, ((MultiPolygon) geoJsonObject).getCoordinates());
            jsonGenerator.writeEndObject();
        }
    }
}
