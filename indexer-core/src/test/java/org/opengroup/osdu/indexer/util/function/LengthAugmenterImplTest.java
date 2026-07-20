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

package org.opengroup.osdu.indexer.util.function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.indexer.StorageType;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.indexer.model.indexproperty.ValueExtraction;

import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class LengthAugmenterImplTest {
    @InjectMocks
    private LengthAugmenterImpl lengthAugmenter;

    @Mock
    private JaxRsDpsLog jaxRsDpsLog;

    private ValueExtraction valueExtraction;
    private Object shapeObject;
    ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setup() throws JsonProcessingException {
        valueExtraction = new ValueExtraction();
        valueExtraction.setValuePath("Len(VirtualProperties.DefaultLocation.Wgs84Coordinates)");
        String json = """
                {
                     "type": "geometrycollection",
                     "geometries": [{
                             "type": "linestring",
                             "coordinates": [
                                 [
                                     -92.592,
                                     28.1811
                                 ],
                                 [
                                     -92.4696,
                                     28.1834
                                 ]
                             ]
                         }
                     ]
                 }
                """;
        shapeObject = mapper.readValue(json, Object.class);
    }


    @Test
    public void isMatched_return_true() {
        Assertions.assertTrue(lengthAugmenter.isMatched(valueExtraction));

        // with space
        valueExtraction.setValuePath("Len ( VirtualProperties.DefaultLocation.Wgs84Coordinates )");

        // lower case
        valueExtraction.setValuePath("len(VirtualProperties.DefaultLocation.Wgs84Coordinates)");
        Assertions.assertTrue(lengthAugmenter.isMatched(valueExtraction));

        // upper case
        valueExtraction.setValuePath("LEN(VirtualProperties.DefaultLocation.Wgs84Coordinates)");
        Assertions.assertTrue(lengthAugmenter.isMatched(valueExtraction));
    }

    @Test
    public void isMatched_return_false() {
        valueExtraction.setValuePath("Length(VirtualProperties.DefaultLocation.Wgs84Coordinates)");
        Assertions.assertFalse(lengthAugmenter.isMatched(valueExtraction));

        Assertions.assertFalse(lengthAugmenter.isMatched(null));
    }

    @Test
    public void getValuePath() {
        List<String> valuePaths = lengthAugmenter.getValuePaths(valueExtraction);
        Assertions.assertEquals(1, valuePaths.size());
        Assertions.assertEquals("VirtualProperties.DefaultLocation.Wgs84Coordinates", valuePaths.get(0));

        // with data. prefix
        valueExtraction.setValuePath("Len(data.VirtualProperties.DefaultLocation.Wgs84Coordinates)");
        valuePaths = lengthAugmenter.getValuePaths(valueExtraction);
        Assertions.assertEquals(1, valuePaths.size());
        Assertions.assertEquals("VirtualProperties.DefaultLocation.Wgs84Coordinates", valuePaths.get(0));

        // with space
        valueExtraction.setValuePath("len ( data.VirtualProperties.DefaultLocation.Wgs84Coordinates )");
        valuePaths = lengthAugmenter.getValuePaths(valueExtraction);
        Assertions.assertEquals(1, valuePaths.size());
        Assertions.assertEquals("VirtualProperties.DefaultLocation.Wgs84Coordinates", valuePaths.get(0));
    }

    @Test
    public void getExtendedSchemaItems() {
        String extendedPropertyName = "Length";
        List<SchemaItem> schemaItems = lengthAugmenter.getExtendedSchemaItems(extendedPropertyName);
        Assertions.assertNotNull(schemaItems);
        Assertions.assertEquals(1, schemaItems.size());

        SchemaItem schemaItem = schemaItems.get(0);
        Assertions.assertEquals(extendedPropertyName, schemaItem.getPath());
        Assertions.assertEquals(StorageType.DOUBLE.getValue(), schemaItem.getKind());
    }

    @Test
    public void getPropertyValues() {
        String extendedPropertyName = "Length";
        Map<String, Object> propertyValues = lengthAugmenter.getPropertyValues(extendedPropertyName, valueExtraction, Map.of("VirtualProperties.DefaultLocation.Wgs84Coordinates", shapeObject));
        Assertions.assertNotNull(propertyValues);
        Assertions.assertEquals(1, propertyValues.size());

        Assertions.assertTrue(propertyValues.containsKey(extendedPropertyName));
        double doubleValue = (double) propertyValues.get(extendedPropertyName);
        Assertions.assertTrue(Math.abs(doubleValue - 12021.88) < 0.01);
    }

    @Test
    public void getPropertyValues_with_empty_shape() throws JsonProcessingException {
        String json = """
        {"type": "geometrycollection", "geometries": []}
        """;
        shapeObject = mapper.readValue(json, Object.class);

        String extendedPropertyName = "Length";
        Map<String, Object> propertyValues = lengthAugmenter.getPropertyValues(extendedPropertyName, valueExtraction, Map.of("VirtualProperties.DefaultLocation.Wgs84Coordinates", shapeObject));
        Assertions.assertTrue(propertyValues.isEmpty());
    }

    @Test
    public void getPropertyValues_with_unsupported_type_of_shape() throws JsonProcessingException {
        String json = """
                {
                      "type": "geometrycollection",
                      "geometries": [{
                              "type": "linestring",
                              "coordinates": [
                                  [
                                      [
                                          100.0,
                                          0.0
                                      ],
                                      [
                                          101.0,
                                          0.0
                                      ],
                                      [
                                          101.0,
                                          1.0
                                      ],
                                      [
                                          100.0,
                                          1.0
                                      ],
                                      [
                                          100.0,
                                          0.0
                                      ]
                                  ]
                              ]
                          }
                      ]
                  }
                """;
        shapeObject = mapper.readValue(json, Object.class);

        String extendedPropertyName = "Length";
        Map<String, Object> propertyValues = lengthAugmenter.getPropertyValues(extendedPropertyName, valueExtraction, Map.of("VirtualProperties.DefaultLocation.Wgs84Coordinates", shapeObject));
        Assertions.assertTrue(propertyValues.isEmpty());
    }
}
