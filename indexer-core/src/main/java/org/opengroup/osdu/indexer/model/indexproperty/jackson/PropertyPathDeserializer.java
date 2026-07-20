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

package org.opengroup.osdu.indexer.model.indexproperty.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.opengroup.osdu.indexer.model.indexproperty.PropertyPath;
import org.opengroup.osdu.indexer.model.indexproperty.RelatedCondition;
import org.opengroup.osdu.indexer.model.indexproperty.RelatedObjectsSpec;
import org.opengroup.osdu.indexer.model.indexproperty.ValueExtraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PropertyPathDeserializer extends JsonDeserializer<PropertyPath> {
    private final String RELATED_OBJECTS_SPEC_RELATIONSHIP_DIRECTION = "RelatedObjectsSpec.RelationshipDirection";
    private final String RELATED_OBJECTS_SPEC_RELATED_OBJECT_KIND = "RelatedObjectsSpec.RelatedObjectKind";
    private final String RELATED_OBJECTS_SPEC_RELATED_OBJECT_ID = "RelatedObjectsSpec.RelatedObjectID";
    private final String RELATED_OBJECTS_SPEC_RELATED_CONDITION_PROPERTY = "RelatedObjectsSpec.RelatedConditionProperty";
    private final String RELATED_OBJECTS_SPEC_RELATED_CONDITION_MATCHES = "RelatedObjectsSpec.RelatedConditionMatches";

    private final String VALUE_EXTRACTION_VALUE_PATH = "ValueExtraction.ValuePath";
    private final String VALUE_EXTRACTION_RELATED_CONDITION_PROPERTY = "ValueExtraction.RelatedConditionProperty";
    private final String VALUE_EXTRACTION_RELATED_CONDITION_MATCHES = "ValueExtraction.RelatedConditionMatches";

    @Override
    public PropertyPath deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        PropertyPath propertyPath = new PropertyPath();
        ObjectCodec codec = jsonParser.getCodec();
        JsonNode node = codec.readTree(jsonParser);

        RelatedObjectsSpec relatedObjectsSpec = deserializeRelatedObjects(node);
        if(relatedObjectsSpec != null) {
            propertyPath.setRelatedObjectsSpec(relatedObjectsSpec);
        }
        ValueExtraction valueExtraction = deserializeValueExtraction(node);
        if(valueExtraction != null) {
            propertyPath.setValueExtraction(valueExtraction);
        }
        return propertyPath;
    }

    private RelatedObjectsSpec deserializeRelatedObjects(JsonNode node) {
        JsonNode relationshipDirection = node.get(RELATED_OBJECTS_SPEC_RELATIONSHIP_DIRECTION);
        JsonNode relatedObjectKind = node.get(RELATED_OBJECTS_SPEC_RELATED_OBJECT_KIND);
        JsonNode relatedObjectID = node.get(RELATED_OBJECTS_SPEC_RELATED_OBJECT_ID);
        JsonNode relatedConditionProperty = node.get(RELATED_OBJECTS_SPEC_RELATED_CONDITION_PROPERTY);
        JsonNode relatedConditionMatches = node.get(RELATED_OBJECTS_SPEC_RELATED_CONDITION_MATCHES);

        if(isNotNull(relationshipDirection) ||
                isNotNull(relatedObjectKind) ||
                isNotNull(relatedObjectID) ||
                isNotNull(relatedConditionProperty) ||
                isNotNull(relatedConditionMatches)) {
            RelatedObjectsSpec relatedObjectsSpec = new RelatedObjectsSpec();
            if(isNotNull(relationshipDirection)) {
                relatedObjectsSpec.setRelationshipDirection(relationshipDirection.asText());
            }
            if(isNotNull(relatedObjectKind)) {
                relatedObjectsSpec.setRelatedObjectKind(relatedObjectKind.asText());
            }
            if(isNotNull(relatedObjectID)) {
                relatedObjectsSpec.setRelatedObjectID(relatedObjectID.asText());
            }
            setRelatedCondition(relatedObjectsSpec, relatedConditionProperty, relatedConditionMatches);
            return relatedObjectsSpec;
        }

        return null;
    }

    private ValueExtraction deserializeValueExtraction(JsonNode node) {
        JsonNode valuePath = node.get(VALUE_EXTRACTION_VALUE_PATH);
        JsonNode relatedConditionProperty = node.get(VALUE_EXTRACTION_RELATED_CONDITION_PROPERTY);
        JsonNode relatedConditionMatches = node.get(VALUE_EXTRACTION_RELATED_CONDITION_MATCHES);

        if(isNotNull(valuePath) ||
                isNotNull(relatedConditionProperty) ||
                isNotNull(relatedConditionMatches)) {
            ValueExtraction valueExtraction = new ValueExtraction();
            if(isNotNull(valuePath)) {
                valueExtraction.setValuePath(valuePath.asText());
            }
            setRelatedCondition(valueExtraction, relatedConditionProperty, relatedConditionMatches);
            return valueExtraction;
        }

        return null;
    }

    private void setRelatedCondition(RelatedCondition relatedCondition, JsonNode relatedConditionProperty, JsonNode relatedConditionMatches) {
        if(isNotNull(relatedConditionProperty)) {
            relatedCondition.setRelatedConditionProperty(relatedConditionProperty.asText());
        }
        if(isNotNull(relatedConditionMatches) && relatedConditionMatches.isArray()) {
            List<String> conditionMatches = new ArrayList<>();
            for (JsonNode subNode : relatedConditionMatches) {
                conditionMatches.add(subNode.asText());
            }
            relatedCondition.setRelatedConditionMatches(conditionMatches);
        }
    }

    private boolean isNotNull(JsonNode node) {
        return node != null && !node.isNull();
    }
}
