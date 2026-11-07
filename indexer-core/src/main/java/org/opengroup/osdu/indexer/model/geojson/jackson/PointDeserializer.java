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

import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.JsonNode;
import org.opengroup.osdu.indexer.model.geojson.Point;
import org.opengroup.osdu.indexer.model.geojson.Position;

public class PointDeserializer extends ValueDeserializer<Point> {
    @Override
    public Point deserialize(JsonParser jsonParser, DeserializationContext ctxt) {
        JsonNode coordinatesNode = ctxt.readTree(jsonParser);
        JsonNode positionNode = coordinatesNode.get(GeoJsonConstants.COORDINATES);
        if(positionNode == null) {
            throw new StreamReadException(jsonParser, "Missing coordinates field in the point");
        }

        Position position = ctxt.readTreeAsValue(positionNode, Position.class);
        if(position.hasAltitude())
            return  new Point(position.getLongitude(), position.getLatitude(), position.getAltitude());
        else
            return  new Point(position.getLongitude(), position.getLatitude());
    }
}
