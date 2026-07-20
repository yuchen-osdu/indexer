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

package org.opengroup.osdu.indexer.model;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengroup.osdu.core.common.model.indexer.StorageType;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class StorageTypeTest {

    @Test
    public void should_returnCorrectValue_getValueTest() {
        Assert.assertEquals("link", StorageType.LINK.getValue());
        Assert.assertEquals("[]link", StorageType.LINK_ARRAY.getValue());
        Assert.assertEquals("boolean", StorageType.BOOLEAN.getValue());
        Assert.assertEquals("string", StorageType.STRING.getValue());
        Assert.assertEquals("int", StorageType.INT.getValue());
        Assert.assertEquals("float", StorageType.FLOAT.getValue());
        Assert.assertEquals("double", StorageType.DOUBLE.getValue());
        Assert.assertEquals("[]double", StorageType.DOUBLE_ARRAY.getValue());
        Assert.assertEquals("long", StorageType.LONG.getValue());
        Assert.assertEquals("datetime", StorageType.DATETIME.getValue());
        Assert.assertEquals("core:dl:geopoint:1.0.0", StorageType.GEO_POINT.getValue());
        Assert.assertEquals("core:dl:geoshape:1.0.0", StorageType.GEO_SHAPE.getValue());
    }
}
