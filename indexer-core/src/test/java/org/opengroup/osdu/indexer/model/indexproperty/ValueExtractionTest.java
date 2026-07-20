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
import java.util.List;

@RunWith(SpringRunner.class)
public class ValueExtractionTest {
    @Test
    public void hasValidCondition_return_true() {
        ValueExtraction valueExtraction = new ValueExtraction();
        valueExtraction.setValuePath("data.NameAliases[].AliasName");
        valueExtraction.setRelatedConditionProperty("data.NameAliases[].AliasNameTypeID");
        List<String> matches = Arrays.asList("opendes:reference-data--AliasNameType:UniqueIdentifier:","reference-data--AliasNameType:RegulatoryName:");
        valueExtraction.setRelatedConditionMatches(matches);

        Assert.assertTrue(valueExtraction.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_true_with_multi_nested() {
        ValueExtraction valueExtraction = new ValueExtraction();
        valueExtraction.setValuePath("data.TechnicalAssurances[].Reviewers[].Name");
        valueExtraction.setRelatedConditionProperty("data.TechnicalAssurances[].Reviewers[].RoleTypeID");
        List<String> matches = Arrays.asList("opendes:reference-data--ContactRoleType:AccountOwner:");
        valueExtraction.setRelatedConditionMatches(matches);

        Assert.assertTrue(valueExtraction.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_false_with_unmatched_multi_nested() {
        ValueExtraction valueExtraction = new ValueExtraction();
        valueExtraction.setValuePath("data.TechnicalAssurances[].Reviewers[].Name");
        valueExtraction.setRelatedConditionProperty("data.TechnicalAssurances[].Auditors[].RoleTypeID");
        List<String> matches = Arrays.asList("opendes:reference-data--ContactRoleType:AccountOwner:");
        valueExtraction.setRelatedConditionMatches(matches);

        Assert.assertFalse(valueExtraction.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_false_with_null_propertyPath() {
        ValueExtraction valueExtraction = new ValueExtraction();
        valueExtraction.setValuePath("data.NameAliases[].AliasName");
        Assert.assertFalse(valueExtraction.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_false_with_empty_matches() {
        ValueExtraction valueExtraction = new ValueExtraction();
        valueExtraction.setValuePath("data.NameAliases[].AliasName");
        valueExtraction.setRelatedConditionProperty("data.NameAliases[].AliasNameTypeID");
        valueExtraction.setRelatedConditionMatches(new ArrayList<>());

        Assert.assertFalse(valueExtraction.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_false_with_unmatched_propertyPath() {
        ValueExtraction valueExtraction = new ValueExtraction();
        valueExtraction.setValuePath("data.NameAliases[].AliasName");
        valueExtraction.setRelatedConditionProperty("data.VerticalMeasurementID[].VerticalMeasurementID");
        List<String> matches = Arrays.asList("KB");
        valueExtraction.setRelatedConditionMatches(matches);

        Assert.assertFalse(valueExtraction.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_false_without_nested_property() {
        ValueExtraction valueExtraction = new ValueExtraction();
        valueExtraction.setValuePath("data.NameAliases[]");
        valueExtraction.setRelatedConditionProperty("data.NameAliases[]");
        List<String> matches = Arrays.asList("opendes:reference-data--AliasNameType:UniqueIdentifier:","reference-data--AliasNameType:RegulatoryName:");
        valueExtraction.setRelatedConditionMatches(matches);
        Assert.assertFalse(valueExtraction.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_true_for_property_under_same_none_nested_property() {
        ValueExtraction valueExtraction = new ValueExtraction();
        valueExtraction.setValuePath("data.NameAliases.AliasName");
        valueExtraction.setRelatedConditionProperty("data.NameAliases.AliasNameTypeID");
        List<String> matches = Arrays.asList("opendes:reference-data--AliasNameType:UniqueIdentifier:","reference-data--AliasNameType:RegulatoryName:");
        valueExtraction.setRelatedConditionMatches(matches);
        Assert.assertTrue(valueExtraction.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_true_for_property_under_different_none_nested_property() {
        ValueExtraction valueExtraction = new ValueExtraction();
        valueExtraction.setValuePath("data.NameAliases.AliasName");
        valueExtraction.setRelatedConditionProperty("data.AliasNameTypeID");
        List<String> matches = Arrays.asList("opendes:reference-data--AliasNameType:UniqueIdentifier:","reference-data--AliasNameType:RegulatoryName:");
        valueExtraction.setRelatedConditionMatches(matches);
        Assert.assertTrue(valueExtraction.hasValidCondition());
    }
}
