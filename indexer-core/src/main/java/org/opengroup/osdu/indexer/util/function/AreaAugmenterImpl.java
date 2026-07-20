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
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.PolygonArea;
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
public class AreaAugmenterImpl extends BaseShapeFunction {
    private static final String regex = "^Area\\s*\\(\\s*[\\w\\-\\.\\[\\]]+\\s*\\)$";
    private static final int DECIMAL_PLACES = 2;

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
        extendedSchemaItems.add(createSchemaItem(extendedPropertyName, StorageType.DOUBLE));
        return extendedSchemaItems;
    }

    @Override
    protected Map<String, Object> doGetValues(String extendedPropertyName, ValueExtraction valueExtraction, GeometryCollection geometryCollection) {
        Map<String, Object> propertyValues = new HashMap<>();

        if(geometryCollection != null && geometryCollection.getGeometries().size() == 1) {
            double area = Double.NaN;
            GeoJsonObject geoJsonObject = geometryCollection.getGeometries().get(0);
            try {
                if (geoJsonObject instanceof Polygon polygon) {
                    area = computeArea(polygon.getCoordinates());
                } else if (geoJsonObject instanceof MultiPolygon multiPolygon) {
                    area = 0;
                    for (List<List<Position>> polygon : multiPolygon.getCoordinates()) {
                        area += computeArea(polygon);
                    }
                }

                if (!Double.isNaN(area)) {
                    area = roundValue(area, DECIMAL_PLACES);
                    propertyValues.put(extendedPropertyName, area);
                }
            }
            catch(Exception e) {
                jaxRsDpsLog.error("Failed to compute area of " + extendedPropertyName, e);
            }
        }
        return propertyValues;
    }

    private double computeArea(List<List<Position>> polygon) {
        if(polygon == null || polygon.isEmpty()) {
            return 0;
        }

        List<Position> exteriorRing = polygon.get(0);
        // Compute the area without hole(s). The result value should be positive
        double area = computePolygonArea(exteriorRing, true);
        if(polygon.size() > 1) {
            for(List<Position> interiorRing : polygon.stream().skip(1).toList()) {
                // Compute the area of a hole. The return value should be negative
                area += computePolygonArea(interiorRing, false);
            }
        }
        return area;
    }

    private double computePolygonArea(List<Position> ring, boolean exterior) {
        if(!isClose(ring)) {
            return 0;
        }

        PolygonArea polygonArea = new PolygonArea(Geodesic.WGS84, false);
        for(Position position : ring) {
            polygonArea.AddPoint(position.getLatitude(), position.getLongitude());
        }
        // In general, 1) ring area is positive when the order of the points is counter-clockwise and
        // 2) it is negative when the order of the points is clockwise. PolygonArea follows the same rule.
        // However, there is no guarantee that the order of the points in OSDU Polygon model follows the same rule.
        double area = polygonArea.Compute().area;
        if((exterior && area < 0) || (!exterior && area > 0)) {
            area *= -1;
        }
        return area;
    }

    private boolean isClose(List<Position> ring) {
        if(CollectionUtils.isEmpty(ring) || ring.size() < 4) {
            return false;
        }

        Position first = ring.get(0);
        Position last = ring.get(ring.size() - 1);
        return first.getLongitude() == last.getLongitude() && first.getLatitude() == last.getLatitude();
    }
}
