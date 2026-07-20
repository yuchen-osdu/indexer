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

package org.opengroup.osdu.indexer.util.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.google.common.base.Strings;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.indexer.StorageType;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.indexer.model.geojson.*;
import org.opengroup.osdu.indexer.model.geojson.jackson.GeoJsonConstants;
import org.opengroup.osdu.indexer.model.indexproperty.ValueExtraction;
import org.opengroup.osdu.indexer.util.PropertyUtil;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseShapeFunction implements IAugmenterFunction {
    private ObjectMapper deserializerMapper = null;

    @Override
    public boolean isMatched(ValueExtraction valueExtraction) {
        if(valueExtraction != null && valueExtraction.isValid()) {
            String regx = getRegex();
            String valuePath = getAndTrimValuePath(valueExtraction);
            if(!Strings.isNullOrEmpty(regx) && !Strings.isNullOrEmpty(valuePath)) {
                Pattern pattern = Pattern.compile(regx, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(valuePath);
                return matcher.matches();
            }
        }
        return false;
    }

    @Override
    public List<String> getValuePaths(ValueExtraction valueExtraction) {
        if(isMatched(valueExtraction)) {
            String valuePath = getAndTrimValuePath(valueExtraction);
            int startIndex = valuePath.indexOf('(') + 1;
            int endIndex = valuePath.lastIndexOf(')');
            valuePath =valuePath.substring(startIndex, endIndex).trim();
            valuePath = PropertyUtil.removeDataPrefix(valuePath);
            return List.of(valuePath);
        }
        return List.of(valueExtraction.getValuePath());
    }

    @Override
    public List<SchemaItem> getExtendedSchemaItems(String extendedPropertyName) {
        if(Strings.isNullOrEmpty(extendedPropertyName)) {
            return new ArrayList<>();
        }
        extendedPropertyName = PropertyUtil.removeDataPrefix(extendedPropertyName);
        return doGetExtendedSchemaItems(extendedPropertyName);
    }

    @Override
    public Map<String, Object> getPropertyValues(String extendedPropertyName, ValueExtraction valueExtraction, Map<String, Object> originalPropertyValues) {
        extendedPropertyName = PropertyUtil.removeDataPrefix(extendedPropertyName);
        List<String> valuePaths = getValuePaths(valueExtraction);
        GeometryCollection geometryCollection = null;
        if(!CollectionUtils.isEmpty(originalPropertyValues) && !CollectionUtils.isEmpty(valuePaths)) {
            try {
                String valuePath = valuePaths.get(0);
                Map<String, Object> shapeObj = (Map<String, Object>) originalPropertyValues.getOrDefault(valuePath, null);
                if(shapeObj != null) {
                    ObjectMapper objectMapper = getDeserializerMapper();
                    geometryCollection = objectMapper.readValue(objectMapper.writeValueAsString(shapeObj), GeometryCollection.class);
                }
            }
            catch(Exception e) {
                getLogger().error("Failed to deserialize the shape object", e);
            }
        }
        if(geometryCollection != null) {
            return doGetValues(extendedPropertyName, valueExtraction, geometryCollection);
        }
        else {
            return new HashMap<>();
        }
    }

    protected abstract String getRegex();
    protected abstract JaxRsDpsLog getLogger();
    protected abstract List<SchemaItem> doGetExtendedSchemaItems(String extendedPropertyName);
    protected abstract Map<String, Object> doGetValues(String extendedPropertyName, ValueExtraction valueExtraction, GeometryCollection geometryCollection);

    protected double roundValue(double value, int decimalPlaces) {
        double scale = Math.pow(10, decimalPlaces);
        return Math.round(value * scale)/scale;
    }

    protected SchemaItem createSchemaItem(String extendedPropertyName, StorageType storageTye) {
        SchemaItem schemaItem = new SchemaItem();
        schemaItem.setPath(extendedPropertyName);
        schemaItem.setKind(storageTye.getValue());
        return schemaItem;
    }

    private String getAndTrimValuePath(ValueExtraction valueExtraction) {
        if(!valueExtraction.isValid()) {
            return "";
        }

        return valueExtraction.getValuePath().trim();
    }

    private synchronized ObjectMapper getDeserializerMapper() {
        if (deserializerMapper == null) {
            deserializerMapper = new ObjectMapper();
            deserializerMapper.registerSubtypes(new NamedType(GeometryCollection.class, GeoJsonConstants.GEOMETRY_COLLECTION));
            deserializerMapper.registerSubtypes(new NamedType(Polygon.class, GeoJsonConstants.POLYGON));
            deserializerMapper.registerSubtypes(new NamedType(MultiPolygon.class, GeoJsonConstants.MULTI_POLYGON));
            deserializerMapper.registerSubtypes(new NamedType(LineString.class, GeoJsonConstants.LINE_STRING));
            deserializerMapper.registerSubtypes(new NamedType(MultiLineString.class, GeoJsonConstants.MULTI_LINE_STRING));
            deserializerMapper.registerSubtypes(new NamedType(Point.class, GeoJsonConstants.POINT));
            deserializerMapper.registerSubtypes(new NamedType(MultiPoint.class, GeoJsonConstants.MULTI_POINT));
        }
        return deserializerMapper;
    }
}
