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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AugmenterConfiguration {
    // Version must be ended with dot and major version only
    // e.g. "Code": "osdu:wks:master-data--Well:1."
    private static final String KIND_WITH_MAJOR_VERSION_PATTERN = "[\\w\\-\\.]+:[\\w\\-\\.]+:[\\w\\-\\.]+:[0-9]+.$";

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Code")
    private String code;

    @JsonProperty("AttributionAuthority")
    private String attributionAuthority;

    @JsonProperty("Configurations")
    private List<PropertyConfiguration> configurations;

    public List<String> getUniqueRelatedObjectKinds() {
        if(configurations == null || configurations.isEmpty())
            return new ArrayList<>();

        Set<String> relatedObjectKinds = new HashSet<>();
        for(PropertyConfiguration configuration : configurations) {
            // In the same configuration, we can use any related object kind to
            // resolve the schema of the extended property in this configuration
            String relatedObjectKind = configuration.getFirstRelatedObjectKind();
            if(!Strings.isNullOrEmpty(relatedObjectKind)) {
                relatedObjectKinds.add(relatedObjectKind);
            }
        }
        return new ArrayList<>(relatedObjectKinds);
    }

    public boolean hasValidCode() {
        // It is just basic test to detect mistake
        if(Strings.isNullOrEmpty(this.code)) {
            return false;
        }

        try {
            Pattern pattern = Pattern.compile(KIND_WITH_MAJOR_VERSION_PATTERN);
            Matcher matcher = pattern.matcher(this.code);
            return matcher.find();
        }
        catch(PatternSyntaxException ex) {
            return false;
        }
    }

    public boolean hasValidConfigurations() {
        if(configurations == null || configurations.isEmpty()) {
            return false;
        }

        return configurations.stream().anyMatch(config -> config.isValid());
    }

    public boolean hasInvalidConfigurations() {
        if(configurations == null || configurations.isEmpty()) {
            return false;
        }

        return configurations.stream().anyMatch(config -> !config.isValid());
    }

    public boolean isValid() {
        return hasValidCode() && hasValidConfigurations();
    }
}
