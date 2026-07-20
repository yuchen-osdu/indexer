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
package org.opengroup.osdu.models.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.StringJoiner;

public class SchemaIdentity {

    private  String authority;
    private  String source;
    private  String entityType;
    private String schemaVersionMajor;
    private String schemaVersionMinor;
    private String schemaVersionPatch;

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getSchemaVersionMajor() {
        return schemaVersionMajor;
    }

    public void setSchemaVersionMajor(String schemaVersionMajor) {
        this.schemaVersionMajor = schemaVersionMajor;
    }

    public String getSchemaVersionMinor() {
        return schemaVersionMinor;
    }

    public void setSchemaVersionMinor(String schemaVersionMinor) {
        this.schemaVersionMinor = schemaVersionMinor;
    }

    public String getSchemaVersionPatch() {
        return schemaVersionPatch;
    }

    public void setSchemaVersionPatch(String schemaVersionPatch) {
        this.schemaVersionPatch = schemaVersionPatch;
    }

    @JsonIgnore
    public String getId() {
        return authority + ":" + source + ":" + entityType + ":" +
                schemaVersionMajor + "." + schemaVersionMinor + "." + schemaVersionPatch;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SchemaIdentity.class.getSimpleName() + "[", "]")
                .add("AUTHORITY='" + authority + "'")
                .add("SOURCE='" + source + "'")
                .add("ENTITY_TYPE='" + entityType + "'")
                .add("schemaVersionMajor='" + schemaVersionMajor + "'")
                .add("schemaVersionMinor='" + schemaVersionMinor + "'")
                .add("schemaVersionPatch='" + schemaVersionPatch + "'")
                .toString();
    }
}
