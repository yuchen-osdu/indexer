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

package org.opengroup.osdu.indexer.util.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import java.util.Map;
import org.opengroup.osdu.core.common.search.Preconditions;
import org.opengroup.osdu.indexer.model.geojson.FeatureCollection;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class GeoShapeParser {

    private ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> parseGeoJson(Map<String, Object> objectMap) {

        Preconditions.checkNotNull(objectMap, "geoShapeObject cannot be null");
        if (objectMap.isEmpty()) throw new IllegalArgumentException("shape not included");

        try {
            FeatureCollection collection = mapper.readValue(mapper.writeValueAsString(objectMap), FeatureCollection.class);
            return mapper.readValue(mapper.writeValueAsString(collection), new TypeReference<Map<String, Object>>() {
            });
        } catch (InvalidTypeIdException e) {
            throw new IllegalArgumentException("must be a valid FeatureCollection");
        } catch (JsonProcessingException e){
            throw new IllegalArgumentException(e.getOriginalMessage());
        }
    }
}
