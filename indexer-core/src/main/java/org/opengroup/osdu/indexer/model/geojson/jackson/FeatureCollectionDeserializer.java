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

package org.opengroup.osdu.indexer.model.geojson.jackson;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.opengroup.osdu.indexer.model.geojson.Feature;
import org.opengroup.osdu.indexer.model.geojson.FeatureCollection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FeatureCollectionDeserializer extends JsonDeserializer<FeatureCollection> {

    @Override
    public FeatureCollection deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        FeatureCollection featureCollection = new FeatureCollection();
        featureCollection.setFeatures(extractFeature(jsonParser));
        return featureCollection;
    }

    private List<Feature> extractFeature(JsonParser jsonParser) throws IOException {
        ObjectCodec codec = jsonParser.getCodec();
        JsonNode featureCollection = codec.readTree(jsonParser);
        JsonNode features = featureCollection.get(GeoJsonConstants.FEATURES);

        if(features == null){
            throw new JsonParseException(jsonParser, "Missing feature field in the ");
        }

        final List<Feature> result = new ArrayList<>();
        for (JsonNode node : features) {
            result.add(codec.treeToValue(node, Feature.class));
        }
        return result;
    }
}
