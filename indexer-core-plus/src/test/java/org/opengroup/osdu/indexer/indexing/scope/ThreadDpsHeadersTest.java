/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.opengroup.osdu.indexer.indexing.scope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;

@ExtendWith(MockitoExtension.class)
@DisplayName("ThreadDpsHeaders Tests")
class ThreadDpsHeadersTest {

    @Mock
    private TokenProvider tokenProvider;

    private ThreadDpsHeaders threadDpsHeaders;

    @BeforeEach
    void setUp() {
        threadDpsHeaders = new ThreadDpsHeaders(tokenProvider);
    }

    @Test
    @DisplayName("Should set authorization header with Bearer token from token provider")
    void testSetThreadContext_ShouldSetAuthorizationHeader() {
        // Arrange
        String expectedToken = "test-id-token-12345";
        when(tokenProvider.getIdToken()).thenReturn(expectedToken);
        Map<String, String> headers = new HashMap<>();

        // Act
        threadDpsHeaders.setThreadContext(headers);

        // Assert
        verify(tokenProvider, times(1)).getIdToken();
        String authHeader = threadDpsHeaders.getAuthorization();
        assertNotNull(authHeader);
        assertTrue(authHeader.contains("Bearer"));
        assertTrue(authHeader.contains(expectedToken));
    }

    @Test
    @DisplayName("Should add all headers from provided map")
    void testSetThreadContext_ShouldAddHeadersFromMap() {
        // Arrange
        String token = "test-token";
        when(tokenProvider.getIdToken()).thenReturn(token);

        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.DATA_PARTITION_ID, "opendes");
        headers.put(DpsHeaders.CORRELATION_ID, "correlation-123");

        // Act
        threadDpsHeaders.setThreadContext(headers);

        // Assert
        verify(tokenProvider, times(1)).getIdToken();
        assertEquals("opendes", threadDpsHeaders.getPartitionId());
        assertEquals("correlation-123", threadDpsHeaders.getCorrelationId());
    }

    @Test
    @DisplayName("Should handle empty headers map")
    void testSetThreadContext_WithEmptyMap() {
        // Arrange
        String token = "test-token";
        when(tokenProvider.getIdToken()).thenReturn(token);
        Map<String, String> emptyHeaders = new HashMap<>();

        // Act
        threadDpsHeaders.setThreadContext(emptyHeaders);

        // Assert
        verify(tokenProvider, times(1)).getIdToken();
        assertNotNull(threadDpsHeaders.getAuthorization());
        assertTrue(threadDpsHeaders.getAuthorization().startsWith("Bearer "));
    }

    @Test
    @DisplayName("Should handle null token from token provider")
    void testSetThreadContext_WithNullToken() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(null);
        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.DATA_PARTITION_ID, "test-partition");

        // Act
        threadDpsHeaders.setThreadContext(headers);

        // Assert
        verify(tokenProvider, times(1)).getIdToken();
        assertEquals("Bearer null", threadDpsHeaders.getAuthorization());
        assertEquals("test-partition", threadDpsHeaders.getPartitionId());
    }

    @Test
    @DisplayName("Should invoke token provider when setting thread context")
    void testSetThreadContext_ShouldInvokeTokenProvider() {
        // Arrange
        String expectedToken = "secure-token-xyz";
        when(tokenProvider.getIdToken()).thenReturn(expectedToken);
        Map<String, String> headers = new HashMap<>();

        // Act
        threadDpsHeaders.setThreadContext(headers);

        // Assert
        verify(tokenProvider, times(1)).getIdToken();
    }

    @Test
    @DisplayName("Should handle multiple calls to setThreadContext")
    void testSetThreadContext_MultipleCalls() {
        // Arrange
        when(tokenProvider.getIdToken())
                .thenReturn("token1")
                .thenReturn("token2");

        Map<String, String> headers1 = new HashMap<>();
        headers1.put(DpsHeaders.DATA_PARTITION_ID, "partition1");

        Map<String, String> headers2 = new HashMap<>();
        headers2.put(DpsHeaders.DATA_PARTITION_ID, "partition2");

        // Act
        threadDpsHeaders.setThreadContext(headers1);
        String firstAuth = threadDpsHeaders.getAuthorization();
        String firstPartition = threadDpsHeaders.getPartitionId();

        threadDpsHeaders.setThreadContext(headers2);
        String secondAuth = threadDpsHeaders.getAuthorization();
        String secondPartition = threadDpsHeaders.getPartitionId();

        // Assert
        verify(tokenProvider, times(2)).getIdToken();
        assertEquals("Bearer token1", firstAuth);
        assertEquals("partition1", firstPartition);
        assertEquals("Bearer token2", secondAuth);
        assertEquals("partition2", secondPartition);
    }

    @Test
    @DisplayName("Should construct ThreadDpsHeaders with TokenProvider dependency")
    void testConstructor() {
        // Act
        ThreadDpsHeaders newInstance = new ThreadDpsHeaders(tokenProvider);

        // Assert
        assertNotNull(newInstance);
    }

    @Test
    @DisplayName("Should set authorization with correct Bearer prefix")
    void testSetThreadContext_BearerPrefix() {
        // Arrange
        String token = "my-test-token-123";
        when(tokenProvider.getIdToken()).thenReturn(token);
        Map<String, String> headers = new HashMap<>();

        // Act
        threadDpsHeaders.setThreadContext(headers);

        // Assert
        String authorization = threadDpsHeaders.getAuthorization();
        assertEquals("Bearer my-test-token-123", authorization);
    }

    @Test
    @DisplayName("Should handle headers with partition ID")
    void testSetThreadContext_WithPartitionId() {
        // Arrange
        String token = "test-token";
        when(tokenProvider.getIdToken()).thenReturn(token);

        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.DATA_PARTITION_ID, "opendes-test");

        // Act
        threadDpsHeaders.setThreadContext(headers);

        // Assert
        assertEquals("opendes-test", threadDpsHeaders.getPartitionId());
    }

    @Test
    @DisplayName("Should handle headers with correlation ID")
    void testSetThreadContext_WithCorrelationId() {
        // Arrange
        String token = "test-token";
        when(tokenProvider.getIdToken()).thenReturn(token);

        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.CORRELATION_ID, "corr-id-12345");

        // Act
        threadDpsHeaders.setThreadContext(headers);

        // Assert
        assertEquals("corr-id-12345", threadDpsHeaders.getCorrelationId());
    }

    @Test
    @DisplayName("Should handle all standard DPS headers")
    void testSetThreadContext_WithAllStandardHeaders() {
        // Arrange
        String token = "test-token";
        when(tokenProvider.getIdToken()).thenReturn(token);

        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.DATA_PARTITION_ID, "test-partition");
        headers.put(DpsHeaders.CORRELATION_ID, "test-correlation");
        headers.put(DpsHeaders.USER_EMAIL, "test@example.com");

        // Act
        threadDpsHeaders.setThreadContext(headers);

        // Assert
        verify(tokenProvider, times(1)).getIdToken();
        assertEquals("test-partition", threadDpsHeaders.getPartitionId());
        assertEquals("test-correlation", threadDpsHeaders.getCorrelationId());
        assertEquals("test@example.com", threadDpsHeaders.getUserEmail());
    }
}
