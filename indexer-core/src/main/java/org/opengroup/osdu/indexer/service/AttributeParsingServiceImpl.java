// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.indexer.service;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.indexer.ElasticType;
import org.opengroup.osdu.core.common.model.indexer.IndexingStatus;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.indexer.util.parser.BooleanParser;
import org.opengroup.osdu.indexer.util.parser.DateTimeParser;
import org.opengroup.osdu.indexer.util.parser.GeoShapeParser;
import org.opengroup.osdu.indexer.util.parser.NumberParser;
import org.opengroup.osdu.indexer.util.parser.StringParser;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.inject.Inject;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Service
@RequestScope
public class AttributeParsingServiceImpl implements IAttributeParsingService {

    @Inject
    private NumberParser numberParser;
    @Inject
    private BooleanParser booleanParser;
    @Inject
    private StringParser stringParser;
    @Inject
    private DateTimeParser dateTimeParser;
    @Inject
    private GeoShapeParser geoShapeParser;
    @Inject
    private GeometryConversionService geometryConversionService;
    @Inject
    private JobStatus jobStatus;

    private Gson gson = new Gson();

    @Override
    public void tryParseValueArray(Class<?> attributeClass, String recordId, String attributeName, Object attributeVal, Map<String, Object> dataMap) {
        BiFunction<String, Object, ?> parser;
        ElasticType elasticType = attributeClass != String.class ? ElasticType.forValue(attributeClass.getSimpleName()) : ElasticType.TEXT;
        switch (elasticType) {
            case TEXT:
            case KEYWORD:
                parser = this.stringParser::parseString;
                break; 
            case DOUBLE:
                parser = this.numberParser::parseDouble;
                break;
            case FLOAT:
                parser = this.numberParser::parseFloat;
                break;
            case INTEGER:
                parser = this.numberParser::parseInteger;
                break;
            case LONG:
                parser = this.numberParser::parseLong;
                break;
            case BOOLEAN:
                parser = this.booleanParser::parseBoolean;
                break;
            case DATE:
                parser = this.dateTimeParser::parseDate;
                // DateTime parser output is String
                attributeClass = String.class;
                break;
            default:
                throw new IllegalArgumentException("Invalid array attribute type");
        }

        try {
            List<String> parsedStringList = isArrayType(attributeVal, attributeClass);
            List out = new ArrayList<>();
            for (Object o : parsedStringList) {
                out.add(parser.apply(attributeName, o));
            }
            Object parsedAttribute = toTypeArray(attributeClass, out);
            dataMap.put(attributeName, parsedAttribute);
        } catch (IllegalArgumentException e) {
            this.jobStatus.addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, e.getMessage(), String.format("record-id: %s | %s", recordId, e.getMessage()));
        }
    }

    @Override
    public void tryParseInteger(String recordId, String attributeName, Object attributeVal, Map<String, Object> dataMap) {
        try {
            int parsedInteger = this.numberParser.parseInteger(attributeName, attributeVal);
            dataMap.put(attributeName, parsedInteger);
        } catch (IllegalArgumentException e) {
            jobStatus.addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, e.getMessage(), String.format("record-id: %s | %s", recordId, e.getMessage()));
        }
    }

    @Override
    public void tryParseLong(String recordId, String attributeName, Object attributeVal, Map<String, Object> dataMap) {
        try {
            long parsedLong = this.numberParser.parseLong(attributeName, attributeVal);
            dataMap.put(attributeName, parsedLong);
        } catch (IllegalArgumentException e) {
            jobStatus.addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, e.getMessage(), String.format("record-id: %s | %s", recordId, e.getMessage()));
        }
    }

    @Override
    public void tryParseFloat(String recordId, String attributeName, Object attributeVal, Map<String, Object> dataMap) {
        try {
            float parsedFloat = this.numberParser.parseFloat(attributeName, attributeVal);
            dataMap.put(attributeName, parsedFloat);
        } catch (IllegalArgumentException e) {
            jobStatus.addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, e.getMessage(), String.format("record-id: %s | %s", recordId, e.getMessage()));
        }
    }

    @Override
    public void tryParseDouble(String recordId, String attributeName, Object attributeVal, Map<String, Object> dataMap) {
        try {
            double parsedDouble = this.numberParser.parseDouble(attributeName, attributeVal);
            dataMap.put(attributeName, parsedDouble);
        } catch (IllegalArgumentException e) {
            jobStatus.addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, e.getMessage(), String.format("record-id: %s | %s", recordId, e.getMessage()));
        }
    }

    @Override
    public void tryParseBoolean(String recordId, String attributeName, Object attributeVal, Map<String, Object> dataMap) {
        boolean parsedBoolean = this.booleanParser.parseBoolean(attributeName, attributeVal);
        dataMap.put(attributeName, parsedBoolean);
    }

    @Override
    public void tryParseString(String recordId, String attributeName, Object attributeVal, Map<String, Object> dataMap) {
        String parsedBoolean = this.stringParser.parseString(attributeName, attributeVal);
        dataMap.put(attributeName, parsedBoolean);
    }

    @Override
    public void tryParseDate(String recordId, String attributeName, Object attributeVal, Map<String, Object> dataMap) {
        try {
            String parsedDate = this.dateTimeParser.parseDate(attributeName, attributeVal);
            if (parsedDate == null) return;
            dataMap.put(attributeName, parsedDate);
        } catch (IllegalArgumentException e) {
            jobStatus.addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, e.getMessage(), String.format("record-id: %s | %s", recordId, e.getMessage()));
        }
    }

    @Override
    public void tryParseGeopoint(String recordId, String attributeName, Object attributeVal, Map<String, Object> dataMap) {

        try {
            Type type = new TypeToken<Map<String, Double>>() {}.getType();
            Map<String, Double> positionMap = this.gson.fromJson(attributeVal.toString(), type);

            if (positionMap == null || positionMap.isEmpty()) return;

            Map<String, Double> position = this.geometryConversionService.tryGetGeopoint(positionMap);

            if (position == null || position.isEmpty()) return;

            dataMap.put(attributeName, position);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            String parsingError = String.format("geo-point parsing error: %s attribute: %s | value: %s", e.getMessage(), attributeName, attributeVal);
            jobStatus.addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, parsingError, String.format("record-id: %s | %s", recordId, parsingError));
        }
    }

    @Override
    public void tryParseGeojson(String recordId, String attributeName, Object attributeVal, Map<String, Object> dataMap) {

        try {
            Map<String, Object> geoJsonMap = new HashMap<>();
            if (attributeVal instanceof Map) {
                try {
                    geoJsonMap = (Map<String, Object>) attributeVal;
                } catch (ClassCastException e) {
                    String parsingError = String.format("geo-json shape parsing error: %s attribute: %s", e.getMessage(), attributeName);
                    jobStatus.addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, parsingError, String.format("record-id: %s | %s", recordId, parsingError));
                }
            }

            if (geoJsonMap.isEmpty()) {
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                geoJsonMap = this.gson.fromJson(this.gson.toJson(attributeVal, type), type);
            }

            if (geoJsonMap == null || geoJsonMap.isEmpty()) return;

            Map<String, Object> parsedShape = this.geoShapeParser.parseGeoJson(geoJsonMap);

            dataMap.put(attributeName, parsedShape);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            String parsingError = String.format("geo-json shape parsing error: %s attribute: %s", e.getMessage(), attributeName);
            jobStatus.addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, parsingError, String.format("record-id: %s | %s", recordId, parsingError));
        }
    }

    @Override
    public void tryParseNested(String recordId, String name, Object value, Map<String, Object> dataMap) {
        dataMap.put(name, value);
    }

    @Override
    public void tryParseObject(String recordId, String name, Object value, Map<String, Object> dataMap) {
        dataMap.put(name, value);
    }

    @Override
    public void tryParseFlattened(String recordId, String name, Object value, Map<String, Object> dataMap) {
        dataMap.put(name, value);
    }


    private List<String> isArrayType(Object attributeVal, Class<?> attributeClass) {
        // Handling string arrays as they're not valid JSON for code below
        if (attributeVal != null && attributeClass == String.class && attributeVal instanceof List<?>) {
            List<String> result = new ArrayList<>();
            for (Object item: (List<Object>)(List<?>) attributeVal) {
                result.add(item == null ? null : String.valueOf(item));
            }
            return result;
        }
        
        try { 
            String value = attributeVal == null ? null : String.valueOf(attributeVal);
            if (attributeVal == null || Strings.isNullOrEmpty(value)) {
                return Collections.EMPTY_LIST;
            }

            Gson converter = new Gson();
            Type type = new TypeToken<List<String>>() {}.getType();
            return converter.fromJson(value, type);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("array parsing error, not a valid array");
        }
    }

    private <N> N toTypeArray(Class<N> fieldClass, List<?> list) {
        Object array = Array.newInstance(fieldClass, list.size());
        for (int i = 0; i < list.size(); i++) {
            Array.set(array, i, list.get(i));
        }
        return (N) array;
    }
}

