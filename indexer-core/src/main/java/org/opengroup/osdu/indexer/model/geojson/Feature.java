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

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.opengroup.osdu.indexer.model.GeoJsonObject;

import java.util.HashMap;
import java.util.Map;

@Data
public class Feature extends GeoJsonObject {

    @JsonInclude()
    private Map<String, Object> properties = new HashMap<>();
    @JsonInclude()
    private GeoJsonObject geometry;
    private String id;

    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key) {
        return (T) properties.get(key);
    }
}
