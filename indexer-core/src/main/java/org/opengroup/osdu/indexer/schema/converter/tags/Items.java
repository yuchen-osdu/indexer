// Copyright 2017-2020, Schlumberger
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

package org.opengroup.osdu.indexer.schema.converter.tags;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Data;

@Data
public class Items {
    @JsonProperty("$ref")
    private String ref;
    private List<AllOfItem> allOf;
    private String type;
    private String pattern;
    private Map<String, TypeProperty> properties;

    public boolean isComplexTypeItems(){
        return Objects.nonNull(ref) || Objects.nonNull(allOf) || Objects.nonNull(properties);
    }

}
