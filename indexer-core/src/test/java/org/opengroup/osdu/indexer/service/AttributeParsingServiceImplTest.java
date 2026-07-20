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

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.indexer.util.parser.BooleanParser;
import org.opengroup.osdu.indexer.util.parser.DateTimeParser;
import org.opengroup.osdu.indexer.util.parser.GeoShapeParser;
import org.opengroup.osdu.indexer.util.parser.NumberParser;
import org.opengroup.osdu.indexer.util.parser.StringParser;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class AttributeParsingServiceImplTest {

    @Mock
    private GeometryConversionService geometryConversionService;
    @Spy
    private BooleanParser booleanParser = new BooleanParser();
    @Spy
    private StringParser stringParser = new StringParser();
    @Spy
    private NumberParser numberParser = new NumberParser();
    @Spy
    private DateTimeParser dateTimeParser = new DateTimeParser();
    @Mock
    private GeoShapeParser geoShapeParser;
    @Mock
    private JaxRsDpsLog log;
    @Mock
    private JobStatus jobStatus;
    @InjectMocks
    private AttributeParsingServiceImpl sut;

    @Test
    public void should_parseValidInteger() {
        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseInteger("common:welldb:wellbore-OGY4ZWQ5", "lat", "101959.15", dataMap);
        assertEquals(dataMap.size(), 1);
        assertTrue(dataMap.containsKey("lat"));
    }

    @Test
    public void should_parseValidIntegerArray() {
        Map<String, Object> dataMap = new HashMap<>();
        final Map<Object, Object> inputs = new HashMap<Object, Object>() {
            {
                put("[]", new Integer[]{});
                put("[101959.1]", new Integer[]{101959});
                put("[139, 20]", new Integer[]{139, 20});
                put("[\"139.987\", \"20\"]", new Integer[]{139, 20});
            }
        };

        this.validateInput(this.sut::tryParseValueArray, Integer.class, Integer.class, inputs, dataMap);
    }

    @Test
    public void should_parseValidLongArray() {
        Map<String, Object> dataMap = new HashMap<>();
        final Map<Object, Object> inputs = new HashMap<Object, Object>() {
            {
                put("[]", new Long[]{});
                put("[4115420654264075766]", new Long[]{4115420654264075766L});
                put("[4115420, 20]", new Long[]{4115420L, 20L});
                put("[\"139\", \"20\"]", new Long[]{139L, 20L});
            }
        };

        this.validateInput(this.sut::tryParseValueArray, Long.class, Long.class, inputs, dataMap);
    }

    @Test
    public void should_parseValidLong() {
        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseLong("common:welldb:wellbore-OGY4ZWQ5", "reference", "", dataMap);
        assertEquals(dataMap.size(), 1);
        assertEquals(dataMap.get("reference"), 0L);
    }

    @Test
    public void should_parseValidFloat() {
        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseFloat("common:welldb:wellbore-MjVhND", "lon", null, dataMap);
        assertEquals(dataMap.size(), 1);
        assertEquals(dataMap.get("lon"), 0.0f);
    }

    @Test
    public void should_parseValidFloatArray() {
        Map<String, Object> dataMap = new HashMap<>();
        final Map<Object, Object> inputs = new HashMap<Object, Object>() {
            {
                put("[]", new Float[]{});
                put("[101959.1]", new Float[]{101959.1f});
                put("[139.90, 20.7]", new Float[]{139.90f, 20.7f});
                put("[\"139.987\", \"20\"]", new Float[]{139.987f, 20.0f});
            }
        };

        this.validateInput(this.sut::tryParseValueArray, Float.class, Float.class, inputs, dataMap);
    }

    @Test
    public void should_parseValidDouble() {
        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseDouble("common:welldb:wellbore-zMWQtMm", "location", 20.0, dataMap);
        assertEquals(dataMap.size(), 1);
        assertEquals(dataMap.get("location"), 20.0);
    }

    @Test
    public void should_parseValidDoubleArray() {
        Map<String, Object> dataMap = new HashMap<>();
        final Map<Object, Object> inputs = new HashMap<Object, Object>() {
            {
                put("[]", new Double[]{});
                put("[101959.1]", new Double[]{101959.1});
                put("[139.1, 20.0]", new Double[]{139.1, 20.0});
                put("[\"139.9\", \"20.1\"]", new Double[]{139.9, 20.1});
            }
        };

        this.validateInput(this.sut::tryParseValueArray, Double.class, Double.class, inputs, dataMap);
    }

    @Test
    public void should_parseBoolean() {
        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseBoolean("common:welldb:wellbore-OGY4ZWQ5", "dry", "", dataMap);
        assertEquals(dataMap.size(), 1);
        assertEquals(dataMap.get("dry"), false);

        this.sut.tryParseBoolean("common:welldb:wellbore-OGY4ZWQ5", "active", null, dataMap);
        assertEquals(dataMap.size(), 2);
        assertEquals(dataMap.get("active"), false);

        this.sut.tryParseBoolean("common:welldb:wellbore-OGY4ZWQ5", "notation", "E.2131", dataMap);
        assertEquals(dataMap.size(), 3);
        assertEquals(dataMap.get("notation"), false);

        this.sut.tryParseBoolean("common:welldb:wellbore-OGY4ZWQ5", "aw", false, dataMap);
        assertEquals(dataMap.size(), 4);
        assertEquals(dataMap.get("aw"), false);

        this.sut.tryParseBoolean("common:welldb:wellbore-OGY4ZWQ5", "side", "true", dataMap);
        assertEquals(dataMap.size(), 5);
        assertEquals(dataMap.get("side"), true);
    }

    @Test
    public void should_parseValidBooleanArray() {
        Map<String, Object> dataMap = new HashMap<>();
        final Map<Object, Object> inputs = new HashMap<Object, Object>() {
            {
                put("[]", new Boolean[]{});
                put("[true]", new Boolean[]{true});
                put("[false, truee]", new Boolean[]{false, false});
                put("[\"true\", \"false\"]", new Boolean[]{true, false});
            }
        };

        this.validateInput(this.sut::tryParseValueArray, Boolean.class, Boolean.class, inputs, dataMap);
    }

    @Test
    public void should_parseString() {
        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseString("common:welldb:wellbore-OGY4ZWQ5", "dry", "", dataMap);
        assertEquals(dataMap.size(), 1);
        assertEquals(dataMap.get("dry"), "");

        this.sut.tryParseString("common:welldb:wellbore-OGY4ZWQ5", "active", null, dataMap);
        assertEquals(dataMap.size(), 2);
        assertEquals(dataMap.get("active"), null);

        this.sut.tryParseString("common:welldb:wellbore-OGY4ZWQ5", "notation", "E.2131", dataMap);
        assertEquals(dataMap.size(), 3);
        assertEquals(dataMap.get("notation"), "E.2131");

        this.sut.tryParseString("common:welldb:wellbore-OGY4ZWQ5", "aw", false, dataMap);
        assertEquals(dataMap.size(), 4);
        assertEquals(dataMap.get("aw"), "false");

        this.sut.tryParseString("common:welldb:wellbore-OGY4ZWQ5", "side", "true", dataMap);
        assertEquals(dataMap.size(), 5);
        assertEquals(dataMap.get("side"), "true");
    }

        @Test
    public void should_parseValidStringArray() {
        Map<String, Object> dataMap = new HashMap<>();
        final Map<Object, Object> inputs = new HashMap<Object, Object>() {
            {
                put("[]", new String[]{});
                put("[test]", new String[]{"test"});
                put("[aaa, bbb]", new String[]{"aaa", "bbb"});
                put("[\"139.987\", \"20\"]", new String[]{"139.987", "20"});
            }
        };

        this.validateInput(this.sut::tryParseValueArray, String.class, String.class, inputs, dataMap);
    }

    @Test
    public void should_parseDate_tryParseDate() {
        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseDate("common:welldb:wellbore-OGY4ZWQ5", "createTime", "", dataMap);
        assertEquals(dataMap.size(), 0);
        assertFalse(dataMap.containsKey("createTime"));

        this.sut.tryParseDate("common:welldb:wellbore-OGY4ZWQ5", "activatedOn", null, dataMap);
        assertEquals(dataMap.size(), 0);
        assertFalse(dataMap.containsKey("activatedOn"));

        this.sut.tryParseDate("common:welldb:wellbore-OGY4ZWQ5", "activatedOn", "E.2131", dataMap);
        assertEquals(dataMap.size(), 0);
        assertFalse(dataMap.containsKey("activatedOn"));

        when(this.dateTimeParser.parseDate("disabledOn", "2018-11-06T19:37:11.128Z")).thenReturn("2018-11-06T19:37:11+0000");
        this.sut.tryParseDate("common:welldb:wellbore-OGY4ZWQ5", "disabledOn", "2018-11-06T19:37:11.128Z", dataMap);
        assertEquals(dataMap.size(), 1);
        assertTrue(dataMap.containsKey("disabledOn"));
        assertEquals(dataMap.get("disabledOn"), "2018-11-06T19:37:11+0000");
    }

    @Test
    public void should_parseValidDateArray() {
        Map<String, Object> dataMap = new HashMap<>();
        final Map<Object, Object> inputs = new HashMap<Object, Object>() {
            {
                put("[]", new String[]{});
                put("[\"2018-11-06T19:37:11.128Z\"]", new String[]{"2018-11-06T19:37:11.128+0000"});
                put("[20000102, 2000-01-02]", new String[]{"2000-01-02T00:00:00+0000", "2000-01-02T00:00:00+0000"});
                // TODO: put("[2018-11-06T19:37:11.128Z]", new String[]{"2018-11-06T19:37:11.128+0000"});
            }
        };

        this.validateInput(this.sut::tryParseValueArray, Date.class, String.class, inputs, dataMap);
    }

    @Test
    public void should_notReturnLatLong_given_oneOfTheNullAttribute_tryGetGeopointTest() {
        LinkedTreeMap<String, Object> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", null);
        positionTreeMap.put("latitude", 20.0);

        Map<String, Object> storageData = new HashMap<>();
        storageData.put("location", positionTreeMap);

        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseGeopoint("common:welldb:wellbore-NjdhZTZ", "location", storageData, dataMap);

        assertTrue(dataMap.isEmpty());
    }

    @Test
    public void should_notReturnLatLong_given_oneOfTheEmptyAttribute_tryGetGeopointTest() {
        LinkedTreeMap<String, Object> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", 23.46);
        positionTreeMap.put("latitude", "");

        Map<String, Object> storageData = new HashMap<>();
        storageData.put("location", positionTreeMap);

        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseGeopoint("common:welldb:wellbore-NjdhZTZ", "location", storageData, dataMap);

        assertTrue(dataMap.isEmpty());
    }

    @Test
    public void should_notReturnLatLong_given_invalidTreeMap_tryGetGeopointTest() {
        LinkedTreeMap<String, Object> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", "hello");
        positionTreeMap.put("latitude", 20.0);

        Map<String, Object> storageData = new HashMap<>();
        storageData.put("location", positionTreeMap);

        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseGeopoint("", "location", storageData, dataMap);

        assertTrue(dataMap.isEmpty());
    }

    @Test
    public void should_notReturnLatLong_given_outOfRange_tryGetGeopointTest() {
        LinkedTreeMap<String, Object> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", -189);
        positionTreeMap.put("latitude", 20.0);

        Map<String, Object> storageData = new HashMap<>();
        storageData.put("location", positionTreeMap);

        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseGeopoint("", "location", storageData, dataMap);

        assertTrue(dataMap.isEmpty());
    }

    @Test
    public void should_returnLatLong_given_validTreeMap_tryGetGeopointTest() {
        Map<String, Double> positionMap = new HashMap<>();
        positionMap.put("longitude", 10.45);
        positionMap.put("latitude", 90.0);

        when(this.geometryConversionService.tryGetGeopoint(positionMap)).thenReturn(positionMap);

        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseGeopoint("", "location", positionMap, dataMap);

        assertFalse(dataMap.isEmpty());
    }

    @Test
    public void should_returnGeoShape_given_validTreeMap_tryGetGeoShapeTest() {
        final String shapeJson = "{\"type\":\"Polygon\",\"coordinates\":[[[100,0],[101,0],[101,1],[100,1],[100,0]]]}";
        Map<String, Object> storageData = parseJson(shapeJson);

        when(this.geoShapeParser.parseGeoJson(storageData)).thenReturn(new HashMap<>());

        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseGeojson("", "location", storageData, dataMap);

        assertFalse(dataMap.isEmpty());
    }

    @Test
    public void should_returnGeoShape_given_validTreeMap_tryGetGeoShapeWithPropertiesTest() {
        final String shapeJson = "{\"features\":[{\"geometry\":{\"type\":\"Point\",\"coordinates\":[-105.01621,39.57422]},\"properties\":{\"id\":\"opendes:work-product-component--GenericRepresentation:0be3c0de-7844-4bcb-a17d-83de84cd2eca\",\"uri\":\"wdms:opendes:1188d27c-9132-41ec-b281-502a6245d00c:f597df66-4197-4347-99c2-acb58ce27ef3:0be3c0de-7844-4bcb-a17d-83de84cd2eca\"},\"type\":\"Feature\"}],\"type\":\"FeatureCollection\"}";
        Map<String, Object> storageData = parseJson(shapeJson);

        when(this.geoShapeParser.parseGeoJson(storageData)).thenReturn(new HashMap<>());

        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseGeojson("", "location", storageData, dataMap);

        assertFalse(dataMap.isEmpty());
    }

    @Test
    public void should_throwException_given_geoShapeParingFailed() {
        final String shapeJson = "{\"type\":\"Polygon\",\"coordinates\":[[[100,NaN],[101,0],[101,1],[100,1],[100,0]]]}";
        Map<String, Object> storageData = parseJson(shapeJson);

        when(this.geoShapeParser.parseGeoJson(any())).thenThrow(new IllegalArgumentException("geo coordinates must be numbers"));

        Map<String, Object> dataMap = new HashMap<>();

        this.sut.tryParseGeojson("", "location", storageData, dataMap);

        assertTrue(dataMap.isEmpty());
    }

    private Map<String, Object> parseJson(String json) {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        return new Gson().fromJson(json, type);
    }

    private <I, O> void validateInput(QuintConsumer<Class<I>, String, String, Object, Map<String, Object>> parser, Class<I> inputType, Class<O> expectedType, Map<Object, Object> inputs, Map<String, Object> outMap) {
        inputs.forEach((attributeVal, expectedOut) -> {
            try {
                parser.accept(inputType, "dummyId", "dummyAttribute", attributeVal, outMap);

                assertEquals(outMap.size(), 1);
                assertTrue(outMap.containsKey("dummyAttribute"));
                assertArrayEquals((I[]) outMap.get("dummyAttribute"), (O[]) expectedOut);
            } catch (IllegalArgumentException e) {
                fail(String.format("Parsing exception expected for %s with value [ %s ]", inputType.getName(), attributeVal));
            }
        });
    }

    @FunctionalInterface
    private interface QuintConsumer<T, U, V, W, X> {
        /**
         * Applies this function to the given arguments.
         *
         * @param t the first function argument
         * @param u the second function argument
         * @param v the third function argument
         * @param w the fourth function argument
         *          * @return the function result
         */
        void accept(T t, U u, V v, W w, X x);
    }
}
