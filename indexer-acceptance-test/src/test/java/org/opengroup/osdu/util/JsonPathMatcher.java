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

package org.opengroup.osdu.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Utility for traversing nested JSON structures to find array values at a dotted path.
 *
 * <p>Supports flattened field names (where a single key like
 * {@code "data.Configurations.Paths.ValueExtraction.RelatedConditionMatches"} appears as a
 * map key rather than nested maps), which is how Elasticsearch stores certain properties.
 */
public class JsonPathMatcher {

    private JsonPathMatcher() {
    }

    /**
     * Traverses a parsed JSON structure following the given path segments and returns the first
     * array value found at the path, or {@code null} when not found.
     *
     * <p>Handles both nested maps and flattened field names. Array elements are traversed
     * recursively.
     *
     * @param data       the root object (typically a {@code Map<String, Object>} parsed from JSON)
     * @param stringList path segments (for example from splitting {@code "data.foo.bar"} on
     *                   {@code "\\."})
     * @return the array value at the path, or {@code null} when not found
     */
    public static Object findArrayInJson(Object data, List<String> stringList) {
        if (data instanceof Map) {
            return handleMap((Map<String, Object>) data, stringList);
        } else if (data instanceof ArrayList) {
            return handleArrayList((ArrayList<?>) data, stringList);
        }
        return null;
    }

    private static boolean isPrefix(List<String> potentialPrefix, List<String> target) {
        for (int i = 0; i < potentialPrefix.size(); i++) {
            if (!potentialPrefix.get(i).equals(target.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static Object handleMap(Map<String, Object> map, List<String> stringList) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            List<String> keyParts = Arrays.asList(entry.getKey().split("\\."));
            if (keyParts.size() > stringList.size()) {
                if (!isPrefix(keyParts.subList(0, stringList.size()), stringList)) {
                    continue;
                }
                Object result = findArrayInJson(value, stringList.subList(0, 0));
                if (result != null) {
                    return result;
                }
            } else {
                if (!isPrefix(keyParts, stringList)) {
                    continue;
                }
                Object result = findArrayInJson(value, stringList.subList(keyParts.size(), stringList.size()));
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static Object handleArrayList(ArrayList<?> arrayList, List<String> stringList) {
        if (stringList.isEmpty()) return arrayList;
        if (arrayList.isEmpty()) {
            return null;
        }
        for (Object arrayElementValue : arrayList) {
            Object result = findArrayInJson(arrayElementValue, stringList);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
