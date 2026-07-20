// Copyright 2017-2020, Schlumberger
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

package org.opengroup.osdu.indexer.schema.converter;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.indexer.schema.converter.config.SchemaConverterPropertiesConfig;
import org.opengroup.osdu.indexer.schema.converter.tags.AllOfItem;
import org.opengroup.osdu.indexer.schema.converter.tags.Definition;
import org.opengroup.osdu.indexer.schema.converter.tags.Definitions;
import org.opengroup.osdu.indexer.schema.converter.tags.Items;
import org.opengroup.osdu.indexer.schema.converter.tags.TypeProperty;
import org.opengroup.osdu.indexer.util.BooleanFeatureFlagClient;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.when;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.MAP_BOOL2STRING_FEATURE_NAME;

@RunWith(MockitoJUnitRunner.class)
public class PropertiesProcessorTest {

    private static final String PATH = "given_path";
    private static final String DEFINITIONS_PREFIX = "#/definitions/";

    @Mock
    private IFeatureFlag featureFlagChecker;

    @Mock
    private BooleanFeatureFlagClient partitionFlagClient;

    private SchemaConverterPropertiesConfig schemaConverterConfig;

    @Before
    public void setup() throws IOException {
        schemaConverterConfig = new SchemaConverterPropertiesConfig(featureFlagChecker, partitionFlagClient);
    }

    @Test
    public void should_fail_on_bad_reference_definition() {
        PropertiesProcessor propertiesProcessor = new PropertiesProcessor(Mockito.mock(Definitions.class),
                schemaConverterConfig);
        propertiesProcessor.processRef(DEFINITIONS_PREFIX + "unknownDefinition");
        assertEquals(1, propertiesProcessor.getErrors().size());
    }

    @Test
    public void should_fail_on_wrong_definition_format() {
        PropertiesProcessor propertiesProcessor = new PropertiesProcessor(Mockito.mock(Definitions.class), schemaConverterConfig);
        propertiesProcessor.processRef("unknownDefinition");
        assertEquals(1, propertiesProcessor.getErrors().size());
    }

    @Test
    public void should_not_process_special_reference() {
        assertFalse(new PropertiesProcessor(null, schemaConverterConfig)
                .processRef(DEFINITIONS_PREFIX + "a:b:anyCrsGeoJsonFeatureCollection:1.0.0").findAny().isPresent());
    }

    @Test
    public void should_return_special_type() {
        String res = new PropertiesProcessor(null, PATH, schemaConverterConfig)
                .processRef(DEFINITIONS_PREFIX + "a:b:core_dl_geopoint:1.0.0").map(Object::toString).reduce("", String::concat);
        assertEquals("{path=" + PATH + ", kind=core:dl:geopoint:1.0.0}", res);
    }

    @Test
    public void should_process_definition_correctly() {
        JaxRsDpsLog log = Mockito.mock(JaxRsDpsLog.class);

        Definitions definitions = new Definitions();
        Definition definition = new Definition();

        TypeProperty property = new TypeProperty();
        property.setFormat("string");
        String propertyName = "propName";

        Map<String, TypeProperty> properties = new LinkedHashMap<>();
        properties.put(propertyName, property);

        definition.setProperties(properties);

        String defName = "a:b:defName:1.0.0";
        definitions.add(defName, definition);

        String res = new PropertiesProcessor(definitions, PATH, schemaConverterConfig)
                .processRef(DEFINITIONS_PREFIX + defName).map(Object::toString).reduce("", String::concat);
        assertEquals(res, "{path="+ PATH + "." + propertyName + ", kind=string}");
    }

    @Test
    public void should_return_int_item() {
        JaxRsDpsLog log = Mockito.mock(JaxRsDpsLog.class);

        AllOfItem allOfItem = new AllOfItem();

        TypeProperty property = new TypeProperty();
        property.setFormat("integer");

        Map<String, TypeProperty> properties = new LinkedHashMap<>();
        properties.put(PATH, property);
        allOfItem.setProperties(properties);

        String res = new PropertiesProcessor(Mockito.mock(Definitions.class), schemaConverterConfig)
                .processItem(allOfItem).map(Object::toString).reduce("", String::concat);
        assertEquals("{path=" + PATH + ", kind=int}", res);
    }

    @Test
    public void should_return_boolean_from_boolean_item_FF_OFF() {
        // in the earlier versions boolean was translated to bool and 
        // this caused mapping boolean values like text as entry in StorageType entry in map is boolean
        internal_should_return_boolean_from_boolean_item(false);
    }

    @Test
    public void should_return_boolean_from_boolean_item_FF_ON() {
        // in the earlier versions boolean was translated to bool and
        // this caused mapping boolean values like text as entry in StorageType entry in map is boolean
        internal_should_return_boolean_from_boolean_item(true);
    }

    private void internal_should_return_boolean_from_boolean_item(boolean ffFlag) {
        when(this.featureFlagChecker.isFeatureEnabled(MAP_BOOL2STRING_FEATURE_NAME)).thenReturn(ffFlag);
        when(schemaConverterConfig.getFeatureFlagChecker().isFeatureEnabled(MAP_BOOL2STRING_FEATURE_NAME)).thenReturn(ffFlag);
        schemaConverterConfig.resetToDefault();
        AllOfItem allOfItem = new AllOfItem();
        JaxRsDpsLog log = Mockito.mock(JaxRsDpsLog.class);

        TypeProperty property = new TypeProperty();
        property.setType("boolean");

        Map<String, TypeProperty> properties = new LinkedHashMap<>();
        properties.put(PATH, property);
        allOfItem.setProperties(properties);

        assertEquals(
                "{path="+PATH+", kind="+ (ffFlag?"boolean":"bool")+"}",
                new PropertiesProcessor(Mockito.mock(Definitions.class), schemaConverterConfig)
                        .processItem(allOfItem).map(Object::toString).reduce("", String::concat)
        );
    }

