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

package org.opengroup.osdu.indexer.util.function;

import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.indexer.model.indexproperty.ValueExtraction;

import java.util.List;
import java.util.Map;

public interface IAugmenterFunction {
    boolean isMatched(ValueExtraction valueExtraction);
    List<String> getValuePaths(ValueExtraction valueExtraction);
    List<SchemaItem> getExtendedSchemaItems(String extendedPropertyName);
    Map<String, Object> getPropertyValues(String extendedPropertyName, ValueExtraction valueExtraction, Map<String, Object> originalPropertyValues);
}
