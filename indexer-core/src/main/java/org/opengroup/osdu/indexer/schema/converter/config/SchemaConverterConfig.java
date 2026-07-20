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

package org.opengroup.osdu.indexer.schema.converter.config;

import java.util.Map;
import java.util.Set;

/*
Provides configuration for the schema converter
 */
public interface SchemaConverterConfig {
    Set<String> getSkippedDefinitions();
    Set<String> getSupportedArrayTypes();
    Map<String, String> getSpecialDefinitionsMap();
    Map<String, String> getPrimitiveTypesMap();
    Set<String> getProcessedArraysTypes();
    String getDefaultObjectArraysType();
}
