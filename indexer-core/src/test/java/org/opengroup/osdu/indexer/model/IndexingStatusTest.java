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
import org.opengroup.osdu.core.common.model.indexer.IndexingStatus;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class IndexingStatusTest {

    @Test
    public void should_returnTrue_givenCorrectParameterOrder_isWorseThanTest() {
        Assert.assertTrue(IndexingStatus.FAIL.isWorseThan(IndexingStatus.SKIP));
        Assert.assertTrue(IndexingStatus.FAIL.isWorseThan(IndexingStatus.SUCCESS));
        Assert.assertTrue(IndexingStatus.FAIL.isWorseThan(IndexingStatus.PROCESSING));
        Assert.assertTrue(IndexingStatus.SKIP.isWorseThan(IndexingStatus.SUCCESS));
        Assert.assertTrue(IndexingStatus.SKIP.isWorseThan(IndexingStatus.PROCESSING));
        Assert.assertTrue(IndexingStatus.SUCCESS.isWorseThan(IndexingStatus.PROCESSING));
    }

    @Test
    public void should_returnFalse_givenReversedParameterOrder_isWorseThanTest() {
        Assert.assertFalse(IndexingStatus.PROCESSING.isWorseThan(IndexingStatus.SUCCESS));
        Assert.assertFalse(IndexingStatus.PROCESSING.isWorseThan(IndexingStatus.SKIP));
        Assert.assertFalse(IndexingStatus.PROCESSING.isWorseThan(IndexingStatus.FAIL));
        Assert.assertFalse(IndexingStatus.SUCCESS.isWorseThan(IndexingStatus.SKIP));
        Assert.assertFalse(IndexingStatus.SUCCESS.isWorseThan(IndexingStatus.FAIL));
        Assert.assertFalse(IndexingStatus.SKIP.isWorseThan(IndexingStatus.FAIL));
    }
}
