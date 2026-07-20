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

package org.opengroup.osdu.indexer.model.indexproperty;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.api.client.util.Strings;
import lombok.Data;
import lombok.ToString;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.indexer.util.PropertyUtil;

import jakarta.inject.Inject;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelatedCondition {
    protected static final String ARRAY_SYMBOL = "[]";

    protected String relatedConditionProperty;

    protected List<String> relatedConditionMatches;

    @Inject
    private JaxRsDpsLog jaxRsDpsLog;

    public boolean isMatch(String propertyValue) {
        if(relatedConditionMatches == null || relatedConditionMatches.isEmpty() || Strings.isNullOrEmpty(propertyValue)) {
            return false;
        }

        for(String condition : relatedConditionMatches) {
            try {
                Pattern pattern = Pattern.compile(condition);
                Matcher matcher = pattern.matcher(propertyValue);
                if(matcher.find())
                    return true;
            }
            catch(PatternSyntaxException ex) {
                this.jaxRsDpsLog.debug(String.format("%s is not a valid regular expression: error: %s", condition, ex.getMessage()));
            }
        }

        return false;
    }

    protected boolean hasCondition() {
        return !Strings.isNullOrEmpty(relatedConditionProperty) &&
               relatedConditionMatches != null &&
               !relatedConditionMatches.isEmpty();
    }

    protected boolean hasValidCondition(String property) {
        if(Strings.isNullOrEmpty(property) || !this.hasCondition())
            return false;

        // If it is not nested object, it is valid in terms of syntax.
        if(!property.contains(ARRAY_SYMBOL) && !relatedConditionProperty.contains(ARRAY_SYMBOL))
            return true;

        return hasMatchNestedParts(property);
    }

    private boolean hasMatchNestedParts(String property) {
        if((property.endsWith(ARRAY_SYMBOL) || relatedConditionProperty.endsWith(ARRAY_SYMBOL)) ||
           (property.contains(ARRAY_SYMBOL) && !relatedConditionProperty.contains(ARRAY_SYMBOL)) ||
           (!property.contains(ARRAY_SYMBOL) && relatedConditionProperty.contains(ARRAY_SYMBOL)))
            return false;

        property = this.getSubstringWithLastArrayField(
                PropertyUtil.removeDataPrefix(property));
        String conditionProperty = this.getSubstringWithLastArrayField(
                PropertyUtil.removeDataPrefix(relatedConditionProperty));

        String delimiter = "\\.";
        String[] propertyParts = property.split(delimiter);
        String[] relatedConditionPropertyParts = conditionProperty.split(delimiter);
        if(propertyParts.length != relatedConditionPropertyParts.length)
            return false;

        for(int i = 0; i < propertyParts.length; i++) {
            if(!propertyParts[i].equals(relatedConditionPropertyParts[i]))
                return false;
        }
        return true;
    }


    private String getSubstringWithLastArrayField(String property) {
        return property.substring(0, property.lastIndexOf(ARRAY_SYMBOL));
    }
}
