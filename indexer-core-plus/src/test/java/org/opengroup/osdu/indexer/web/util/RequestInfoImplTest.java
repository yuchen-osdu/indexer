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
package org.opengroup.osdu.indexer.web.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.search.DeploymentEnvironment;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.IAuthorizationService;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class RequestInfoImplTest {

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private TenantInfo tenantInfo;

    @Mock
    private IndexerConfigurationProperties properties;

    @Mock
    private IAuthorizationService authorizationService;

    @InjectMocks
    private RequestInfoImpl requestInfo;

    private Map<String, String> headersMap;

    @BeforeEach
    void setUp() {
        headersMap = new HashMap<>();
        headersMap.put("Content-Type", "application/json");
        headersMap.put("partition-id", "test-partition");
    }

    @Test
    void testGetHeaders_shouldDelegateAllHeaderMethodsToDpsHeaders() {
        // Arrange
        String expectedPartitionId = "test-partition-123";
        when(dpsHeaders.getPartitionId()).thenReturn(expectedPartitionId);
        when(dpsHeaders.getHeaders()).thenReturn(headersMap);

        // Assert - getHeaders returns the injected DpsHeaders instance
        assertEquals(dpsHeaders, requestInfo.getHeaders());

        // Assert - getPartitionId delegates to dpsHeaders
        assertEquals(expectedPartitionId, requestInfo.getPartitionId());
        verify(dpsHeaders).getPartitionId();

        // Assert - getHeadersMap delegates to dpsHeaders
        assertEquals(headersMap, requestInfo.getHeadersMap());
        verify(dpsHeaders).getHeaders();
    }

    @Test
    void testGetHeadersMapWithDwdAuthZ_LocalEnvironment() {
        // Arrange
        String authToken = "Bearer test-token";
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        when(dpsHeaders.getAuthorization()).thenReturn(authToken);
        when(dpsHeaders.getUserEmail()).thenReturn("test@example.com");
        when(dpsHeaders.getHeaders()).thenReturn(headersMap);

        // Act
        Map<String, String> result = requestInfo.getHeadersMapWithDwdAuthZ();

        // Assert
        assertNotNull(result);
        verify(dpsHeaders).put(DpsHeaders.AUTHORIZATION, authToken);
        verify(dpsHeaders).getHeaders();
    }

    @Test
    void testGetHeadersMapWithDwdAuthZ_CloudEnvironment() {
        // Arrange
        String idToken = "cloud-id-token";
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.CLOUD);
        when(tokenProvider.getIdToken()).thenReturn(idToken);
        when(dpsHeaders.getHeaders()).thenReturn(headersMap);

        // Act
        Map<String, String> result = requestInfo.getHeadersMapWithDwdAuthZ();

        // Assert
        assertNotNull(result);
        verify(dpsHeaders).put(DpsHeaders.AUTHORIZATION, "Bearer " + idToken);
        verify(tokenProvider).getIdToken();
        verify(dpsHeaders).getHeaders();
    }

    @Test
    void testGetHeadersWithDwdAuthZ_LocalEnvironment() {
        // Arrange
        String authToken = "Bearer test-token";
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        when(dpsHeaders.getAuthorization()).thenReturn(authToken);
        when(dpsHeaders.getUserEmail()).thenReturn("test@example.com");

        // Act
        DpsHeaders result = requestInfo.getHeadersWithDwdAuthZ();

        // Assert
        assertNotNull(result);
        assertEquals(dpsHeaders, result);
        verify(dpsHeaders).put(DpsHeaders.AUTHORIZATION, authToken);
    }

    @Test
    void testGetHeadersWithDwdAuthZ_CloudEnvironment() {
        // Arrange
        String idToken = "cloud-id-token";
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.CLOUD);
        when(tokenProvider.getIdToken()).thenReturn(idToken);

        // Act
        DpsHeaders result = requestInfo.getHeadersWithDwdAuthZ();

        // Assert
        assertNotNull(result);
        assertEquals(dpsHeaders, result);
        verify(dpsHeaders).put(DpsHeaders.AUTHORIZATION, "Bearer " + idToken);
        verify(tokenProvider).getIdToken();
    }

    @Test
    void testIsCronRequest() {
        // Act
        boolean result = requestInfo.isCronRequest();

        // Assert
        assertFalse(result);
    }

    @Test
    void testIsTaskQueueRequest() {
        // Act
        boolean result = requestInfo.isTaskQueueRequest();

        // Assert
        assertFalse(result);
    }

    @Test
    void testCheckOrGetAuthorizationHeader_LocalEnvironment_ValidHeaders() {
        // Arrange
        String authToken = "Bearer valid-token";
        String userEmail = "user@example.com";
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        when(dpsHeaders.getAuthorization()).thenReturn(authToken);
        when(dpsHeaders.getUserEmail()).thenReturn(userEmail);

        // Act
        String result = requestInfo.checkOrGetAuthorizationHeader();

        // Assert
        assertEquals(authToken, result);
        verify(dpsHeaders).getAuthorization();
        verify(dpsHeaders).getUserEmail();
    }

    @Test
    void testCheckOrGetAuthorizationHeader_LocalEnvironment_EmptyAuthHeader() {
        // Arrange
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        when(dpsHeaders.getAuthorization()).thenReturn("");

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            requestInfo.checkOrGetAuthorizationHeader();
        });

        assertEquals(HttpStatus.SC_UNAUTHORIZED, exception.getError().getCode());
        assertEquals("Authorization token cannot be empty", exception.getError().getMessage());
        assertEquals("Invalid authorization header", exception.getError().getReason());
    }

    @Test
    void testCheckOrGetAuthorizationHeader_LocalEnvironment_NullAuthHeader() {
        // Arrange
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        when(dpsHeaders.getAuthorization()).thenReturn(null);

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            requestInfo.checkOrGetAuthorizationHeader();
        });

        assertEquals(HttpStatus.SC_UNAUTHORIZED, exception.getError().getCode());
        assertEquals("Authorization token cannot be empty", exception.getError().getMessage());
        assertEquals("Invalid authorization header", exception.getError().getReason());
    }

    @ParameterizedTest
    @MethodSource("provideCheckOrGetAuthorizationHeaderTestCases")
    void testCheckOrGetAuthorizationHeader(
            DeploymentEnvironment environment,
            String authToken,
            String userEmail,
            String expectedIdToken,
            boolean shouldThrowException,
            Integer expectedErrorCode,
            String expectedErrorMessage,
            String expectedErrorReason) {

        // Arrange
        when(properties.getDeploymentEnvironment()).thenReturn(environment);

        if (environment == DeploymentEnvironment.LOCAL) {
            when(dpsHeaders.getAuthorization()).thenReturn(authToken);
            when(dpsHeaders.getUserEmail()).thenReturn(userEmail);
        } else if (environment == DeploymentEnvironment.CLOUD) {
            when(tokenProvider.getIdToken()).thenReturn(expectedIdToken);
        }

        // Act & Assert
        if (shouldThrowException) {
            AppException exception = assertThrows(AppException.class, () -> {
                requestInfo.checkOrGetAuthorizationHeader();
            });

            assertEquals(expectedErrorCode, exception.getError().getCode());
            assertEquals(expectedErrorMessage, exception.getError().getMessage());
            assertEquals(expectedErrorReason, exception.getError().getReason());
        } else {
            String result = requestInfo.checkOrGetAuthorizationHeader();
            assertEquals("Bearer " + expectedIdToken, result);
            verify(tokenProvider).getIdToken();
            verify(dpsHeaders, never()).getAuthorization();
            verify(dpsHeaders, never()).getUserEmail();
        }
    }

    private static Stream<Arguments> provideCheckOrGetAuthorizationHeaderTestCases() {
        return Stream.of(
                // Local environment - empty user email
                Arguments.of(
                        DeploymentEnvironment.LOCAL,
                        "Bearer valid-token",
                        "",
                        null,
                        true,
                        HttpStatus.SC_UNAUTHORIZED,
                        "User header cannot be empty",
                        "Invalid user header"
                ),
                // Local environment - null user email
                Arguments.of(
                        DeploymentEnvironment.LOCAL,
                        "Bearer valid-token",
                        null,
                        null,
                        true,
                        HttpStatus.SC_UNAUTHORIZED,
                        "User header cannot be empty",
                        "Invalid user header"
                ),
                // Cloud environment
                Arguments.of(
                        DeploymentEnvironment.CLOUD,
                        null,
                        null,
                        "generated-id-token",
                        false,
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    void testCheckOrGetAuthorizationHeader_MultipleCloudCalls() {
        // Arrange - Test multiple calls to cloud environment
        String idToken = "generated-id-token";
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.CLOUD);
        when(tokenProvider.getIdToken()).thenReturn(idToken);

        // Act
        String result1 = requestInfo.checkOrGetAuthorizationHeader();
        String result2 = requestInfo.checkOrGetAuthorizationHeader();

        // Assert
        assertEquals("Bearer " + idToken, result1);
        assertEquals("Bearer " + idToken, result2);
        verify(tokenProvider, times(2)).getIdToken();
    }

    @Test
    void testGetHeadersWithDwdAuthZ_IntegrationFlow() {
        // Arrange
        String authToken = "Bearer integration-token";
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        when(dpsHeaders.getAuthorization()).thenReturn(authToken);
        when(dpsHeaders.getUserEmail()).thenReturn("integration@example.com");

        // Act
        DpsHeaders result = requestInfo.getHeadersWithDwdAuthZ();

        // Assert
        assertNotNull(result);
        verify(dpsHeaders).put(DpsHeaders.AUTHORIZATION, authToken);
    }

    @Test
    void testGetPartitionId_NullValue() {
        // Arrange
        when(dpsHeaders.getPartitionId()).thenReturn(null);

        // Act
        String result = requestInfo.getPartitionId();

        // Assert
        assertNull(result);
        verify(dpsHeaders).getPartitionId();
    }

    @Test
    void testGetHeadersMap_EmptyMap() {
        // Arrange
        Map<String, String> emptyMap = new HashMap<>();
        when(dpsHeaders.getHeaders()).thenReturn(emptyMap);

        // Act
        Map<String, String> result = requestInfo.getHeadersMap();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(dpsHeaders).getHeaders();
    }

    @Test
    void testCheckOrGetAuthorizationHeader_LocalEnvironment_WhitespaceAuthHeader() {
        String authToken = "   ";
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        when(dpsHeaders.getAuthorization()).thenReturn(authToken);
        when(dpsHeaders.getUserEmail()).thenReturn("");

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            requestInfo.checkOrGetAuthorizationHeader();
        });

        assertEquals(HttpStatus.SC_UNAUTHORIZED, exception.getError().getCode());
        assertEquals("User header cannot be empty", exception.getError().getMessage());
        assertEquals("Invalid user header", exception.getError().getReason());
    }

    @Test
    void testCheckOrGetAuthorizationHeader_LocalEnvironment_WhitespaceUserEmail() {
        String authToken = "Bearer valid-token";
        String whitespaceEmail = "   ";
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        when(dpsHeaders.getAuthorization()).thenReturn(authToken);
        when(dpsHeaders.getUserEmail()).thenReturn(whitespaceEmail);

        // Act
        String result = requestInfo.checkOrGetAuthorizationHeader();

        // Assert - Should return the auth token without throwing exception
        assertEquals(authToken, result);
        verify(dpsHeaders).getAuthorization();
        verify(dpsHeaders).getUserEmail();
    }
}
