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
package org.opengroup.osdu.indexer.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.context.annotation.RequestScope;

import java.lang.reflect.Method;
import java.util.Vector;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebAppMainContextConfiguration Tests")
class WebAppMainContextConfigurationTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private HttpServletRequest httpServletRequest;

    private WebAppMainContextConfiguration configuration;

    private static final String TEST_CONTEXT_ID = "test-context-id";
    private static final String[] TEST_BEAN_NAMES = {"bean1", "bean2", "bean3"};

    @BeforeEach
    void setUp() {
        configuration = new WebAppMainContextConfiguration(applicationContext);
        setupHttpServletRequestMock();
    }

    private void setupHttpServletRequestMock() {
        // Create a fresh enumeration for each setup
        Vector<String> headerNames = new Vector<>();
        headerNames.add("data-partition-id");
        headerNames.add("correlation-id");

        lenient().when(httpServletRequest.getHeaderNames()).thenReturn(headerNames.elements());
        lenient().when(httpServletRequest.getHeader("data-partition-id")).thenReturn("test-partition");
        lenient().when(httpServletRequest.getHeader("correlation-id")).thenReturn("test-correlation");
    }

    @Test
    @DisplayName("Should instantiate with correct annotations and configuration values")
    void shouldInstantiateWithCorrectAnnotationsAndConfigurationValues() {
        // Assert - instantiation
        assertNotNull(configuration);

        // Assert - all required Spring annotations are present
        assertTrue(configuration.getClass().isAnnotationPresent(Configuration.class));
        assertTrue(configuration.getClass().isAnnotationPresent(EnableAutoConfiguration.class));
        assertTrue(configuration.getClass().isAnnotationPresent(PropertySource.class));
        assertTrue(configuration.getClass().isAnnotationPresent(ComponentScan.class));

        // Assert - PropertySource points to correct file
        PropertySource propertySource = configuration.getClass().getAnnotation(PropertySource.class);
        assertEquals("classpath:application.properties", propertySource.value()[0]);

        // Assert - ComponentScan targets correct base package with exclusion filters
        ComponentScan componentScan = configuration.getClass().getAnnotation(ComponentScan.class);
        assertEquals(1, componentScan.value().length);
        assertEquals("org.opengroup.osdu", componentScan.value()[0]);
        assertTrue(componentScan.excludeFilters().length > 0);
    }

    @Test
    @DisplayName("Should execute setUp and interact with ApplicationContext correctly")
    void shouldExecuteSetUpAndInteractWithApplicationContext() {
        // Arrange
        when(applicationContext.getId()).thenReturn(TEST_CONTEXT_ID);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(TEST_BEAN_NAMES);

        // Act
        configuration.setUp();

        // Assert - setUp delegates to ApplicationContext for context id and bean names
        verify(applicationContext).getId();
        verify(applicationContext).getBeanDefinitionNames();

        // Reset to test empty bean names scenario
        reset(applicationContext);

        // Arrange - empty bean names array
        when(applicationContext.getId()).thenReturn(TEST_CONTEXT_ID);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{});

        // Act & Assert - setUp still completes successfully with no beans
        configuration.setUp();
        verify(applicationContext).getId();
        verify(applicationContext).getBeanDefinitionNames();
    }

    @Test
    @DisplayName("Should have dpsHeaders method with correct annotations and produce independent instances")
    void shouldHaveDpsHeadersMethodWithCorrectAnnotationsAndProduceIndependentInstances() throws NoSuchMethodException {
        // Assert - method exists with all required annotations
        Method method = WebAppMainContextConfiguration.class.getMethod("dpsHeaders", HttpServletRequest.class);
        assertNotNull(method);
        assertTrue(method.isAnnotationPresent(Bean.class));
        assertTrue(method.isAnnotationPresent(Primary.class));
        assertTrue(method.isAnnotationPresent(RequestScope.class));

        // Arrange - separate enumerations for each call since Enumeration is single-use
        Vector<String> headerNames1 = new Vector<>();
        headerNames1.add("data-partition-id");
        headerNames1.add("correlation-id");

        Vector<String> headerNames2 = new Vector<>();
        headerNames2.add("data-partition-id");
        headerNames2.add("correlation-id");

        when(httpServletRequest.getHeaderNames())
                .thenReturn(headerNames1.elements())
                .thenReturn(headerNames2.elements());

        // Act
        DpsHeaders result1 = configuration.dpsHeaders(httpServletRequest);
        DpsHeaders result2 = configuration.dpsHeaders(httpServletRequest);

        // Assert - both calls return valid, independent DpsHeaders instances
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotSame(result1, result2);
    }

    @Test
    @DisplayName("Should handle edge cases and complete full configuration lifecycle")
    void shouldHandleEdgeCasesAndCompleteFullLifecycle() {
        // Arrange - null context ID
        when(applicationContext.getId()).thenReturn(null);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(TEST_BEAN_NAMES);

        // Act & Assert - setUp completes with null context ID
        configuration.setUp();

        // Reset to test large bean names
        reset(applicationContext);

        // Arrange - large bean names array
        String[] largeBeanNames = new String[1000];
        for (int i = 0; i < 1000; i++) {
            largeBeanNames[i] = "bean" + i;
        }
        when(applicationContext.getId()).thenReturn(TEST_CONTEXT_ID);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(largeBeanNames);

        // Act & Assert - setUp completes with 1000 beans
        configuration.setUp();

        // Reset to test full lifecycle
        reset(applicationContext);

        // Arrange - full lifecycle with dpsHeaders creation
        when(applicationContext.getId()).thenReturn(TEST_CONTEXT_ID);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(TEST_BEAN_NAMES);

        Vector<String> headerNames1 = new Vector<>();
        headerNames1.add("data-partition-id");
        headerNames1.add("correlation-id");

        Vector<String> headerNames2 = new Vector<>();
        headerNames2.add("data-partition-id");
        headerNames2.add("correlation-id");

        when(httpServletRequest.getHeaderNames())
                .thenReturn(headerNames1.elements())
                .thenReturn(headerNames2.elements());

        // Act
        configuration.setUp();
        DpsHeaders headers1 = configuration.dpsHeaders(httpServletRequest);
        DpsHeaders headers2 = configuration.dpsHeaders(httpServletRequest);

        // Assert - setUp interacted correctly and produced independent DpsHeaders
        verify(applicationContext).getId();
        verify(applicationContext).getBeanDefinitionNames();
        assertNotNull(headers1);
        assertNotNull(headers2);
        assertNotSame(headers1, headers2);
    }
}
