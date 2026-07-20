// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.indexer.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
public class RecordInfoTest {

    private List<RecordInfo> msgs;
    private final String recordChangedMessages = "[{\"id\":\"tenant1:doc:test1\",\"kind\":\"tenant1:testindexer1:well:1.0.0\",\"op\":\"purge\"}," +
            "{\"id\":\"tenant1:doc:test4\",\"kind\":\"tenant1:testindexer4:well:1.0.0\",\"op\":\"delete\"}," +
            "{\"id\":\"tenant1:doc:test5\",\"kind\":\"tenant1:testindexer4:well:1.0.0\",\"op\":\"delete\"}," +
            "{\"id\":\"tenant1:doc:test2\",\"kind\":\"tenant1:testindexer2:well:1.0.0\",\"op\":\"create\"}," +
            "{\"id\":\"tenant1:doc:test3\",\"kind\":\"tenant1:testindexer3:well:1.0.0\",\"op\":\"update\"}]";

    @Before
    public void setup() {
        Type listType = new TypeToken<List<RecordInfo>>() {}.getType();
        msgs = (new Gson()).fromJson(recordChangedMessages, listType);
    }

    @Test
    public void should_returnOpMap_given_createOrUpdateOperations_getUpsertRecordIdsTest() {
        Map<String, Map<String, OperationType>> kindRecordOpMap = RecordInfo.getUpsertRecordIds(msgs);
        Assert.assertEquals(2, kindRecordOpMap.size());
        Assert.assertEquals(OperationType.create, kindRecordOpMap.get("tenant1:testindexer2:well:1.0.0").get("tenant1:doc:test2"));
        Assert.assertEquals(OperationType.update, kindRecordOpMap.get("tenant1:testindexer3:well:1.0.0").get("tenant1:doc:test3"));
    }


    private void should_return400_getUpsertRecordTest(List<RecordInfo> msgs, String errorMessage) {
        try {
            RecordInfo.getUpsertRecordIds(msgs);
        } catch (AppException e) {
            Assert.assertEquals(HttpStatus.BAD_REQUEST.value(), e.getError().getCode());
            Assert.assertEquals(errorMessage, e.getError().getMessage());
        } catch (Exception e) {
            fail("Should throw this exception" + e.getMessage());
        }
    }

    @Test
    public void should_return400_given_nullMessage_getUpsertRecordTest() {
        should_return400_getUpsertRecordTest(null, "Error parsing upsert records in request payload.");
    }

    @Test
    public void should_returnValidResponse_getDeleteRecordIdsTest() {
        Map<String, List<String>> deleteRecordMap = RecordInfo.getDeleteRecordIds(msgs);
        Assert.assertEquals(2, deleteRecordMap.size());
        Assert.assertEquals(1, deleteRecordMap.get("tenant1:testindexer1:well:1.0.0").size());
        Assert.assertEquals(2, deleteRecordMap.get("tenant1:testindexer4:well:1.0.0").size());
    }


    private void should_return400_getDeleteRecordTest(List<RecordInfo> msgs, String errorMessage) {
        try {
            RecordInfo.getDeleteRecordIds(msgs);
            fail("Should throw exception");
        } catch (AppException e) {
            Assert.assertEquals(HttpStatus.BAD_REQUEST.value(), e.getError().getCode());
            Assert.assertEquals(errorMessage, e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not throw this exception" + e.getMessage());
        }
    }

    @Test
    public void should_return400_givenNullMessage_getDeleteRecordIdsTest() {
        should_return400_getDeleteRecordTest(null, "Error parsing delete records in request payload.");
    }
}
