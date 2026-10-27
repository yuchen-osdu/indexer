/*
  Copyright 2026 Google LLC
  Copyright 2026 EPAM Systems, Inc

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.opengroup.osdu.indexer.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.indexer.model.RecordChangeInfo;
import org.opengroup.osdu.indexer.model.indexproperty.ChildrenKinds;
import org.opengroup.osdu.indexer.model.indexproperty.ParentChildRelationshipSpec;
import org.opengroup.osdu.indexer.model.indexproperty.ParentChildRelationshipSpecs;
import org.opengroup.osdu.indexer.util.PropertyUtil;

class AugmenterSpecAndDataRedisCacheTest {

    private final Map<String, String> store = new HashMap<>();
    private RedisCache<String, String> redisCache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisCache = mock(RedisCache.class);
        lenient().doAnswer(invocation -> store.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(redisCache).put(anyString(), anyString());
        lenient().doAnswer(invocation -> store.get(invocation.<String>getArgument(0)))
                .when(redisCache).get(anyString());
    }

    @Test
    void relatedObjectCacheShouldKeepValueTypes() {
        RelatedObjectRedisCache cache = new RelatedObjectRedisCache(redisCache);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("Name", "Facility_123");
        data.put("Count", 5);
        data.put("Big", 9007199254740993L);
        data.put("Depth", 12021.88);
        data.put("Active", true);
        data.put("Location", Map.of("lat", 29.17064, "lon", -95.002875));
        data.put("NameAliases", List.of(Map.of("AliasName", "123454321", "Order", 1)));
        RecordData recordData = new RecordData();
        recordData.setData(data);

        cache.put("id", recordData);
        RecordData cached = cache.get("id");

        assertEquals(data, cached.getData());
        // Change detection compares with equals; a type change would report unchanged properties as changed
        assertTrue(PropertyUtil.getChangedProperties(data, cached.getData()).isEmpty());
    }

    @Test
    void recordChangeInfoCacheShouldRoundTrip() {
        RecordChangeInfoRedisCache cache = new RecordChangeInfoRedisCache(redisCache);
        RecordInfo recordInfo = new RecordInfo();
        recordInfo.setId("osdu:wellbore:1");
        recordInfo.setKind("osdu:wks:master-data--Wellbore:1.0.0");
        recordInfo.setOp(OperationType.update.getValue());
        RecordChangeInfo changeInfo = new RecordChangeInfo();
        changeInfo.setRecordInfo(recordInfo);
        changeInfo.setUpdatedProperties(new ArrayList<>(List.of("FacilityName", "Location")));

        cache.put("id", changeInfo);

        assertEquals(changeInfo, cache.get("id"));
    }

    @Test
    void childrenKindsCacheShouldRoundTrip() {
        ChildrenKindsRedisCache cache = new ChildrenKindsRedisCache(redisCache);
        ChildrenKinds childrenKinds = new ChildrenKinds();
        childrenKinds.setKinds(List.of("osdu:wks:work-product-component--WellLog:1."));

        cache.put("osdu:wks:master-data--Wellbore:1.", childrenKinds);

        assertEquals(childrenKinds, cache.get("osdu:wks:master-data--Wellbore:1."));
    }

    @Test
    void parentChildRelationshipSpecsCacheShouldRoundTrip() {
        ParentChildRelationshipSpecsRedisCache cache = new ParentChildRelationshipSpecsRedisCache(redisCache);
        ParentChildRelationshipSpec spec = new ParentChildRelationshipSpec();
        spec.setParentKind("osdu:wks:master-data--Wellbore:1.");
        spec.setParentObjectIdPath("data.WellboreID");
        spec.setChildKind("osdu:wks:work-product-component--WellLog:1.");
        spec.setChildValuePaths(new ArrayList<>(Arrays.asList("data.FacilityName", "data.Location")));
        ParentChildRelationshipSpecs specs = new ParentChildRelationshipSpecs();
        specs.setSpecList(List.of(spec));

        cache.put("osdu:wks:work-product-component--WellLog:1.", specs);

        assertEquals(specs, cache.get("osdu:wks:work-product-component--WellLog:1."));
    }

    @Test
    void kindCacheShouldStoreKindAsIs() {
        KindRedisCache cache = new KindRedisCache(redisCache);

        cache.put("osdu:wks:master-data--Wellbore:1.", "osdu:wks:master-data--Wellbore:1.2.0");

        assertEquals("osdu:wks:master-data--Wellbore:1.2.0", store.get("osdu:wks:master-data--Wellbore:1."));
        assertEquals("osdu:wks:master-data--Wellbore:1.2.0", cache.get("osdu:wks:master-data--Wellbore:1."));
    }

    @Test
    void missingKeysShouldReturnNull() {
        assertNull(new RelatedObjectRedisCache(redisCache).get("missing"));
        assertNull(new RecordChangeInfoRedisCache(redisCache).get("missing"));
        assertNull(new ChildrenKindsRedisCache(redisCache).get("missing"));
        assertNull(new ParentChildRelationshipSpecsRedisCache(redisCache).get("missing"));
        assertNull(new KindRedisCache(redisCache).get("missing"));
    }
}
