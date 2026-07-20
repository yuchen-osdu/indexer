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

package org.opengroup.osdu.indexer.model.indexproperty;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(SpringRunner.class)
public class RelatedConditionTest {

    @Test
    public void isMatch_return_false_for_no_match_conditions() {
        RelatedCondition relatedCondition = new RelatedCondition();
        Assert.assertFalse(relatedCondition.isMatch("abc"));

        relatedCondition.setRelatedConditionMatches(new ArrayList<>());
        Assert.assertFalse(relatedCondition.isMatch("abc"));
    }

    @Test
    public void isMatch_return_false_for_empty_propertyValue() {
        RelatedCondition relatedCondition = new RelatedCondition();
        relatedCondition.setRelatedConditionMatches(Arrays.asList("xyz","abc"));
        Assert.assertFalse(relatedCondition.isMatch(null));
        Assert.assertFalse(relatedCondition.isMatch(""));
    }

    @Test
    public void isMatch_return_true_for_string_match() {
        RelatedCondition relatedCondition = new RelatedCondition();
        relatedCondition.setRelatedConditionMatches(Arrays.asList("xyz","abc"));
        Assert.assertTrue(relatedCondition.isMatch("abc"));
   }

    @Test
    public void isMatch_return_false_for_string_match() {
        RelatedCondition relatedCondition = new RelatedCondition();
        relatedCondition.setRelatedConditionMatches(Arrays.asList("xyz","abc"));
        Assert.assertFalse(relatedCondition.isMatch("ABC"));
    }

    @Test
    public void isMatch_return_true_for_regex_match() {
        RelatedCondition relatedCondition = new RelatedCondition();
        relatedCondition.setRelatedConditionMatches(Arrays.asList("^[\\w\\-\\.]+:master-data\\-\\-Wellbore:[\\w\\-\\.\\:\\%]+$"));
        Assert.assertTrue(relatedCondition.isMatch("opendes:master-data--Wellbore:2B886EAF-83A9-4811-9068"));

        relatedCondition.setRelatedConditionMatches(Arrays.asList(
                "^[\\w\\-\\.]+:reference-data--AliasNameType:UniqueIdentifier:$",
                "^[\\w\\-\\.]+:reference-data--AliasNameType:RegulatoryName:$",
                "^[\\w\\-\\.]+:reference-data--AliasNameType:PreferredName:$",
                "^[\\w\\-\\.]+:reference-data--AliasNameType:CommonName:$",
                "^[\\w\\-\\.]+:reference-data--AliasNameType:ShortName:$"));
        Assert.assertTrue(relatedCondition.isMatch("opendes:reference-data--AliasNameType:PreferredName:"));
    }

    @Test
    public void isMatch_return_false_for_regex_match() {
        RelatedCondition relatedCondition = new RelatedCondition();
        relatedCondition.setRelatedConditionMatches(Arrays.asList("^[\\w\\-\\.]+:master-data\\-\\-Wellbore:[\\w\\-\\.\\:\\%]+$"));
        Assert.assertFalse(relatedCondition.isMatch("opendes:work-product-component--WellLog:2B886EAF-83A9-4811-9068"));

        relatedCondition.setRelatedConditionMatches(Arrays.asList(
                "^[\\w\\-\\.]+:reference-data--AliasNameType:UniqueIdentifier:$",
                "^[\\w\\-\\.]+:reference-data--AliasNameType:RegulatoryName:$",
                "^[\\w\\-\\.]+:reference-data--AliasNameType:PreferredName:$",
                "^[\\w\\-\\.]+:reference-data--AliasNameType:CommonName:$",
                "^[\\w\\-\\.]+:reference-data--AliasNameType:ShortName:$"));
        Assert.assertFalse(relatedCondition.isMatch("opendes:reference-data--AliasNameType:PreferredName"));
   }

}
