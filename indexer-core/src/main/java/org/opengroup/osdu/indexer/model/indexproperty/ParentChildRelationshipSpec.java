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

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParentChildRelationshipSpec {
    private String parentKind;
    private String parentObjectIdPath;
    private String childKind;
    private List<String> childValuePaths;

    public ParentChildRelationshipSpec() {
        childValuePaths = new ArrayList<>();
    }

    @Override
    public boolean equals(Object another) {
        if(another == null || !(another instanceof ParentChildRelationshipSpec))
            return false;

        ParentChildRelationshipSpec anotherSpec = (ParentChildRelationshipSpec)another;
        return this.toString().equals(anotherSpec.toString());
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append((parentKind != null)? parentKind : "__");
        stringBuilder.append("<>");
        stringBuilder.append((childKind != null)? childKind : "__");
        stringBuilder.append("<>");
        stringBuilder.append((parentObjectIdPath != null)? parentObjectIdPath : "__");
        return stringBuilder.toString();
    }
}
