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
import org.opengroup.osdu.core.common.model.indexer.ElasticType;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class ElasticTypeTest {

    @Test
    public void should_returnUndefined_givenNull_forValueTest() {
        Assert.assertEquals(ElasticType.UNDEFINED, ElasticType.forValue(null));
    }

    @Test
    public void should_returnUndefined_givenUnknownType_forValueTest() {
        Assert.assertEquals(ElasticType.UNDEFINED, ElasticType.forValue("test"));
    }

    @Test
    public void should_returnCorrespondingType_givenValidType_forValueTest() {
        Assert.assertEquals(ElasticType.KEYWORD, ElasticType.forValue("keyword"));
        Assert.assertEquals(ElasticType.TEXT, ElasticType.forValue("Text"));
        Assert.assertEquals(ElasticType.DATE, ElasticType.forValue("Date"));
        Assert.assertEquals(ElasticType.NESTED, ElasticType.forValue("nestED"));
        Assert.assertEquals(ElasticType.OBJECT, ElasticType.forValue("OBJECT"));
        Assert.assertEquals(ElasticType.GEO_POINT, ElasticType.forValue("geo_point"));
        Assert.assertEquals(ElasticType.GEO_SHAPE, ElasticType.forValue("geo_shape"));
        Assert.assertEquals(ElasticType.INTEGER, ElasticType.forValue("INteger"));
        Assert.assertEquals(ElasticType.LONG, ElasticType.forValue("long"));
        Assert.assertEquals(ElasticType.FLOAT, ElasticType.forValue("FLOAT"));
        Assert.assertEquals(ElasticType.DOUBLE, ElasticType.forValue("DOUBLE"));
        Assert.assertEquals(ElasticType.BOOLEAN, ElasticType.forValue("Boolean"));
        Assert.assertEquals(ElasticType.UNDEFINED, ElasticType.forValue("undefined"));
    }
}
