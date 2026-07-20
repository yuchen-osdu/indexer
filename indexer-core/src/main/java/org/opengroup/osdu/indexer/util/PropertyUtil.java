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

package org.opengroup.osdu.indexer.util;

import com.google.api.client.util.Strings;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.opengroup.osdu.indexer.model.Kind;

import java.util.*;

public class PropertyUtil {
    public static final String DATA_VIRTUAL_DEFAULT_LOCATION = "data.VirtualProperties.DefaultLocation";
    public static final String VIRTUAL_DEFAULT_LOCATION = "VirtualProperties.DefaultLocation";
    public static final String FIELD_WGS84_COORDINATES = ".Wgs84Coordinates";
    public static final String VIRTUAL_DEFAULT_LOCATION_WGS84_PATH = VIRTUAL_DEFAULT_LOCATION + FIELD_WGS84_COORDINATES;
    public static final String VIRTUAL_DEFAULT_LOCATION_IS_DECIMATED_PATH = VIRTUAL_DEFAULT_LOCATION + ".IsDecimated";


    private static final String PROPERTY_DELIMITER = ".";
    private static final String NESTED_OBJECT_DELIMITER = "[].";
    private static final String ARRAY_SYMBOL = "[]";
    private static final String DATA_PREFIX = "data" + PROPERTY_DELIMITER;

    public static boolean isPropertyPathMatched(String propertyPath, String parentPropertyPath) {
        // We should not just use propertyPath.startsWith(parentPropertyPath)
        // For example, if parentPropertyPath is "data.FacilityName" and propertyPath.startsWith(parentPropertyPath) is used,
        // then the property "data.FacilityNameAlias" will be matched and unexpected result will be returned.
        return !Strings.isNullOrEmpty(propertyPath) && (propertyPath.startsWith(parentPropertyPath + PROPERTY_DELIMITER) || propertyPath.equals(parentPropertyPath));
    }

    public static Map<String, Object> getValueOfNoneNestedProperty(String propertyPath, Map<String, Object> data) {
        if(Strings.isNullOrEmpty(propertyPath) || propertyPath.contains(ARRAY_SYMBOL) || data == null || data.isEmpty())
            return new HashMap<>();

        Map<String, Object> values = new HashMap<>();
        if(data.containsKey(propertyPath)) {
           if(data.get(propertyPath) != null) {
               values.put(propertyPath, data.get(propertyPath));
           }
        }
        else {
            for (String key : data.keySet()) {
                if (propertyPath.startsWith(key + PROPERTY_DELIMITER)) {
                    Object v = data.get(key);
                    if (v instanceof Map) {
                        propertyPath = propertyPath.substring((key + PROPERTY_DELIMITER).length());
                        Map<String, Object> subPropertyValues = getValueOfNoneNestedProperty(propertyPath, (Map<String, Object>) v);
                        for (Map.Entry<String, Object> entry: subPropertyValues.entrySet()) {
                            values.put(key + PROPERTY_DELIMITER + entry.getKey(), entry.getValue());
                        }
                    }
                } else if (key.startsWith(propertyPath + PROPERTY_DELIMITER)) {
                    if(data.get(key) != null) {
                        values.put(key, data.get(key));
                    }
                }
            }
        }
        return values;
    }

    public static boolean hasSameMajorKind(String left, String right) {
        try {
            Kind leftKind = new Kind(left);
            Kind rightKind = new Kind(right);

            String[] leftVersions = leftKind.getVersion().split("\\.");
            String[] rightVersions = rightKind.getVersion().split("\\.");
            return leftKind.getAuthority().equals(rightKind.getAuthority()) &&
                    leftKind.getSource().equals(rightKind.getSource()) &&
                    leftKind.getType().equals(rightKind.getType()) &&
                    leftVersions[0].equals(rightVersions[0]);
        }
        catch(Exception ex) {
            // catch exception in case either left kind or right kind is an invalid kind
            return false;
        }
    }

    public static String removeDataPrefix(String path) {
        if (!Strings.isNullOrEmpty(path) && path.startsWith(DATA_PREFIX))
            return path.substring(DATA_PREFIX.length());
        return path;
    }