    @Test
    public void should_return_boolean_from_bool_item() { 
        // StorageType entry in map is boolean not bool
        AllOfItem allOfItem = new AllOfItem();
        JaxRsDpsLog log = Mockito.mock(JaxRsDpsLog.class);

        TypeProperty property = new TypeProperty();
        property.setType("bool");

        Map<String, TypeProperty> properties = new LinkedHashMap<>();
        properties.put(PATH, property);
        allOfItem.setProperties(properties);

        String res = new PropertiesProcessor(Mockito.mock(Definitions.class), schemaConverterConfig)
                .processItem(allOfItem).map(Object::toString).reduce("", String::concat);
        assertEquals("{path=" + PATH + ", kind=bool}", res);
    }

    @Test
    public void should_return_processed_nested_array_items(){
        JaxRsDpsLog log = Mockito.mock(JaxRsDpsLog.class);

        Map<String, TypeProperty> itemsProperties = new LinkedHashMap<>();
        TypeProperty intProperty = new TypeProperty();
        intProperty.setType("integer");
        itemsProperties.put(PATH, intProperty);

        Items items = new Items();
        items.setProperties(itemsProperties);

        TypeProperty arrayProperty = new TypeProperty();
        arrayProperty.setIndexHint(ImmutableMap.of("type","nested"));
        arrayProperty.setType("array");
        arrayProperty.setItems(items);

        Map<String, TypeProperty> allOfItemProperties = new LinkedHashMap<>();
        allOfItemProperties.put(PATH,arrayProperty);

        AllOfItem allOfItem = new AllOfItem();
        allOfItem.setProperties(allOfItemProperties);

        String res = new PropertiesProcessor(Mockito.mock(Definitions.class), schemaConverterConfig)
            .processItem(allOfItem).map(Object::toString).reduce("", String::concat);
        assertEquals("{path="+ PATH + ", kind=nested, properties=[{path="+ PATH + ", kind=int}]}",res);
    }

    @Test
    public void should_return_flattened_array_without_processing(){
        JaxRsDpsLog log = Mockito.mock(JaxRsDpsLog.class);

        Map<String, TypeProperty> itemsProperties = new LinkedHashMap<>();
        TypeProperty intProperty = new TypeProperty();
        intProperty.setType("integer");
        itemsProperties.put(PATH, intProperty);

        Items items = new Items();
        items.setProperties(itemsProperties);

        TypeProperty arrayProperty = new TypeProperty();
        arrayProperty.setIndexHint(ImmutableMap.of("type","flattened"));
        arrayProperty.setType("array");
        arrayProperty.setItems(items);

        Map<String, TypeProperty> allOfItemProperties = new LinkedHashMap<>();
        allOfItemProperties.put(PATH,arrayProperty);

        AllOfItem allOfItem = new AllOfItem();
        allOfItem.setProperties(allOfItemProperties);

        String res = new PropertiesProcessor(Mockito.mock(Definitions.class), schemaConverterConfig)
            .processItem(allOfItem).map(Object::toString).reduce("", String::concat);
        assertEquals("{path="+ PATH + ", kind=flattened}",res);
    }

    @Test
    public void should_return_object_array_without_hints_in_schema_without_processing(){
        JaxRsDpsLog log = Mockito.mock(JaxRsDpsLog.class);

        Map<String, TypeProperty> itemsProperties = new LinkedHashMap<>();
        TypeProperty intProperty = new TypeProperty();
        intProperty.setType("integer");
        itemsProperties.put(PATH, intProperty);

        Items items = new Items();
        items.setProperties(itemsProperties);

        TypeProperty arrayProperty = new TypeProperty();
        arrayProperty.setType("array");
        arrayProperty.setItems(items);

        Map<String, TypeProperty> allOfItemProperties = new LinkedHashMap<>();
        allOfItemProperties.put(PATH,arrayProperty);

        AllOfItem allOfItem = new AllOfItem();
        allOfItem.setProperties(allOfItemProperties);

        String res = new PropertiesProcessor(Mockito.mock(Definitions.class), schemaConverterConfig)
            .processItem(allOfItem).map(Object::toString).reduce("", String::concat);
        assertEquals("{path="+ PATH + ", kind=[]object}",res);
    }

    @Test
    public void should_process_not_object_array_type(){
        JaxRsDpsLog log = Mockito.mock(JaxRsDpsLog.class);

        Items items = new Items();
        items.setType("integer");

        TypeProperty arrayProperty = new TypeProperty();
        arrayProperty.setType("array");
        arrayProperty.setItems(items);

        Map<String, TypeProperty> allOfItemProperties = new LinkedHashMap<>();
        allOfItemProperties.put(PATH,arrayProperty);

        AllOfItem allOfItem = new AllOfItem();
        allOfItem.setProperties(allOfItemProperties);

        String res = new PropertiesProcessor(Mockito.mock(Definitions.class), schemaConverterConfig)
            .processItem(allOfItem).map(Object::toString).reduce("", String::concat);
        assertEquals("{path="+ PATH + ", kind=[]int}",res);
    }
}
