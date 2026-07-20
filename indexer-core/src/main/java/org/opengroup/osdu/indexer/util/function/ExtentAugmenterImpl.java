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

import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.indexer.StorageType;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.indexer.model.GeoJsonObject;
import org.opengroup.osdu.indexer.model.geojson.*;
import org.opengroup.osdu.indexer.model.indexproperty.ValueExtraction;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExtentAugmenterImpl extends BaseShapeFunction  {
    private static final String regex = "^Extent\\s*\\(\\s*[\\w\\-\\.\\[\\]]+\\s*\\)$";
    private static final int DECIMAL_PLACES = 6;

    private static final String LATITUDE_RANGE = "latitudeRange";
    private static final String LONGITUDE_RANGE = "longitudeRange";
    private static final String LATITUDE_CENTER = "latitudeCenter";
    private static final String LONGITUDE_CENTER = "longitudeCenter";

    private final JaxRsDpsLog jaxRsDpsLog;

    @Override
    protected String getRegex() {
        return regex;
    }

    @Override
    protected JaxRsDpsLog getLogger() {
        return jaxRsDpsLog;
    }

    @Override
    protected List<SchemaItem> doGetExtendedSchemaItems(String extendedPropertyName) {
        List<SchemaItem> extendedSchemaItems = new ArrayList<>();
        extendedSchemaItems.add(createSchemaItem(getLatitudeRangePropertyPath(extendedPropertyName), StorageType.DOUBLE));
        extendedSchemaItems.add(createSchemaItem(getLongitudeRangePropertyPath(extendedPropertyName), StorageType.DOUBLE));
        extendedSchemaItems.add(createSchemaItem(getLatitudeCenterPropertyPath(extendedPropertyName), StorageType.DOUBLE));
        extendedSchemaItems.add(createSchemaItem(getLongitudeCenterPropertyPath(extendedPropertyName), StorageType.DOUBLE));
        return extendedSchemaItems;
    }

    @Override
    protected Map<String, Object> doGetValues(String extendedPropertyName, ValueExtraction valueExtraction, GeometryCollection geometryCollection) {
        Map<String, Object> propertyValues = new HashMap<>();

        if(geometryCollection != null && !CollectionUtils.isEmpty(geometryCollection.getGeometries())) {
            Extent extent = new Extent();
            updateExtent(extent, geometryCollection);
            if(extent.isValid()) {
                propertyValues.put(getLatitudeRangePropertyPath(extendedPropertyName), roundValue(extent.latMax - extent.latMin, DECIMAL_PLACES));
                propertyValues.put(getLatitudeCenterPropertyPath(extendedPropertyName), roundValue((extent.latMax + extent.latMin)/2, DECIMAL_PLACES));

                double longitudeRange = (extent.lonMax - extent.lonMin > 180)
                        ? 360 + extent.lonMin - extent.lonMax : extent.lonMax - extent.lonMin;
                propertyValues.put(getLongitudeRangePropertyPath(extendedPropertyName), roundValue(longitudeRange, DECIMAL_PLACES));
                propertyValues.put(getLongitudeCenterPropertyPath(extendedPropertyName), roundValue((extent.lonMax + extent.lonMin)/2, DECIMAL_PLACES));
            }
        }
        return propertyValues;
    }

    private String getLatitudeRangePropertyPath(String extendedPropertyName) {
        return extendedPropertyName + "." + LATITUDE_RANGE;
    }

    private String getLongitudeRangePropertyPath(String extendedPropertyName) {
        return extendedPropertyName + "." + LONGITUDE_RANGE;
    }

    private String getLatitudeCenterPropertyPath(String extendedPropertyName) {
        return extendedPropertyName + "." + LATITUDE_CENTER;
    }

    private String getLongitudeCenterPropertyPath(String extendedPropertyName) {
        return extendedPropertyName + "." + LONGITUDE_CENTER;
    }

    private void updateExtent(Extent extent, GeometryCollection geometryCollection) {
        if(geometryCollection == null || CollectionUtils.isEmpty(geometryCollection.getGeometries())) {
            return;
        }

        for (GeoJsonObject geoJsonObject : geometryCollection.getGeometries()) {
            if(geoJsonObject instanceof Geometry) {
                updateExtent(extent, ((Geometry<?>) geoJsonObject).getCoordinates());
            }
            else if(geoJsonObject instanceof GeometryCollection subCollection) {
                updateExtent(extent, subCollection);
            }
        }
    }


    private void updateExtent(Extent extent, List<?> coordinates) {
        Object firstElement = coordinates.get(0);
        if(firstElement instanceof List){
            for(Object coordinatesElement : coordinates) {
                updateExtent(extent, (List<?>) coordinatesElement);
            }
        } else if(firstElement instanceof Position){
            updateExtentFromLine(extent, (List<Position>) coordinates);
        }
    }

    private void updateExtentFromLine(Extent extent, List<Position> coordinates) {
        for(Position position : coordinates) {
            if(!Double.isNaN(position.getLatitude())) {
                double latitude = position.getLatitude();
                if(Double.isNaN(extent.latMin) || extent.latMin > latitude) {
                    extent.latMin = latitude;
                }
                if(Double.isNaN(extent.latMax) || extent.latMax < latitude) {
                    extent.latMax = latitude;
                }
            }
            if(!Double.isNaN(position.getLongitude())) {
                double longitude = position.getLongitude();
                if(Double.isNaN(extent.lonMin) || extent.lonMin > longitude) {
                    extent.lonMin = longitude;
                }
                if(Double.isNaN(extent.lonMax) || extent.lonMax < longitude) {
                    extent.lonMax = longitude;
                }
            }
        }
    }

    private static class Extent {
        double latMin = Double.NaN;
        double latMax = Double.NaN;
        double lonMin = Double.NaN;
        double lonMax = Double.NaN;

        boolean isValid() {
            return !(Double.isNaN(latMin) || Double.isNaN(latMax) || Double.isNaN(lonMin) || Double.isNaN(lonMax));
        }
    }
}