    public static String removeIdPostfix(String objectId) {
        if (objectId != null && objectId.endsWith(":")) {
            objectId = objectId.substring(0, objectId.length() - 1);
        }
        return objectId;
    }

    public static Map<String, Object> combineObjectMap(Map<String, Object> to, Map<String, Object> from) {
        if((to == null || to.isEmpty()) && (from == null || from.isEmpty())) {
            return new HashMap<>();
        }
        else if(to == null || to.isEmpty()) {
            return from;
        }
        else if(from == null || from.isEmpty()) {
            return to;
        }

        for (Map.Entry<String, Object> entry : from.entrySet()) {
            if (to.containsKey(entry.getKey())) {
                Object toObject = to.get(entry.getKey());
                Object fromObject = entry.getValue();
                if (toObject instanceof List && fromObject instanceof List) {
                    Set<Object> objectSet = new HashSet<>();
                    objectSet.addAll((List) toObject);
                    objectSet.addAll((List) fromObject);
                    List<Object> propertyValueList = new ArrayList<>(objectSet);
                    Collections.sort(propertyValueList, Comparator.comparing(Object::toString));
                    to.put(entry.getKey(), propertyValueList);
                }
                else if(toObject instanceof Map && fromObject instanceof Map) {
                    Object objectMap = combineObjectMap((Map<String, Object>) toObject, (Map<String, Object>) fromObject);
                    to.put(entry.getKey(), objectMap);
                }
                else if(!toObject.equals(fromObject)) {
                    if(toObject.getClass().equals(fromObject.getClass())) {
                        List<Object> propertyValueList = new ArrayList<>();
                        propertyValueList.add(toObject);
                        propertyValueList.add(fromObject);
                        Collections.sort(propertyValueList, Comparator.comparing(Object::toString));
                        to.put(entry.getKey(), propertyValueList);
                    }
                    else if(toObject instanceof List || fromObject instanceof List) {
                        List<Object> propertyValueList = toObject instanceof List? (List)toObject : (List)fromObject;
                        Object object = toObject instanceof List? fromObject : toObject;
                        if(!propertyValueList.isEmpty() && propertyValueList.get(0).getClass().equals(object.getClass())) {
                            propertyValueList.add(object);
                            Collections.sort(propertyValueList, Comparator.comparing(Object::toString));
                            to.put(entry.getKey(), propertyValueList);
                        }
                        else if(propertyValueList.isEmpty()) {
                            to.put(entry.getKey(), object);
                        }
                    }
                }
            } else {
                to.put(entry.getKey(), entry.getValue());
            }
        }

        return to;
    }

