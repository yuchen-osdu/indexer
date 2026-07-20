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
import net.sf.geographiclib.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LengthAugmenterImpl extends BaseShapeFunction {
    private static final String regex = "^Len\\s*\\(\\s*[\\w\\-\\.\\[\\]]+\\s*\\)$";
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
            double length = Double.NaN;
            GeoJsonObject geoJsonObject = geometryCollection.getGeometries().get(0);
            try {
                if (geoJsonObject instanceof LineString lineString) {
                    length = computePolylineLength(lineString.getCoordinates());
                } else if (geoJsonObject instanceof MultiLineString multiLineString) {
                    length = 0;
                    for (List<Position> line : multiLineString.getCoordinates()) {
                        length += computePolylineLength(line);
                    }
                }

                if (!Double.isNaN(length)) {
                    length = roundValue(length, DECIMAL_PLACES);
                    propertyValues.put(extendedPropertyName, length);
                }
            }
            catch (Exception e) {
                jaxRsDpsLog.error("Failed to compute length of " + extendedPropertyName, e);
            }
        }
        return propertyValues;
    }

    private double computePolylineLength(List<Position> line) {
        if(line.size() <= 1) {
            return 0;
        }

        double length = 0;
        for (int i = 0; i < line.size() -1; i++) {
            Position current = line.get(i);
            Position next = line.get(i + 1);
            GeodesicLine geodesicLine = Geodesic.WGS84.InverseLine(current.getLatitude(), current.getLongitude(), next.getLatitude(), next.getLongitude());
            length += geodesicLine.Distance();
        }

        return length;
    }
}
