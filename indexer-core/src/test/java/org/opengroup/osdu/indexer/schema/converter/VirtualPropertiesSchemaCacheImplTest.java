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

package org.opengroup.osdu.indexer.schema.converter;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opengroup.osdu.indexer.provider.interfaces.ISchemaCache;
import org.opengroup.osdu.indexer.schema.converter.tags.Priority;
import org.opengroup.osdu.indexer.schema.converter.tags.VirtualProperties;
import org.opengroup.osdu.indexer.schema.converter.tags.VirtualProperty;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.LinkedList;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
public class VirtualPropertiesSchemaCacheImplTest {
    @Mock
    private ISchemaCache schemaCache;
    @InjectMocks
    private VirtualPropertiesSchemaCacheImpl sut;

    private VirtualProperties virtualProperties;
    private Gson gson = new Gson();

    @Before
    public void setup() {
        virtualProperties = new VirtualProperties();
        VirtualProperty virtualProperty = new VirtualProperty();
        virtualProperty.setType("object");
        List<Priority> priorityList = new LinkedList<Priority>();
        Priority priority = new Priority();
        priority.setType("object");
        priority.setPath("data.ProjectedBottomHoleLocation");
        priorityList.add(priority);
        priority = new Priority();
        priority.setType("object");
        priority.setPath("data.GeographicBottomHoleLocation");
        priorityList.add(priority);
        priority = new Priority();
        priority.setType("object");
        priority.setPath("data.SpatialLocation");
        priorityList.add(priority);
        virtualProperty.setPriorities(priorityList);
        virtualProperties.add("data.VirtualProperties.DefaultLocation", virtualProperty);
    }

    @Test
    public void put_valid_key_value() {
        this.sut.put("wellbore", this.virtualProperties);
        verify(this.schemaCache, times(1)).put(eq("wellbore_virtual_properties"), Mockito.anyString());
    }

    @Test
    public void put_null_empty_key() {
        this.sut.put(null, this.virtualProperties);
        this.sut.put("", this.virtualProperties);
        verify(this.schemaCache, times(0)).put(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void put_null_value() {
        this.sut.put("wellbore", null);
        verify(this.schemaCache, times(0)).put(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void get_with_valid_key_return_valid_value() {
        when(this.schemaCache.get("wellbore_virtual_properties")).thenReturn(gson.toJson(this.virtualProperties));
        VirtualProperties virtualProperties = this.sut.get("wellbore");

        Assert.assertNotNull(virtualProperties);
        Assert.assertNotNull(virtualProperties.getProperties());
        VirtualProperty virtualProperty = virtualProperties.getProperties().get("data.VirtualProperties.DefaultLocation");
        Assert.assertNotNull(virtualProperty);
        Assert.assertEquals(3, virtualProperty.getPriorities().size());
    }

    @Test
    public void get_with_none_existing_key() {
        when(this.schemaCache.get("wellbore_virtual_properties")).thenReturn(gson.toJson(this.virtualProperties));
        VirtualProperties virtualProperties = this.sut.get("well");
        Assert.assertNull(virtualProperties);
        verify(this.schemaCache, times(1)).get(Mockito.anyString());
    }

    @Test
    public void get_with_null_empty_key() {
        when(this.schemaCache.get("wellbore_virtual_properties")).thenReturn(gson.toJson(this.virtualProperties));
        VirtualProperties virtualProperties = this.sut.get(null);
        Assert.assertNull(virtualProperties);
        virtualProperties = this.sut.get("");
        Assert.assertNull(virtualProperties);
        verify(this.schemaCache, times(0)).get(Mockito.anyString());
    }

    @Test
    public void delete_with_existing_key() {
        when(this.schemaCache.get("wellbore_virtual_properties")).thenReturn(gson.toJson(this.virtualProperties));
        this.sut.delete("wellbore");
        verify(this.schemaCache, times(1)).delete(eq("wellbore_virtual_properties"));
    }

    @Test
    public void delete_with_none_existing_key() {
        when(this.schemaCache.get("well_virtual_properties")).thenReturn(null);
        this.sut.delete("well");
        verify(this.schemaCache, times(0)).delete(Mockito.anyString());
    }

    @Test
    public void delete_with_null_empty_key() {
        when(this.schemaCache.get(Mockito.anyString())).thenReturn(gson.toJson(this.virtualProperties));
        this.sut.delete(null);
        this.sut.delete("");
        verify(this.schemaCache, times(0)).delete(Mockito.anyString());
    }

    @Test
    public void clearAll() {
        this.sut.clearAll();
        verify(this.schemaCache, times(1)).clearAll();
    }

}
