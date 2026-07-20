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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.opengroup.osdu.indexer.model.geojson.Position;

public class MultiLineStringDeserializer extends JsonDeserializer<List<List<Position>>> {

    @Override
    public List<List<Position>> deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        List<List<Position>> multiLineStringCoordinatesList = new ArrayList<>();
        ObjectCodec codec = jsonParser.getCodec();
        JsonNode coordinatesNode = codec.readTree(jsonParser);
        if (coordinatesNode.isArray()) {
            for (JsonNode jsonNode : coordinatesNode) {
                ArrayList<Position> positionsList = new ArrayList<>();
                for (JsonNode positionNode : jsonNode) {
                    positionsList.add(codec.treeToValue(positionNode, Position.class));
                }
                multiLineStringCoordinatesList.add(positionsList);
            }
        }
        return multiLineStringCoordinatesList;
    }
}
