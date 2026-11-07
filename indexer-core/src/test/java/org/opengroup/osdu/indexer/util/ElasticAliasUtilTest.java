/*
 * Copyright 2017-2025, The Open Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.indexer.util;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.elasticsearch.indices.AliasDefinition;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(SpringRunner.class)
public class ElasticAliasUtilTest {

    @Mock
    private JaxRsDpsLog log;

    @InjectMocks
    private ElasticAliasUtil sut;

    @Mock
    private ElasticsearchClient client;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Before
    public void setup() {
        initMocks(this);
        when(client.indices()).thenReturn(indicesClient);
    }

    @Test
    public void testGetPhysicalIndexNameForCreation_AlwaysReturnsVersioned() {
        assertEquals("opendes-osdu-file-1.0.0-r1", sut.getPhysicalIndexNameForCreation("opendes-osdu-file-1.0.0"));
    }

    @Test
    public void testGetPhysicalIndexNameForCreation_WithComplexIndexName_ReturnsCorrectFormat() {
        assertEquals("simple-r1", sut.getPhysicalIndexNameForCreation("simple"));
        assertEquals("tenant1-osdu-file-1.0.0-r1", sut.getPhysicalIndexNameForCreation("tenant1-osdu-file-1.0.0"));
        assertEquals("very-long-complex-index-name-with-many-parts-1.2.3-r1",
                     sut.getPhysicalIndexNameForCreation("very-long-complex-index-name-with-many-parts-1.2.3"));
    }

    @Test
    public void testIsAlias_WhenAliasExists_ReturnsTrue() throws IOException {
        String aliasName = "opendes-osdu-file-1.0.0";
        GetAliasResponse response = mock(GetAliasResponse.class);
        Map<String, IndexAliases> result = new HashMap<>();
        IndexAliases indexAliases = mock(IndexAliases.class);

        Map<String, AliasDefinition> aliases = new HashMap<>();
        aliases.put(aliasName, AliasDefinition.of(builder -> builder));
        when(indexAliases.aliases()).thenReturn(aliases);

        result.put("opendes-osdu-file-1.0.0-r1", indexAliases);
        when(response.result()).thenReturn(result);
        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenReturn(response);

        assertTrue(sut.isAlias(client, aliasName));
    }

    @Test
    public void testIsAlias_WhenAliasDoesNotExist_ReturnsFalse() throws IOException {
        String aliasName = "opendes-osdu-file-1.0.0";
        ElasticsearchException exception = new ElasticsearchException("_doc",
            ErrorResponse.of(es -> es.status(404).error(
                ErrorCause.of(ec -> ec.type("not_found").reason("Alias not found")))));
        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenThrow(exception);

        assertFalse(sut.isAlias(client, aliasName));
    }

    @Test
    public void testIsAlias_WithElasticsearchException_RethrowsException() throws IOException {
        String aliasName = "opendes-osdu-file-1.0.0";
        ElasticsearchException exception = new ElasticsearchException("_doc",
            ErrorResponse.of(es -> es.status(500).error(
                ErrorCause.of(ec -> ec.type("internal_error").reason("Internal server error")))));
        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenThrow(exception);

        try {
            sut.isAlias(client, aliasName);
            fail("Should have thrown ElasticsearchException");
        } catch (ElasticsearchException e) {
            assertEquals(500, e.status());
        }
    }

    @Test
    public void testResolveAliasToPhysicalIndexes_ReturnsListOfIndexes() throws IOException {
        String aliasName = "opendes-osdu-file-1.0.0";
        GetAliasResponse response = mock(GetAliasResponse.class);
        Map<String, IndexAliases> result = new HashMap<>();

        IndexAliases indexAliases1 = mock(IndexAliases.class);
        Map<String, AliasDefinition> aliases1 = new HashMap<>();
        aliases1.put(aliasName, AliasDefinition.of(builder -> builder));
        when(indexAliases1.aliases()).thenReturn(aliases1);
        result.put("opendes-osdu-file-1.0.0-r1", indexAliases1);

        when(response.result()).thenReturn(result);
        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenReturn(response);

        List<String> physicalIndexes = sut.resolveAliasToPhysicalIndexes(client, aliasName);

        assertEquals(1, physicalIndexes.size());
        assertTrue(physicalIndexes.contains("opendes-osdu-file-1.0.0-r1"));
    }

    @Test
    public void testResolveAliasToPhysicalIndexes_WhenAliasNotFound_ReturnsEmptyList() throws IOException {
        String aliasName = "non-existent-alias";
        ElasticsearchException exception = new ElasticsearchException("_doc",
            ErrorResponse.of(es -> es.status(404).error(
                ErrorCause.of(ec -> ec.type("not_found").reason("Alias not found")))));
        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenThrow(exception);

        List<String> physicalIndexes = sut.resolveAliasToPhysicalIndexes(client, aliasName);
        assertTrue(physicalIndexes.isEmpty());
    }

    @Test
    public void testResolveAliasToPhysicalIndexes_WithMultiplePhysicalIndexes() throws IOException {
        String aliasName = "opendes-osdu-file-1.0.0";
        GetAliasResponse response = mock(GetAliasResponse.class);
        Map<String, IndexAliases> result = new HashMap<>();

        IndexAliases indexAliases1 = mock(IndexAliases.class);
        Map<String, AliasDefinition> aliases1 = new HashMap<>();
        aliases1.put(aliasName, AliasDefinition.of(builder -> builder));
        when(indexAliases1.aliases()).thenReturn(aliases1);
        result.put("opendes-osdu-file-1.0.0-r1", indexAliases1);

        IndexAliases indexAliases2 = mock(IndexAliases.class);
        Map<String, AliasDefinition> aliases2 = new HashMap<>();
        aliases2.put(aliasName, AliasDefinition.of(builder -> builder));
        when(indexAliases2.aliases()).thenReturn(aliases2);
        result.put("opendes-osdu-file-1.0.0-r2", indexAliases2);

        when(response.result()).thenReturn(result);
        when(indicesClient.getAlias(any(GetAliasRequest.class))).thenReturn(response);

        List<String> physicalIndexes = sut.resolveAliasToPhysicalIndexes(client, aliasName);
        assertEquals(2, physicalIndexes.size());
    }

    @Test
    public void testCreateAlias_Success_ReturnsTrue() throws IOException {
        PutAliasResponse response = mock(PutAliasResponse.class);
        when(response.acknowledged()).thenReturn(true);
        when(indicesClient.putAlias(any(PutAliasRequest.class))).thenReturn(response);

        boolean created = sut.createAlias(client, "opendes-osdu-file-1.0.0", "opendes-osdu-file-1.0.0-r1");
        assertTrue(created);
        verify(log).info(contains("Created alias"));
    }

    @Test
    public void testCreateAlias_Failure_ReturnsFalse() throws IOException {
        PutAliasResponse response = mock(PutAliasResponse.class);
        when(response.acknowledged()).thenReturn(false);
        when(indicesClient.putAlias(any(PutAliasRequest.class))).thenReturn(response);

        boolean created = sut.createAlias(client, "opendes-osdu-file-1.0.0", "opendes-osdu-file-1.0.0-r1");
        assertFalse(created);
    }

    @Test
    public void testCreateAlias_WithNullOrEmptyParameters_HandlesGracefully() throws IOException {
        assertFalse(sut.createAlias(client, null, "physical-index"));
        assertFalse(sut.createAlias(client, "", "physical-index"));
        assertFalse(sut.createAlias(client, "alias-name", null));
        assertFalse(sut.createAlias(client, "alias-name", ""));
        verify(indicesClient, never()).putAlias(any(PutAliasRequest.class));
    }

    @Test
    public void testCreateAlias_WithNetworkTimeout_ThrowsException() throws IOException {
        ElasticsearchException networkException = new ElasticsearchException("test",
            ErrorResponse.of(er -> er.status(500).error(
                ErrorCause.of(ec -> ec.type("connection_timeout").reason("Connection timeout")))));

        when(indicesClient.putAlias(any(PutAliasRequest.class))).thenThrow(networkException);

        try {
            sut.createAlias(client, "opendes-osdu-file-1.0.0", "opendes-osdu-file-1.0.0-r1");
            fail("Should have thrown ElasticsearchException");
        } catch (ElasticsearchException e) {
            assertEquals(500, e.status());
        }
    }
}
