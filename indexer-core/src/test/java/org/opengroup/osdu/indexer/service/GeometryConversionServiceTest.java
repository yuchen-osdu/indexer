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

import com.google.gson.internal.LinkedTreeMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.opengroup.osdu.core.common.Constants;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(SpringRunner.class)
public class GeometryConversionServiceTest {

    @InjectMocks
    private GeometryConversionService sut;

    @Test
    public void should_returnValidResults_given_validTreeMap_getGeoShapeTest() {
        LinkedTreeMap<String, Object> map = new LinkedTreeMap<>();
        List<LinkedTreeMap<String, Object>> features = new ArrayList<>();
        LinkedTreeMap<String, Object> object = new LinkedTreeMap<>();
        LinkedTreeMap<String, Object> geometry = new LinkedTreeMap<>();
        LinkedTreeMap<String, Object> properties = new LinkedTreeMap<>();
        properties.put("dummyKye", "dummyValue");
        geometry.put("type", "type");
        geometry.put("radius", "radius");
        geometry.put("coordinates", "coordinates");
        object.put("geometry", geometry);
        object.put(Constants.PROPERTIES, properties);
        features.add(object);
        map.put("features", features);

        List<Map<String, Object>> geometries = this.sut.getGeoShape(map);

        assertEquals(1, geometries.size());
        assertEquals(properties, geometries.get(0).get(Constants.PROPERTIES));
    }

    @Test
    public void should_returnNull_given_nullOrEmptyTreeMap_tryGetGeopointTest() {
        assertNull(this.sut.tryGetGeopoint(null));
        assertNull(this.sut.tryGetGeopoint(new LinkedTreeMap<>()));
    }

    @Test
    public void should_returnNull_given_invalidTreeMap_tryGetGeopointTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", null);
        positionTreeMap.put("latitude", 20.0);

        Map<String, Double> result = this.sut.tryGetGeopoint(positionTreeMap);
        assertNull(result);
    }

    @Test
    public void should_returnNull_given_longitudeExceedsMax_tryGetGeopointTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", 200.0);
        positionTreeMap.put("latitude", 20.0);

        Map<String, Double> result = this.sut.tryGetGeopoint(positionTreeMap);
        assertNull(result);
    }

    @Test
    public void should_returnNull_given_longitudeBelowMin_tryGetGeopointTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", -200.0);
        positionTreeMap.put("latitude", 20.0);

        Map<String, Double> result = this.sut.tryGetGeopoint(positionTreeMap);
        assertNull(result);
    }

    @Test
    public void should_returnNull_given_latitudeExceedsMax_tryGetGeopointTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", 10.0);
        positionTreeMap.put("latitude", 95.0);

        Map<String, Double> result = this.sut.tryGetGeopoint(positionTreeMap);
        assertNull(result);
    }

    @Test
    public void should_returnNull_given_latitudeBelowMin_tryGetGeopointTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", 10.0);
        positionTreeMap.put("latitude", -95.0);

        Map<String, Double> result = this.sut.tryGetGeopoint(positionTreeMap);
        assertNull(result);
    }

    @Test
    public void should_returnLatLong_given_validTreeMap_tryGetGeopointTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", 10.0);
        positionTreeMap.put("latitude", 20.0);

        Map<String, Double> result = this.sut.tryGetGeopoint(positionTreeMap);

        assertEquals(new Double(10.0), result.get("lon"));
        assertEquals(new Double(20.0), result.get("lat"));
    }

    @Test
    public void should_throwNullPointerException_given_nullTreeMap_getGeopointGeoJsonTest() {
        Map<String, Object> geometry = this.sut.getGeopointGeoJson(null);

        assertNull(geometry);
    }

    @Test
    public void should_returnValidResults_given_validTreeMap_getGeopointGeoJsonTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", 10.0);
        positionTreeMap.put("latitude", 20.0);

        Map<String, Object> result = this.sut.getGeopointGeoJson(positionTreeMap);

        assertEquals(2, result.size());
        assertEquals("geometrycollection", result.get(Constants.TYPE));
    }

    @Test
    public void should_throwNullPointerException_given_nullTreeMap_getGeopointGeometryTest() {
        Map<String, Object> geometry = this.sut.getGeopointGeometry(null);

        assertNull(geometry);
    }

    @Test
    public void should_returnValidResults_given_validTreeMap_getGeopointGeometryTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", 10.0);
        positionTreeMap.put("latitude", 20.0);

        Map<String, Object> result = this.sut.getGeopointGeometry(positionTreeMap);

        assertEquals(2, result.size());
        assertEquals("point", result.get(Constants.TYPE));
    }

    @Test
    public void should_returnNull_given_longitudeExceedsMax_getGeopointGeometryTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", 200.0);
        positionTreeMap.put("latitude", 20.0);

        Map<String, Object> result = this.sut.getGeopointGeometry(positionTreeMap);
        assertNull(result);
    }

    @Test
    public void should_returnNull_given_longitudeBelowMin_getGeopointGeometryTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", -200.0);
        positionTreeMap.put("latitude", 20.0);

        Map<String, Object> result = this.sut.getGeopointGeometry(positionTreeMap);
        assertNull(result);
    }

    @Test
    public void should_returnNull_given_latitudeExceedsMax_getGeopointGeometryTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", 10.0);
        positionTreeMap.put("latitude", 95.0);

        Map<String, Object> result = this.sut.getGeopointGeometry(positionTreeMap);
        assertNull(result);
    }

    @Test
    public void should_returnNull_given_latitudeBelowMin_getGeopointGeometryTest() {
        LinkedTreeMap<String, Double> positionTreeMap = new LinkedTreeMap<>();
        positionTreeMap.put("longitude", 10.0);
        positionTreeMap.put("latitude", -95.0);

        Map<String, Object> result = this.sut.getGeopointGeometry(positionTreeMap);
        assertNull(result);
    }
}
