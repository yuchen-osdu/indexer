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
import java.util.Optional;
import org.opengroup.osdu.core.common.Constants;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequestScope
public class GeometryConversionService {

    private static final String POINT = "point";

    private static final String GEOMETRY_COLLECTION = "geometrycollection";
    private static final String GEOMETRIES = "geometries";
    private static final String COORDINATES = "coordinates";
    private static final String RADIUS = "radius";

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat(".######");


    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getGeoShape(LinkedTreeMap<String, Object> map) {

        String key1 = "GeoJSON.features.geometry";
        String[] strArray = key1.split("\\.");

        // strArray[1] is features
        List<LinkedTreeMap<String, Object>> features = (ArrayList<LinkedTreeMap<String, Object>>) map.get(strArray[1]);
        List<Map<String, Object>> geometries = new ArrayList<>();

        for (LinkedTreeMap<String, Object> linkedMap : features) {

            // strArray[2] is geometry
            LinkedTreeMap<String, Object> geometry = (LinkedTreeMap) linkedMap.get(strArray[2]);

            // properties
            LinkedTreeMap<String, Object> properties = (LinkedTreeMap) linkedMap.get(Constants.PROPERTIES);
            Map<String, Object> innerMap = new HashMap<>();
            String type = (String) geometry.get("type");
            innerMap.put(Constants.TYPE, type.toLowerCase());
            innerMap.put(COORDINATES, geometry.get(COORDINATES));
            if (properties != null && properties.size() > 0)
                innerMap.put(Constants.PROPERTIES, properties);
            if (geometry.get(RADIUS) != null)
                innerMap.put(RADIUS, geometry.get(RADIUS));
            geometries.add(innerMap);
        }

        return geometries;
    }

    public Map<String, Double> tryGetGeopoint(Map<String, Double> positionMap) {

        if (positionMap == null || positionMap.size() == 0) return null;

        try {
            Map<String, Double> position = new HashMap<>();
            double lon = new Double(DECIMAL_FORMAT.format(Optional.ofNullable(positionMap.get("longitude")).orElse(positionMap.get("lon"))));
            if (lon > 180 || lon < -180)
                throw new IllegalArgumentException("'longitude' value is out of the range [-180, 180]");
            double lat = new Double(DECIMAL_FORMAT.format(Optional.ofNullable(positionMap.get("latitude")).orElse(positionMap.get("lat"))));
            if (lat > 90 || lat < -90)
                throw new IllegalArgumentException("'latitude' value is out of the range [-90, 90]");
            position.put("lon", lon);
            position.put("lat", lat);
            return position;
        } catch (NullPointerException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public Map<String, Object> getGeopointGeoJson(Map<String, Double> positionMap) {

        Map<String, Object> geometry = this.getGeopointGeometry(positionMap);
        if (geometry == null) return null;

        List<Map<String, Object>> geometries = new ArrayList<>();
        geometries.add(geometry);

        Map<String, Object> outerMap = new HashMap<>();
        outerMap.put(Constants.TYPE, GEOMETRY_COLLECTION);
        outerMap.put(GEOMETRIES, geometries);

        return outerMap;
    }

    public Map<String, Object> getGeopointGeometry(Map<String, Double> positionMap) {

        try {
            double lon = new Double(DECIMAL_FORMAT.format(positionMap.get("longitude")));
            if (lon > 180 || lon < -180)
                throw new IllegalArgumentException("'longitude' value is out of the range [-180, 180]");
            double lat = new Double(DECIMAL_FORMAT.format(positionMap.get("latitude")));
            if (lat > 90 || lat < -90)
                throw new IllegalArgumentException("'latitude' value is out of the range [-90, 90]");

            Map<String, Object> inMap = new HashMap<>();
            inMap.put(Constants.TYPE, POINT);
            List<Double> points = new ArrayList<>();
            points.add(lon);
            points.add(lat);
            inMap.put(COORDINATES, points);
            return inMap;
        } catch (NullPointerException | IllegalArgumentException ignored) {
            return null;
        }
    }
}