    public static Map<String, Object> replacePropertyPaths(String newPathPrefix, String valuePath, Map<String, Object> objectMap) {
        if(Strings.isNullOrEmpty(newPathPrefix) || Strings.isNullOrEmpty(valuePath) || objectMap == null || objectMap.isEmpty()) {
            return new HashMap<>();
        }

        newPathPrefix = removeDataPrefix(newPathPrefix);
        valuePath = removeDataPrefix(valuePath);

        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
            String key = entry.getKey();
            if (key.equals(valuePath) || key.startsWith(valuePath + PROPERTY_DELIMITER)) {
                key = key.replace(valuePath, newPathPrefix);
                values.put(key, entry.getValue());
            }
        }
        return values;
    }

    public static boolean isConcreteKind(String kind) {
        if(Strings.isNullOrEmpty(kind)) {
            return false;
        }

        String[] parts = kind.split(":");
        if(parts.length != 4)
            return false;
        String[] subVersions = parts[3].split("\\.");
        return (subVersions.length == 3);
    }

    public static String getKindWithMajor(String kind) {
        if(Strings.isNullOrEmpty(kind)) {
            return kind;
        }
        String[] parts = kind.split(":");
        if(parts.length != 4)
            return "";

        int index = kind.lastIndexOf(":");
        String[] subVersions = parts[3].split("\\.");
        String kindWithMajor = kind.substring(0, index) + ":" + subVersions[0] + ".";
        return kindWithMajor;
    }

    public static List<String> getChangedProperties(Map<String, Object> leftMap, Map<String, Object> rightMap) {
        if(leftMap == null && rightMap == null) {
            return new ArrayList<>();
        }

        if(leftMap == null) {
            leftMap = new HashMap<>();
        }
        if(rightMap == null) {
            rightMap = new HashMap<>();
        }
        // If leftMap does not have property A and rightMap has a property A with null value
        // Maps.difference will consider that leftMap and rightMap are different.
        // In our case, they are the same. The properties with null value should be removed
        // in the cloned map objects
        leftMap = removeNullValues(leftMap);
        rightMap = removeNullValues(rightMap);

        MapDifference<String, Object> difference = Maps.difference(leftMap, rightMap);
        if(difference.areEqual()) {
            return new ArrayList<>();
        }

        Set<String> changedProperties = new HashSet<>();
        if (difference.entriesOnlyOnLeft().size() > 0) {
            difference.entriesOnlyOnLeft().forEach((key, value) -> changedProperties.add(key));
        }
        if (difference.entriesOnlyOnRight().size() > 0) {
            difference.entriesOnlyOnRight().forEach((key, value) -> changedProperties.add(key));
        }
        if (difference.entriesDiffering().size() > 0) {
            for(Map.Entry<String, MapDifference.ValueDifference<Object>> entry : difference.entriesDiffering().entrySet()) {
                try {
                    MapDifference.ValueDifference<Object> valueDifference = entry.getValue();
                    Object left = valueDifference.leftValue();
                    Object right = valueDifference.rightValue();
                    if(left == null && right == null) {
                        //Same
                    }
                    else if(left == null || right == null) {
                        changedProperties.add(entry.getKey());
                    }
                    else if(left instanceof Map) {
                        Map<String, Object> innerLeftMap = (Map<String, Object>)left;
                        Map<String, Object> innerRightMap = (Map<String, Object>)right;
                        List<String> nestedChangedProperties = getChangedProperties(innerLeftMap, innerRightMap);
                        for (String nestedProperty: nestedChangedProperties) {
                            String p = entry.getKey() + PROPERTY_DELIMITER + nestedProperty;
                            changedProperties.add(p);
                        }
                    }
                    else if(left instanceof List) {
                        List<Object> innerLeftList = (List<Object>)left;
                        List<Object> innerRightList = (List<Object>)right;
                        if(innerLeftList.size() != innerRightList.size()) {
                            String p = entry.getKey() + ARRAY_SYMBOL;
                            changedProperties.add(p);
                        }
                        else {
                            for(int i = 0; i < innerLeftList.size(); i++) {
                                Map<String, Object> innerLeftMap = (Map<String, Object>)innerLeftList.get(i);
                                Map<String, Object> innerRightMap = (Map<String, Object>)innerRightList.get(i);
                                List<String> nestedChangedProperties = getChangedProperties(innerLeftMap, innerRightMap);
                                for (String nestedProperty: nestedChangedProperties) {
                                    String p = entry.getKey() + NESTED_OBJECT_DELIMITER + nestedProperty;
                                    changedProperties.add(p);
                                }
                            }
                        }
                    }
                    else {
                        // Special handle of number. The integer value from the search result could be converted to double
                        if(left instanceof Double || right instanceof Double) {
                            double leftValue = Double.parseDouble(left.toString());
                            double rightValue = Double.parseDouble(right.toString());
                            if(Double.compare(leftValue, rightValue) == 0) {
                                continue; // The left/right values are the same in this case
                            }
                        }
                        changedProperties.add(entry.getKey());
                    }
                }
                catch (Exception ex) {
                    // assume there is difference in this case
                    changedProperties.add(entry.getKey());
                }
            }
        }

        return new ArrayList<>(changedProperties);
    }

    private static Map<String, Object> removeNullValues(Map<String, Object> dataMap) {
        // The original object should not be changed
        dataMap = new HashMap<>(dataMap);
        dataMap.entrySet().removeIf(entry -> entry.getValue() == null);
        return dataMap;
    }
}
