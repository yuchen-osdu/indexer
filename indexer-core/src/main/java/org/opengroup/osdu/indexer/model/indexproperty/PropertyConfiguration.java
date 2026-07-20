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
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;
import org.opengroup.osdu.indexer.util.PropertyUtil;

import java.util.List;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyConfiguration {
    private final String EXTRACT_FIRST_MATCH_POLICY = "ExtractFirstMatch";
    private final String EXTRACT_ALL_MATCHES_POLICY = "ExtractAllMatches";

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Policy")
    private String policy;

    @JsonProperty("UseCase")
    private String useCase;

    @JsonProperty("Paths")
    private List<PropertyPath> paths;

    public String getExtendedPropertyName() {
        return PropertyUtil.removeDataPrefix(this.name);
    }

    public boolean isExtractFirstMatch() {
        return EXTRACT_FIRST_MATCH_POLICY.equalsIgnoreCase(policy);
    }

    public boolean isExtractAllMatches() {
        return EXTRACT_ALL_MATCHES_POLICY.equalsIgnoreCase(policy);
    }

    public boolean isValid() {
        boolean hasValidPath = (paths != null && paths.stream().filter(p -> p.isValid()).findFirst().orElse(null) != null);
        return hasValidPath && (isExtractFirstMatch() || isExtractAllMatches());
    }

    /**
     * Though one extended property can be mapped to a property from different kinds of source objects, we assume
     * that all those source properties should have the same schema.
     * @return
     */
    public String getFirstRelatedObjectKind() {
        if(paths != null) {
            for (PropertyPath path : paths) {
                if (path.isValid() && path.hasValidRelatedObjectsSpec()) {
                    return path.getRelatedObjectsSpec().getRelatedObjectKind();
                }
            }
        }
        return null;
    }
}
