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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.Map;

public class Definitions {
    private Map<String, Definition> items = new HashMap<>();

    public Definition getDefinition(String name) {
        return items.get(name);
    }

    @JsonAnySetter
    public void add(String key, Definition value) {
        items.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Definition> getProperties() {
        return items;
    }
}
