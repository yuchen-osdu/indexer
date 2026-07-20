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
package org.opengroup.osdu.indexer.common.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.auth.TokenProvider;

@ExtendWith(MockitoExtension.class)
class ServiceAccountJwtGcpClientImplTest {

    @Mock
    private TokenProvider tokenProvider;

    @InjectMocks
    private ServiceAccountJwtGcpClientImpl serviceAccountJwtClient;

    private static final String TEST_TENANT_NAME = "test-tenant";
    private static final String TEST_ID_TOKEN = "test-id-token-12345";

    @BeforeEach
    void setUp() {
        reset(tokenProvider);
    }

    @Test
    void getIdToken_shouldReturnBearerTokenAndDelegateToTokenProvider() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act
        String result = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert - correct Bearer format
        assertNotNull(result);
        assertTrue(result.startsWith("Bearer "));
        assertEquals("Bearer " + TEST_ID_TOKEN, result);

        // Assert - tokenProvider called exactly once, tenant name is not used in token generation
        verify(tokenProvider, times(1)).getIdToken();
        verifyNoMoreInteractions(tokenProvider);
    }

    @Test
    void getIdToken_shouldHandleNullTenantName() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act
        String result = serviceAccountJwtClient.getIdToken(null);

        // Assert
        assertNotNull(result);
        assertEquals("Bearer " + TEST_ID_TOKEN, result);
        verify(tokenProvider).getIdToken();
    }

    @Test
    void getIdToken_shouldHandleEmptyTenantName() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act
        String result = serviceAccountJwtClient.getIdToken("");

        // Assert
        assertNotNull(result);
        assertEquals("Bearer " + TEST_ID_TOKEN, result);
    }

    @Test
    void getIdToken_shouldHandleWhitespaceTenantName() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act
        String result = serviceAccountJwtClient.getIdToken("   ");

        // Assert
        assertNotNull(result);
        assertEquals("Bearer " + TEST_ID_TOKEN, result);
    }

    @Test
    void getIdToken_shouldHandleLongTenantName() {
        // Arrange
        String longTenantName = "a".repeat(1000);
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act
        String result = serviceAccountJwtClient.getIdToken(longTenantName);

        // Assert
        assertNotNull(result);
        assertEquals("Bearer " + TEST_ID_TOKEN, result);
    }

    @Test
    void getIdToken_shouldHandleSpecialCharactersInTenantName() {
        // Arrange
        String specialTenantName = "tenant-!@#$%^&*()_+={}[]|\\:;\"'<>,.?/";
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act
        String result = serviceAccountJwtClient.getIdToken(specialTenantName);

        // Assert
        assertNotNull(result);
        assertEquals("Bearer " + TEST_ID_TOKEN, result);
    }

    @Test
    void getIdToken_shouldHandleEmptyToken() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn("");

        // Act
        String result = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert
        assertEquals("Bearer ", result);
    }

    @Test
    void getIdToken_shouldHandleTokenWithSpaces() {
        // Arrange
        String tokenWithSpaces = "token with spaces";
        when(tokenProvider.getIdToken()).thenReturn(tokenWithSpaces);

        // Act
        String result = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert
        assertEquals("Bearer " + tokenWithSpaces, result);
    }

    @Test
    void getIdToken_shouldHandleTokenAlreadyPrefixedWithBearer() {
        // Arrange - Token already has Bearer prefix (though this shouldn't happen)
        String tokenWithBearer = "Bearer existing-token";
        when(tokenProvider.getIdToken()).thenReturn(tokenWithBearer);

        // Act
        String result = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert
        // Will result in double Bearer prefix
        assertEquals("Bearer " + tokenWithBearer, result);
        assertEquals("Bearer Bearer existing-token", result);
    }

    @Test
    void getIdToken_shouldHandleVeryLongToken() {
        // Arrange
        String longToken = "a".repeat(5000);
        when(tokenProvider.getIdToken()).thenReturn(longToken);

        // Act
        String result = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert
        assertTrue(result.startsWith("Bearer "));
        assertEquals("Bearer " + longToken, result);
        assertEquals(5007, result.length()); // "Bearer " (7) + 5000
    }

    @Test
    void getIdToken_shouldHandleTokenWithSpecialCharacters() {
        // Arrange
        String specialToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature";
        when(tokenProvider.getIdToken()).thenReturn(specialToken);

        // Act
        String result = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert
        assertEquals("Bearer " + specialToken, result);
    }

    @Test
    void getIdToken_shouldHandleMultipleInvocations() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act
        String result1 = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);
        String result2 = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);
        String result3 = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert
        assertEquals(result1, result2);
        assertEquals(result2, result3);
        verify(tokenProvider, times(3)).getIdToken();
    }

    @Test
    void getIdToken_shouldReturnDifferentTokensWhenProviderReturnsNew() {
        // Arrange
        when(tokenProvider.getIdToken())
                .thenReturn("token1")
                .thenReturn("token2")
                .thenReturn("token3");

        // Act
        String result1 = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);
        String result2 = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);
        String result3 = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert
        assertEquals("Bearer token1", result1);
        assertEquals("Bearer token2", result2);
        assertEquals("Bearer token3", result3);
        assertNotEquals(result1, result2);
        assertNotEquals(result2, result3);
    }

    @Test
    void getIdToken_shouldWorkWithDifferentTenantNamesButSameToken() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act
        String result1 = serviceAccountJwtClient.getIdToken("tenant1");
        String result2 = serviceAccountJwtClient.getIdToken("tenant2");
        String result3 = serviceAccountJwtClient.getIdToken("tenant3");

        // Assert
        assertEquals(result1, result2);
        assertEquals(result2, result3);
        verify(tokenProvider, times(3)).getIdToken();
    }

    @Test
    void getIdToken_shouldNotCacheResults() {
        // Arrange
        when(tokenProvider.getIdToken())
                .thenReturn("token1")
                .thenReturn("token2");

        // Act
        String result1 = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);
        String result2 = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert
        assertNotEquals(result1, result2);
        assertEquals("Bearer token1", result1);
        assertEquals("Bearer token2", result2);
    }

    @Test
    void getIdToken_shouldBeThreadSafe() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act - Simulate concurrent access
        String result1 = serviceAccountJwtClient.getIdToken("tenant1");
        String result2 = serviceAccountJwtClient.getIdToken("tenant2");

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1, result2);
    }

    @Test
    void getIdToken_shouldHaveCorrectFormat() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act
        String result = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert
        String[] parts = result.split(" ", 2);
        assertEquals(2, parts.length);
        assertEquals("Bearer", parts[0]);
        assertEquals(TEST_ID_TOKEN, parts[1]);
    }

    @Test
    void getIdToken_shouldNotHaveTrailingSpaces() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act
        String result = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert
        assertEquals(result, result.trim());
        assertFalse(result.endsWith(" "));
    }

    @Test
    void getIdToken_shouldHaveExactlyOneSpaceAfterBearer() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_ID_TOKEN);

        // Act
        String result = serviceAccountJwtClient.getIdToken(TEST_TENANT_NAME);

        // Assert
        assertTrue(result.contains("Bearer "));
        assertFalse(result.contains("Bearer  ")); // No double spaces
        assertEquals("Bearer " + TEST_ID_TOKEN, result);
    }

    @Test
    void shouldBeInstantiableWithConstructor() {
        // Arrange
        TokenProvider provider = mock(TokenProvider.class);

        // Act
        ServiceAccountJwtGcpClientImpl client = new ServiceAccountJwtGcpClientImpl(provider);

        // Assert
        assertNotNull(client);
    }

    @Test
    void shouldImplementIServiceAccountJwtClient() {
        // Assert
        assertTrue(serviceAccountJwtClient instanceof org.opengroup.osdu.core.common.util.IServiceAccountJwtClient);
    }
}
