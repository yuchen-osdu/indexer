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
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.indexer.StorageType;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.indexer.model.indexproperty.ValueExtraction;

import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ExtentAugmenterImplTest {
    @InjectMocks
    private ExtentAugmenterImpl extentAugmenter;

    private ValueExtraction valueExtraction;
    private Object shapeObject;
    ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setup() throws JsonProcessingException {
        valueExtraction = new ValueExtraction();
        valueExtraction.setValuePath("Extent(VirtualProperties.DefaultLocation.Wgs84Coordinates)");

        String json = """
                {
                      "type": "geometrycollection",
                      "geometries": [{
                              "type": "polygon",
                              "coordinates": [
                                  [
                                      [
                                          1.9424678718459296,
                                          58.41226788404921
                                      ],
                                      [
                                          1.7925815543556232,
                                          58.43047583899937
                                      ],
                                      [
                                          1.811825015931608,
                                          58.47423325793858
                                      ],
                                      [
                                          1.961888009650567,
                                          58.45600339792121
                                      ],
                                      [
                                          1.9424678718459296,
                                          58.41226788404921
                                      ]
                                  ]
                              ]
                          }
                      ]
                  }
              }
              """;
        shapeObject = mapper.readValue(json, Object.class);
    }

    @Test
    public void isMatched_return_true() {
        Assertions.assertTrue(extentAugmenter.isMatched(valueExtraction));

        // with space
        valueExtraction.setValuePath("Extent ( VirtualProperties.DefaultLocation.Wgs84Coordinates )");

        // lower case
        valueExtraction.setValuePath("extent(VirtualProperties.DefaultLocation.Wgs84Coordinates)");
        Assertions.assertTrue(extentAugmenter.isMatched(valueExtraction));

        // upper case
        valueExtraction.setValuePath("EXTENT(VirtualProperties.DefaultLocation.Wgs84Coordinates)");
        Assertions.assertTrue(extentAugmenter.isMatched(valueExtraction));
    }

    @Test
    public void isMatched_return_false() {
        valueExtraction.setValuePath("Extend(VirtualProperties.DefaultLocation.Wgs84Coordinates)");
        Assertions.assertFalse(extentAugmenter.isMatched(valueExtraction));

        Assertions.assertFalse(extentAugmenter.isMatched(null));
    }

    @Test
    public void getValuePath() {
        List<String> valuePaths = extentAugmenter.getValuePaths(valueExtraction);
        Assertions.assertEquals(1, valuePaths.size());
        Assertions.assertEquals("VirtualProperties.DefaultLocation.Wgs84Coordinates", valuePaths.get(0));

        // with data. prefix
        valueExtraction.setValuePath("Extent(data.VirtualProperties.DefaultLocation.Wgs84Coordinates)");
        valuePaths = extentAugmenter.getValuePaths(valueExtraction);
        Assertions.assertEquals(1, valuePaths.size());
        Assertions.assertEquals("VirtualProperties.DefaultLocation.Wgs84Coordinates", valuePaths.get(0));


        // with space
        valueExtraction.setValuePath("Extent ( VirtualProperties.DefaultLocation.Wgs84Coordinates )");
        valuePaths = extentAugmenter.getValuePaths(valueExtraction);
        Assertions.assertEquals(1, valuePaths.size());
        Assertions.assertEquals("VirtualProperties.DefaultLocation.Wgs84Coordinates", valuePaths.get(0));
    }

    @Test
    public void getExtendedSchemaItems() {
        String extendedPropertyName = "Extent";
        List<SchemaItem> schemaItems = extentAugmenter.getExtendedSchemaItems(extendedPropertyName);
        Assertions.assertNotNull(schemaItems);
        Assertions.assertEquals(4, schemaItems.size());

        for(SchemaItem schemaItem : schemaItems) {
            Assertions.assertTrue(schemaItem.getPath().startsWith(extendedPropertyName));
            Assertions.assertEquals(StorageType.DOUBLE.getValue(), schemaItem.getKind());
        }
    }

    @Test
    public void getPropertyValues() {
        String extendedPropertyName = "Extent";
        Map<String, Object> propertyValues = extentAugmenter.getPropertyValues(extendedPropertyName, valueExtraction, Map.of("VirtualProperties.DefaultLocation.Wgs84Coordinates", shapeObject));
        Assertions.assertNotNull(propertyValues);
        Assertions.assertEquals(4, propertyValues.size());

        double latitudeRange = (double) propertyValues.get(extendedPropertyName + "." + "latitudeRange");
        Assertions.assertTrue(Math.abs(latitudeRange - 0.061965) < 0.000001);

        double longitudeRange = (double) propertyValues.get(extendedPropertyName + "." + "longitudeRange");
        Assertions.assertTrue(Math.abs(longitudeRange - 0.169306) < 0.000001);

        double latitudeCenter = (double) propertyValues.get(extendedPropertyName + "." + "latitudeCenter");
        Assertions.assertTrue(Math.abs(latitudeCenter - 58.443251) < 0.000001);

        double longitudeCenter = (double) propertyValues.get(extendedPropertyName + "." + "longitudeCenter");
        Assertions.assertTrue(Math.abs(longitudeCenter - 1.877235) < 0.000001);
    }

    @Test
    public void getPropertyValues_with_empty_shape() throws JsonProcessingException {
        String json = """
        {"type": "geometrycollection", "geometries": []}
        """;
        shapeObject = mapper.readValue(json, Object.class);

        String extendedPropertyName = "Extent";
        Map<String, Object> propertyValues = extentAugmenter.getPropertyValues(extendedPropertyName, valueExtraction, Map.of("VirtualProperties.DefaultLocation.Wgs84Coordinates", shapeObject));
        Assertions.assertTrue(propertyValues.isEmpty());
    }
}
