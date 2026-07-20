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

package org.opengroup.osdu.indexer.model.indexproperty.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengroup.osdu.indexer.model.indexproperty.PropertyConfiguration;
import org.opengroup.osdu.indexer.model.indexproperty.AugmenterConfiguration;
import org.opengroup.osdu.indexer.model.indexproperty.PropertyPath;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

@RunWith(SpringRunner.class)
public class PropertyPathDeserializerTest {
    @Test
    public void deserialize_configurations_test() throws JsonProcessingException {
        String jsonText = getJsonFromFile("well_configuration_record.json");
        ObjectMapper objectMapper = new ObjectMapper();
        AugmenterConfiguration augmenterConfiguration = objectMapper.readValue(jsonText, AugmenterConfiguration.class);
        Assert.assertNotNull(augmenterConfiguration);
        Assert.assertEquals(2, augmenterConfiguration.getConfigurations().size());
        PropertyConfiguration countryNameConfiguration = augmenterConfiguration.getConfigurations().get(0);
        PropertyConfiguration wellUWIConfiguration = augmenterConfiguration.getConfigurations().get(1);

        Assert.assertEquals("CountryNames", countryNameConfiguration.getName());
        Assert.assertEquals(1, countryNameConfiguration.getPaths().size());
        PropertyPath path1 = countryNameConfiguration.getPaths().get(0);
        Assert.assertTrue(path1.hasValidRelatedObjectsSpec());
        Assert.assertTrue(path1.getRelatedObjectsSpec().hasValidCondition());
        Assert.assertEquals(1, path1.getRelatedObjectsSpec().getRelatedConditionMatches().size());
        Assert.assertTrue(path1.hasValidValueExtraction());
        Assert.assertFalse(path1.getValueExtraction().hasValidCondition());

        Assert.assertEquals("WellUWI", wellUWIConfiguration.getName());
        Assert.assertEquals(1, wellUWIConfiguration.getPaths().size());
        PropertyPath path2 = wellUWIConfiguration.getPaths().get(0);
        Assert.assertFalse(path2.hasValidRelatedObjectsSpec());
        Assert.assertTrue(path2.hasValidValueExtraction());
        Assert.assertTrue(path2.getValueExtraction().hasValidCondition());
        Assert.assertEquals(5, path2.getValueExtraction().getRelatedConditionMatches().size());

    }

    @SneakyThrows
    private String getJsonFromFile(String file) {
        InputStream inStream = this.getClass().getResourceAsStream("/indexproperty/" + file);
        BufferedReader br = new BufferedReader(new InputStreamReader(inStream));
        StringBuilder stringBuilder = new StringBuilder();
        String sCurrentLine;
        while ((sCurrentLine = br.readLine()) != null)
        {
            stringBuilder.append(sCurrentLine).append("\n");
        }
        return stringBuilder.toString();
    }

}
