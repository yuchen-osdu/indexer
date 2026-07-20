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
public class RelatedObjectsSpecTest {

    @Test
    public void hasValidCondition_return_true() {
        RelatedObjectsSpec spec = new RelatedObjectsSpec();
        spec.setRelatedObjectKind("osdu:wks:master-data--GeoPoliticalEntity:1.");
        spec.setRelationshipDirection(RelatedObjectsSpec.CHILD_TO_PARENT);
        spec.setRelatedObjectID("data.GeoContexts[].GeoPoliticalEntityID");
        spec.setRelatedConditionProperty("data.GeoContexts[].GeoTypeID");
        List<String> matches = Arrays.asList("opendes:reference-data--GeoPoliticalEntityType:Country:");
        spec.setRelatedConditionMatches(matches);

        Assert.assertTrue(spec.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_true_with_multi_nested() {
        RelatedObjectsSpec spec = new RelatedObjectsSpec();
        spec.setRelatedObjectKind("osdu:wks:master-data--Organisation:1.");
        spec.setRelationshipDirection(RelatedObjectsSpec.CHILD_TO_PARENT);
        spec.setRelatedObjectID("data.TechnicalAssurances[].Reviewers[].OrganisationID");
        spec.setRelatedConditionProperty("data.TechnicalAssurances[].Reviewers[].RoleTypeID");
        List<String> matches = Arrays.asList("opendes:reference-data--ContactRoleType:AccountOwner:");
        spec.setRelatedConditionMatches(matches);

        Assert.assertTrue(spec.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_false_with_unmatched_multi_nested() {
        RelatedObjectsSpec spec = new RelatedObjectsSpec();
        spec.setRelatedObjectKind("osdu:wks:master-data--Organisation:1.");
        spec.setRelatedObjectID("data.TechnicalAssurances[].Reviewers[].OrganisationID");
        spec.setRelatedConditionProperty("data.TechnicalAssurances[].Auditors[].RoleTypeID");
        List<String> matches = Arrays.asList("opendes:reference-data--ContactRoleType:AccountOwner:");
        spec.setRelatedConditionMatches(matches);

        Assert.assertFalse(spec.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_false_with_null_propertyPath() {
        RelatedObjectsSpec spec = new RelatedObjectsSpec();
        spec.setRelatedObjectKind("osdu:wks:master-data--GeoPoliticalEntity:1.");
        spec.setRelatedObjectID("data.GeoContexts[].GeoPoliticalEntityID");
        Assert.assertFalse(spec.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_false_with_empty_matches() {
        RelatedObjectsSpec spec = new RelatedObjectsSpec();
        spec.setRelatedObjectKind("osdu:wks:master-data--GeoPoliticalEntity:1.");
        spec.setRelatedObjectID("data.GeoContexts[].GeoPoliticalEntityID");
        spec.setRelatedConditionProperty("data.GeoContexts[].GeoTypeID");
        spec.setRelatedConditionMatches(new ArrayList<>());

        Assert.assertFalse(spec.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_false_with_unmatched_propertyPath() {
        RelatedObjectsSpec spec = new RelatedObjectsSpec();
        spec.setRelatedObjectKind("osdu:wks:master-data--GeoPoliticalEntity:1.");
        spec.setRelatedObjectID("data.GeoContexts[].GeoPoliticalEntityID");
        spec.setRelatedConditionProperty("data.VerticalMeasurementID[].VerticalMeasurementID");
        List<String> matches = Arrays.asList("KB");
        spec.setRelatedConditionMatches(matches);

        Assert.assertFalse(spec.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_false_without_nested_property() {
        RelatedObjectsSpec spec = new RelatedObjectsSpec();
        spec.setRelatedObjectKind("osdu:wks:master-data--GeoPoliticalEntity:1.");
        spec.setRelatedObjectID("data.GeoContexts[]");
        spec.setRelatedConditionProperty("data.GeoContexts[]");
        List<String> matches = Arrays.asList("opendes:reference-data--GeoPoliticalEntityType:Country:");
        spec.setRelatedConditionMatches(matches);
        Assert.assertFalse(spec.hasValidCondition());
    }

    @Test
    public void hasValidCondition_return_true_for_none_nested_property() {
        RelatedObjectsSpec spec = new RelatedObjectsSpec();
        spec.setRelatedObjectKind("osdu:wks:master-data--GeoPoliticalEntity:1.");
        spec.setRelatedObjectID("data.GeoContexts.GeoPoliticalEntityID");
        spec.setRelatedConditionProperty("data.GeoContexts.GeoTypeID");
        List<String> matches = Arrays.asList("opendes:reference-data--GeoPoliticalEntityType:Country:");
        spec.setRelatedConditionMatches(matches);
        Assert.assertTrue(spec.hasValidCondition());
    }

}
