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

package org.opengroup.osdu.indexer.model;

import lombok.Getter;

@Getter
public class Kind {

    private String kind;
    private String authority;
    private String source;
    private String type;
    private String version;

    public Kind(String kind) {
        this.kind = kind;
        String[] parts = this.kind.split(":");

        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid Kind, must be in format: authority:source:type:version");
        }

        this.authority = parts[0];
        this.source = parts[1];
        this.type = parts[2];
        this.version = parts[3];
    }
}
