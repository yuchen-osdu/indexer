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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class JsonPathMatcher {

    // Since there's no unit tests for tests, there's this:
    public static void main(String[] args) {
        String jsonGoodString = """
{       "id": "tenant1:reference-data--IndexPropertyPathConfiguration:index-property--Wellbore:1.",
        "data": {
            "Name": "Wellbore-IndexPropertyPathConfiguration",
            "Description": "The index property list for index-property--Wellbore:1., valid for all index-property--Wellbore kinds for major version 1.",
            "Code": "test:indexer:index-property--Wellbore:1.",
            "AttributionAuthority": "OSDU",
            "Configurations": [{
                    "Name": "WellUWI",
                    "Policy": "ExtractFirstMatch",
                    "Paths": [{
                            "ValueExtraction.RelatedConditionMatches": [
                                "UniqueIdentifier:$",
                                "RegulatoryName:$",
                                "PreferredName:$",
                                "CommonName:$",
                                "ShortName:$"
                            ],
                            "ValueExtraction.RelatedConditionProperty": "data.NameAliases[].AliasNameTypeID",
                            "ValueExtraction.ValuePath": "data.NameAliases[].AliasName"
                            
                        }
                    ],
                    "UseCase": "As a user I want to discover and match Wells by their UWI. I am aware that this is not globally reliable, however, I am able to specify a prioritized AliasNameType list to look up value in the NameAliases array."
                }
            ]
        }
 }
 """;
        String jsonBadString = """
{       "id": "tenant1:reference-data--IndexPropertyPathConfiguration:index-property--Wellbore:1.",
        "data": {
            "Name": "Wellbore-IndexPropertyPathConfiguration",
            "Description": "The index property list for index-property--Wellbore:1., valid for all index-property--Wellbore kinds for major version 1.",
            "Code": "test:indexer:index-property--Wellbore:1.",
            "AttributionAuthority": "OSDU",
            "Configurations": [{
                    "Name": "WellUWI",
                    "Policy": "ExtractFirstMatch",
                    "Paths": [{
                            "ValueExtraction": {
                                "RelatedConditionMatches": "[UniqueIdentifier:$,RegulatoryName:$,PreferredName:$]",
                                "RelatedConditionProperty": "data.NameAliases[].AliasNameTypeID",
                                "ValuePath": "data.NameAliases[].AliasName"
                            }
                        }
                    ],
                    "UseCase": "As a user I want to discover and match Wells by their UWI. I am aware that this is not globally reliable, however, I am able to specify a prioritized AliasNameType list to look up value in the NameAliases array."
                }
            ]
        }
 }
 """;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> dataMap = objectMapper.readValue(jsonGoodString, Map.class);
            List<String> stringList = java.util.Arrays.asList("data.Configurations.Paths.ValueExtraction.RelatedConditionMatches".split("\\."));
            Object found = FindArrayInJson(dataMap, stringList);
            System.out.println("in Good String Found? "+ objectMapper.writeValueAsString(found));
            dataMap = objectMapper.readValue(jsonBadString, Map.class);
            found = FindArrayInJson(dataMap, stringList);
            System.out.println("in Bad String Found? "+ objectMapper.writeValueAsString(found));
            stringList = java.util.Arrays.asList("data.Configurations.Paths.ValueExtraction.SomeDifferentKey".split("\\."));
            found = FindArrayInJson(dataMap, stringList);
            System.out.println("in Bad String Found? "+ objectMapper.writeValueAsString(found));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Assure that field in the Json path (supporting flattened fields) is an array of strings
    public static Object FindArrayInJson(Object data, List<String> stringList) {
        if (data instanceof Map) {
            // Handle Map
            return handleMap((Map<String, Object>) data, stringList);
        } else if (data instanceof ArrayList) {
            // Handle ArrayList
            return handleArrayList((ArrayList<?>) data, stringList);
        } else {
            // Handle other types
            return null;
        }
    }

    private static boolean isPrefix(List<String> potentialPrefix, List<String> target) {
        boolean result = true;
        for (Integer i = 0; i < potentialPrefix.size(); i++) {
            if (!potentialPrefix.get(i).equals(target.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static Object handleMap(Map<String, Object> map, List<String> stringList) {

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            List<String> keyParts = java.util.Arrays.asList(entry.getKey().split("\\."));
            if (keyParts.size() > stringList.size()) {
                if (!isPrefix(keyParts.subList(0, stringList.size()), stringList)) {
                    continue;
                }
                Object result = FindArrayInJson(value, stringList.subList(0, 0));
                if (result != null) { return result; }
            } else {
                if (!isPrefix(keyParts, stringList)) {
                    continue;
                }
                Object result = FindArrayInJson(value, stringList.subList(keyParts.size(), stringList.size()));
                if (result != null) { return result; }
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
            Object result = FindArrayInJson(arrayElementValue, stringList);
            if (result != null) { return result; }
        }
        return null;
    }
}
