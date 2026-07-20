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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@RunWith(SpringRunner.class)
public class AugmenterConfigurationTest {
    private AugmenterConfiguration augmenterConfiguration;

    @Before
    public void setup() throws JsonProcessingException {
        String jsonText = getJsonFromFile("well_configuration_record.json");
        ObjectMapper objectMapper = new ObjectMapper();
        augmenterConfiguration = objectMapper.readValue(jsonText, AugmenterConfiguration.class);
    }

    @Test
    public void isValid() {
        Assert.assertTrue(augmenterConfiguration.hasValidCode());
        Assert.assertTrue(augmenterConfiguration.hasValidConfigurations());
        Assert.assertFalse(augmenterConfiguration.hasInvalidConfigurations());
        Assert.assertTrue(augmenterConfiguration.isValid());
    }

    @Test
    public void hasInvalidCode() {
        String code = augmenterConfiguration.getCode();

        augmenterConfiguration.setCode(code + "0.0");
        Assert.assertFalse(augmenterConfiguration.hasValidCode());
        Assert.assertFalse(augmenterConfiguration.isValid());

        augmenterConfiguration.setCode("a:b:1.");
        Assert.assertFalse(augmenterConfiguration.hasValidCode());
        Assert.assertFalse(augmenterConfiguration.isValid());

        augmenterConfiguration.setCode("");
        Assert.assertFalse(augmenterConfiguration.hasValidCode());
        Assert.assertFalse(augmenterConfiguration.isValid());

        augmenterConfiguration.setCode(null);
        Assert.assertFalse(augmenterConfiguration.hasValidCode());
        Assert.assertFalse(augmenterConfiguration.isValid());
    }

    @Test
    public void hasNoValidConfigurations() {
        List<PropertyConfiguration> propertyConfigurations = augmenterConfiguration.getConfigurations();

        augmenterConfiguration.setConfigurations(new ArrayList<>());
        Assert.assertFalse(augmenterConfiguration.hasValidConfigurations());
        Assert.assertFalse(augmenterConfiguration.hasInvalidConfigurations());
        Assert.assertFalse(augmenterConfiguration.isValid());

        augmenterConfiguration.setConfigurations(null);
        Assert.assertFalse(augmenterConfiguration.hasValidConfigurations());
        Assert.assertFalse(augmenterConfiguration.hasInvalidConfigurations());
        Assert.assertFalse(augmenterConfiguration.isValid());

        propertyConfigurations.forEach(p -> p.setPolicy(""));
        augmenterConfiguration.setConfigurations(propertyConfigurations);
        Assert.assertFalse(augmenterConfiguration.hasValidConfigurations());
        Assert.assertTrue(augmenterConfiguration.hasInvalidConfigurations());
        Assert.assertFalse(augmenterConfiguration.isValid());
    }

    @Test
    public void hasPartialValidConfigurations() {
        List<PropertyConfiguration> propertyConfigurations = augmenterConfiguration.getConfigurations();
        Assert.assertEquals(2, propertyConfigurations.size());

        propertyConfigurations.get(0).setPolicy("");
        Assert.assertTrue(augmenterConfiguration.hasValidConfigurations());
        Assert.assertTrue(augmenterConfiguration.hasInvalidConfigurations());
        Assert.assertTrue(augmenterConfiguration.isValid());
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
