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

package org.opengroup.osdu.indexer.model.geojson;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import org.opengroup.osdu.indexer.model.GeoJsonObject;
import org.opengroup.osdu.indexer.model.geojson.jackson.GeometryCollectionSerializer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
@JsonSerialize(using = GeometryCollectionSerializer.class)
public class GeometryCollection extends GeoJsonObject implements Iterable<GeoJsonObject> {

    private List<GeoJsonObject> geometries = new ArrayList<>();

    @Override
    public Iterator<GeoJsonObject> iterator() {
        return geometries.iterator();
    }

    public GeometryCollection add(GeoJsonObject geometry) {
        geometries.add(geometry);
        return this;
    }
}
