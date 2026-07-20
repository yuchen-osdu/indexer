/*
 * Copyright 2017-2025, The Open Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.request;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class SpatialFilter {
    String field;
    ByBoundingBox byBoundingBox;
    ByDistance byDistance;
    ByGeoPolygon byGeoPolygon;

    @Builder
    public static class ByDistance {
        Coordinates point;
        int distance;
    }

    @Builder
    public static class ByBoundingBox {
        Coordinates topLeft;
        Coordinates bottomRight;
    }

    @Builder
    public static class Coordinates {
        Double latitude;
        Double longitude;
    }

    @Builder
    public static class ByGeoPolygon {
        List<Coordinates> points;
    }
}