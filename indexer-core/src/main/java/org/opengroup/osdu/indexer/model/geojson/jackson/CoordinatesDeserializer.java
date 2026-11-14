/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.indexer.model.geojson.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.opengroup.osdu.indexer.model.geojson.Position;

public class CoordinatesDeserializer extends ValueDeserializer<List<Position>> {

    @Override
    public List<Position> deserialize(JsonParser jsonParser, DeserializationContext ctxt) {
        ArrayList<Position> positions = new ArrayList<>();
        JsonNode positionsNode = ctxt.readTree(jsonParser);
        if (positionsNode.isArray()) {
            for (JsonNode node : positionsNode) {
                positions.add(ctxt.readTreeAsValue(node, Position.class));
            }
        }
        return positions;
    }
}
