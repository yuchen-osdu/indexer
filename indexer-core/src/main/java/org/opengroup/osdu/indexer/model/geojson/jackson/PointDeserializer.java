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

package org.opengroup.osdu.indexer.model.geojson.jackson;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.opengroup.osdu.indexer.model.geojson.Point;
import org.opengroup.osdu.indexer.model.geojson.Position;
import java.io.IOException;

public class PointDeserializer extends JsonDeserializer<Point> {
    @Override
    public Point deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        ObjectCodec codec = jsonParser.getCodec();
        JsonNode coordinatesNode = codec.readTree(jsonParser);
        JsonNode positionNode = coordinatesNode.get(GeoJsonConstants.COORDINATES);
        if(positionNode == null) {
            throw new JsonParseException(jsonParser, "Missing coordinates field in the point");
        }

        Position position = codec.treeToValue(positionNode, Position.class);
        if(position.hasAltitude())
            return  new Point(position.getLongitude(), position.getLatitude(), position.getAltitude());
        else
            return  new Point(position.getLongitude(), position.getLatitude());
    }
}
