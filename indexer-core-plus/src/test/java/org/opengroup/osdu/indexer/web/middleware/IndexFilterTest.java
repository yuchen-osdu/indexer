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
package org.opengroup.osdu.indexer.web.middleware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.search.DeploymentEnvironment;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;

@ExtendWith(MockitoExtension.class)
class IndexFilterTest {

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private IRequestInfo requestInfo;

    @Mock
    private IndexerConfigurationProperties properties;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private HttpServletResponse httpServletResponse;

    @Mock
    private FilterChain filterChain;

    @Mock
    private FilterConfig filterConfig;

    @InjectMocks
    private IndexFilter indexFilter;

    private static final String TEST_CORRELATION_ID = "test-correlation-123";
    private static final String TASK_HANDLERS_URI = "/api/indexer/v2/task-handlers/process";
    private static final String CRON_HANDLERS_URI = "/api/indexer/v2/cron-handlers/schedule";
    private static final String REGULAR_URI = "/api/indexer/v2/index";

    @BeforeEach
    void setUp() {
        Mockito.reset(dpsHeaders, requestInfo, properties, httpServletRequest,
                httpServletResponse, filterChain);

        lenient().when(dpsHeaders.getCorrelationId()).thenReturn(TEST_CORRELATION_ID);
    }

    @Test
    void shouldInitAndDestroySuccessfully() {
        // Assert - instance exists before lifecycle calls
        assertNotNull(indexFilter);

        // Act & Assert - init and destroy complete without exception
        indexFilter.init(filterConfig);
        indexFilter.destroy();
    }

    @Test
    void shouldPassRequestAndManageCorrelationIdHeader() throws IOException, ServletException {
        // Arrange - correlation ID missing
        when(httpServletRequest.getMethod()).thenReturn("GET");
        when(httpServletRequest.getRequestURI()).thenReturn(REGULAR_URI);
        when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        // Act
        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Assert - request passes through, correlation ID is added, no task queue check
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verify(httpServletResponse).addHeader(DpsHeaders.CORRELATION_ID, TEST_CORRELATION_ID);
        verify(requestInfo, never()).isTaskQueueRequest();

        // Reset mocks to flip correlation ID state
        reset(httpServletRequest, httpServletResponse, filterChain, requestInfo);

        // Arrange - correlation ID already present
        when(httpServletRequest.getMethod()).thenReturn("POST");
        when(httpServletRequest.getRequestURI()).thenReturn(REGULAR_URI);
        when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn("existing-id");

        // Act
        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Assert - request passes through, existing correlation ID is not overwritten
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verify(httpServletResponse, never()).addHeader(eq(DpsHeaders.CORRELATION_ID), anyString());
    }

    @Test
    void shouldAllowTaskHandlerRequestsInLocalEnvironment() throws IOException, ServletException {
        // Arrange - standard POST to task-handlers in LOCAL
        when(httpServletRequest.getMethod()).thenReturn("POST");
        when(httpServletRequest.getRequestURI()).thenReturn(TASK_HANDLERS_URI);
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        lenient().when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        // Act
        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Assert - passes through without task queue check
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verify(requestInfo, never()).isTaskQueueRequest();

        // Reset mocks to test case-insensitive handling
        reset(httpServletRequest, httpServletResponse, filterChain, requestInfo, properties);

        // Arrange - lowercase method and URI
        when(httpServletRequest.getMethod()).thenReturn("post");
        when(httpServletRequest.getRequestURI()).thenReturn(TASK_HANDLERS_URI.toLowerCase());
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        lenient().when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        // Act
        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Assert - case-insensitive method and URI still passes through
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        // Reset mocks to test uppercase URI
        reset(httpServletRequest, httpServletResponse, filterChain, requestInfo, properties);

        // Arrange - uppercase URI
        when(httpServletRequest.getMethod()).thenReturn("POST");
        when(httpServletRequest.getRequestURI()).thenReturn("/API/TASK-HANDLERS/TEST");
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        lenient().when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        // Act
        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Assert - uppercase URI still passes through
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        // Reset mocks to test GET request
        reset(httpServletRequest, httpServletResponse, filterChain, requestInfo, properties);

        // Arrange - GET to task-handlers in LOCAL
        when(httpServletRequest.getMethod()).thenReturn("GET");
        when(httpServletRequest.getRequestURI()).thenReturn(TASK_HANDLERS_URI);
        lenient().when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        lenient().when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        // Act
        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Assert - GET also passes through without task queue check
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verify(requestInfo, never()).isTaskQueueRequest();
    }

    @Test
    void test13_shouldAllowOrDenyTaskHandlerRequestBasedOnTaskQueueCheckInCloud() throws IOException, ServletException {
        // Arrange - valid task queue request in CLOUD
        when(httpServletRequest.getMethod()).thenReturn("POST");
        when(httpServletRequest.getRequestURI()).thenReturn(TASK_HANDLERS_URI);
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.CLOUD);
        when(requestInfo.isTaskQueueRequest()).thenReturn(true);
        when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        // Act
        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Assert - task queue check is performed and request passes through
        verify(requestInfo).isTaskQueueRequest();
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        // Reset mocks to test denied case
        reset(httpServletRequest, httpServletResponse, filterChain, requestInfo, properties);

        // Arrange - non-task queue request in CLOUD
        when(httpServletRequest.getMethod()).thenReturn("POST");
        when(httpServletRequest.getRequestURI()).thenReturn(TASK_HANDLERS_URI);
        when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.CLOUD);
        when(requestInfo.isTaskQueueRequest()).thenReturn(false);

        // Act & Assert - throws 403 Forbidden and filter chain is never reached
        AppException exception = assertThrows(AppException.class, () ->
                indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getError().getCode());
        assertEquals("Access denied", exception.getError().getReason());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldCheckAccessForGetToCronHandlers() throws IOException, ServletException {
        when(httpServletRequest.getMethod()).thenReturn("GET");
        when(httpServletRequest.getRequestURI()).thenReturn(CRON_HANDLERS_URI);
        when(requestInfo.isTaskQueueRequest()).thenReturn(true);
        when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        verify(requestInfo, times(1)).isTaskQueueRequest();
        verify(filterChain, times(1)).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    void shouldThrowExceptionForUnauthorizedCronAccess() {
        when(httpServletRequest.getMethod()).thenReturn("GET");
        when(httpServletRequest.getRequestURI()).thenReturn(CRON_HANDLERS_URI);
        when(requestInfo.isTaskQueueRequest()).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () -> {
            indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        });

        assertEquals(org.apache.http.HttpStatus.SC_FORBIDDEN, exception.getError().getCode());
    }

    @Test
    void shouldAllowTaskQueueRequestToCronHandlers() throws IOException, ServletException {
        when(httpServletRequest.getMethod()).thenReturn("GET");
        when(httpServletRequest.getRequestURI()).thenReturn("/api/cron-handlers/daily");
        when(requestInfo.isTaskQueueRequest()).thenReturn(true);
        when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        verify(filterChain, times(1)).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    void shouldHandleCaseInsensitiveGetMethod() throws IOException, ServletException {
        when(httpServletRequest.getMethod()).thenReturn("get");
        when(httpServletRequest.getRequestURI()).thenReturn(CRON_HANDLERS_URI.toLowerCase());
        when(requestInfo.isTaskQueueRequest()).thenReturn(true);
        when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        verify(requestInfo, times(1)).isTaskQueueRequest();
    }

    @Test
    void shouldNotCheckCronAccessForPostMethod() throws IOException, ServletException {
        when(httpServletRequest.getMethod()).thenReturn("POST");
        when(httpServletRequest.getRequestURI()).thenReturn(CRON_HANDLERS_URI);
        lenient().when(properties.getDeploymentEnvironment()).thenReturn(DeploymentEnvironment.LOCAL);
        when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        verify(filterChain, times(1)).doFilter(httpServletRequest, httpServletResponse);
        verify(requestInfo, never()).isTaskQueueRequest();
    }

    @Test
    void shouldTestValidateAccountIdWithNullAccountId() throws Exception {
        Method method = IndexFilter.class.getDeclaredMethod("validateAccountId", DpsHeaders.class);
        method.setAccessible(true);

        DpsHeaders testHeaders = Mockito.mock(DpsHeaders.class);
        when(testHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn("");

        AppException exception = assertThrows(AppException.class, () -> {
            invokeValidateAccountId(method, testHeaders);
        });

        assertEquals(org.apache.http.HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
        assertEquals("Bad request", exception.getError().getReason());
    }

    @Test
    void shouldTestValidateAccountIdWithEmptyAccountId() throws Exception {
        Method method = IndexFilter.class.getDeclaredMethod("validateAccountId", DpsHeaders.class);
        method.setAccessible(true);

        DpsHeaders testHeaders = Mockito.mock(DpsHeaders.class);
        when(testHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn("");

        AppException exception = assertThrows(AppException.class, () -> {
            invokeValidateAccountId(method, testHeaders);
        });

        assertEquals(org.apache.http.HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
    }

    @Test
    void shouldTestValidateAccountIdWithMultiplePartitions() throws Exception {
        Method method = IndexFilter.class.getDeclaredMethod("validateAccountId", DpsHeaders.class);
        method.setAccessible(true);

        DpsHeaders testHeaders = Mockito.mock(DpsHeaders.class);
        when(testHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn("partition1, partition2");

        AppException exception = assertThrows(AppException.class, () -> {
            invokeValidateAccountId(method, testHeaders);
        });

        assertEquals(org.apache.http.HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
        assertEquals("Bad request", exception.getError().getReason());
    }

    @Test
    void shouldAddStandardResponseHeadersWithoutCorrelationId() throws IOException, ServletException {
        when(httpServletRequest.getMethod()).thenReturn("GET");
        when(httpServletRequest.getRequestURI()).thenReturn(REGULAR_URI);
        when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Verify standard headers are added
        verify(httpServletResponse, times(1)).addHeader(eq(DpsHeaders.CORRELATION_ID), anyString());
        verify(filterChain, times(1)).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    void shouldHandleNullDeploymentEnvironment() throws IOException, ServletException {
        when(httpServletRequest.getMethod()).thenReturn("POST");
        when(httpServletRequest.getRequestURI()).thenReturn(TASK_HANDLERS_URI);
        when(properties.getDeploymentEnvironment()).thenReturn(null);
        when(requestInfo.isTaskQueueRequest()).thenReturn(true);
        when(httpServletResponse.getHeader(DpsHeaders.CORRELATION_ID)).thenReturn(null);

        indexFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Should check access because environment is not LOCAL
        verify(requestInfo, times(1)).isTaskQueueRequest();
        verify(filterChain, times(1)).doFilter(httpServletRequest, httpServletResponse);
    }

    @ParameterizedTest
    @MethodSource("provideAccountIdValidationCases")
    @DisplayName("Should validate and trim account IDs correctly")
    void testValidateAccountId_shouldHandleVariousAccountIdFormats(
            String input,
            String expectedOutput,
            String testDescription) throws Exception {
        // Arrange
        Method method = IndexFilter.class.getDeclaredMethod("validateAccountId", DpsHeaders.class);
        method.setAccessible(true);

        DpsHeaders testHeaders = Mockito.mock(DpsHeaders.class);
        when(testHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(input);

        // Act
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(indexFilter, testHeaders);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedOutput, result.get(0));
    }

    private static Stream<Arguments> provideAccountIdValidationCases() {
        return Stream.of(
                Arguments.of("test-partition", "test-partition", "valid account ID"),
                Arguments.of("   ", "", "whitespace only"),
                Arguments.of("  test-partition  ", "test-partition", "leading and trailing spaces")
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeValidateAccountId(Method method, DpsHeaders headers) {
        try {
            return (List<String>) method.invoke(indexFilter, headers);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
