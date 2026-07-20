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
package org.opengroup.osdu.indexer.web.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

import java.lang.reflect.Method;

@ExtendWith(MockitoExtension.class)
@DisplayName("CorePlusSecurityConfig Tests")
class CorePlusSecurityConfigTest {

    @Mock
    private HttpSecurity httpSecurity;

    @Mock
    private DefaultSecurityFilterChain defaultSecurityFilterChain;

    @Mock
    private WebSecurity webSecurity;

    @Mock
    private WebSecurity.IgnoredRequestConfigurer ignoredRequestConfigurer;

    private CorePlusSecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new CorePlusSecurityConfig();
    }

    @Test
    @DisplayName("Should instantiate and have all required security annotations")
    void shouldInstantiateWithRequiredSecurityAnnotations() {
        // Assert
        assertNotNull(securityConfig);
        assertTrue(securityConfig.getClass().isAnnotationPresent(Configuration.class));
        assertTrue(securityConfig.getClass().isAnnotationPresent(EnableWebSecurity.class));
        assertTrue(securityConfig.getClass().isAnnotationPresent(EnableMethodSecurity.class));
    }

    @Test
    @DisplayName("Should have securityFilterChain method with correct signature and Bean annotation")
    void shouldHaveSecurityFilterChainMethodWithCorrectSignature() throws NoSuchMethodException {
        // Act
        Method method = CorePlusSecurityConfig.class.getMethod("securityFilterChain", HttpSecurity.class);

        // Assert - method exists and is annotated with @Bean
        assertNotNull(method);
        assertTrue(method.isAnnotationPresent(Bean.class));

        // Assert - accepts single HttpSecurity parameter
        assertEquals(1, method.getParameterCount());
        assertEquals(HttpSecurity.class, method.getParameterTypes()[0]);

        // Assert - returns SecurityFilterChain
        assertEquals(SecurityFilterChain.class, method.getReturnType());

        // Assert - declares Exception
        assertTrue(method.getExceptionTypes().length > 0);
        assertEquals(Exception.class, method.getExceptionTypes()[0]);
    }

    @Test
    @DisplayName("Should build SecurityFilterChain with all security configurations")
    void shouldBuildSecurityFilterChainWithAllConfigurations() throws Exception {
        // Arrange
        when(httpSecurity.cors(any())).thenReturn(httpSecurity);
        when(httpSecurity.csrf(any())).thenReturn(httpSecurity);
        when(httpSecurity.sessionManagement(any())).thenReturn(httpSecurity);
        when(httpSecurity.authorizeHttpRequests(any())).thenReturn(httpSecurity);
        when(httpSecurity.httpBasic(any())).thenReturn(httpSecurity);
        when(httpSecurity.build()).thenReturn(defaultSecurityFilterChain);

        // Act
        SecurityFilterChain result = securityConfig.securityFilterChain(httpSecurity);

        // Assert - returns a valid SecurityFilterChain
        assertNotNull(result);
        assertEquals(defaultSecurityFilterChain, result);

        // Assert - all security configurations were applied
        verify(httpSecurity).cors(any());
        verify(httpSecurity).csrf(any());
        verify(httpSecurity).sessionManagement(any());
        verify(httpSecurity).authorizeHttpRequests(any());
        verify(httpSecurity).httpBasic(any());
        verify(httpSecurity).build();
    }

    @Test
    @DisplayName("Should produce independent SecurityFilterChains on each invocation")
    void shouldProduceIndependentSecurityFilterChains() throws Exception {
        // Arrange
        HttpSecurity httpSecurity2 = mock(HttpSecurity.class);
        DefaultSecurityFilterChain filterChain2 = mock(DefaultSecurityFilterChain.class);

        when(httpSecurity.cors(any())).thenReturn(httpSecurity);
        when(httpSecurity.csrf(any())).thenReturn(httpSecurity);
        when(httpSecurity.sessionManagement(any())).thenReturn(httpSecurity);
        when(httpSecurity.authorizeHttpRequests(any())).thenReturn(httpSecurity);
        when(httpSecurity.httpBasic(any())).thenReturn(httpSecurity);
        when(httpSecurity.build()).thenReturn(defaultSecurityFilterChain);

        when(httpSecurity2.cors(any())).thenReturn(httpSecurity2);
        when(httpSecurity2.csrf(any())).thenReturn(httpSecurity2);
        when(httpSecurity2.sessionManagement(any())).thenReturn(httpSecurity2);
        when(httpSecurity2.authorizeHttpRequests(any())).thenReturn(httpSecurity2);
        when(httpSecurity2.httpBasic(any())).thenReturn(httpSecurity2);
        when(httpSecurity2.build()).thenReturn(filterChain2);

        // Act
        SecurityFilterChain chain1 = securityConfig.securityFilterChain(httpSecurity);
        SecurityFilterChain chain2 = securityConfig.securityFilterChain(httpSecurity2);

        // Assert - each invocation returns its own independent chain
        assertNotNull(chain1);
        assertNotNull(chain2);
        assertNotSame(chain1, chain2);
        assertEquals(defaultSecurityFilterChain, chain1);
        assertEquals(filterChain2, chain2);
    }

    @Test
    @DisplayName("Should have webSecurityCustomizer method with correct signature and return a valid instance")
    void shouldHaveWebSecurityCustomizerWithCorrectSignatureAndInstance() throws Exception {
        // Act - verify method signature via reflection
        Method method = CorePlusSecurityConfig.class.getMethod("webSecurityCustomizer");

        // Assert - method exists, is a @Bean, takes no parameters, and returns WebSecurityCustomizer
        assertNotNull(method);
        assertTrue(method.isAnnotationPresent(Bean.class));
        assertEquals(0, method.getParameterCount());
        assertEquals(WebSecurityCustomizer.class, method.getReturnType());

        // Assert - actual invocation returns a valid instance
        WebSecurityCustomizer result = securityConfig.webSecurityCustomizer();
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should return a functional WebSecurityCustomizer that configures ignoring rules")
    void shouldReturnFunctionalWebSecurityCustomizerWithIgnoringRules() {
        // Arrange
        when(webSecurity.ignoring()).thenReturn(ignoredRequestConfigurer);
        when(ignoredRequestConfigurer.requestMatchers("/api-docs", "/info", "/swagger"))
                .thenReturn(ignoredRequestConfigurer);

        // Act
        WebSecurityCustomizer customizer = securityConfig.webSecurityCustomizer();
        customizer.customize(webSecurity);

        // Assert - returns a valid WebSecurityCustomizer
        assertNotNull(customizer);

        // Assert - customizer delegates to webSecurity.ignoring() with the expected paths
        verify(webSecurity).ignoring();
        verify(ignoredRequestConfigurer).requestMatchers("/api-docs", "/info", "/swagger");
    }

    @Test
    @DisplayName("Should have exactly two @Bean methods")
    void shouldHaveExactlyTwoBeanMethods() {
        // Act
        long beanMethods = java.util.Arrays.stream(securityConfig.getClass().getMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .count();

        // Assert
        assertEquals(2, beanMethods);
    }

    @Test
    @DisplayName("Should be public class")
    void shouldBePublicClass() {
        // Assert
        assertTrue(java.lang.reflect.Modifier.isPublic(securityConfig.getClass().getModifiers()));
    }

    @Test
    @DisplayName("Should have default constructor")
    void shouldHaveDefaultConstructor() {
        // Act & Assert
        assertDoesNotThrow(CorePlusSecurityConfig::new);
    }

    @Test
    @DisplayName("Should allow multiple instances")
    void shouldAllowMultipleInstances() {
        // Act
        CorePlusSecurityConfig config1 = new CorePlusSecurityConfig();
        CorePlusSecurityConfig config2 = new CorePlusSecurityConfig();

        // Assert
        assertNotNull(config1);
        assertNotNull(config2);
        assertNotSame(config1, config2);
    }

    @Test
    @DisplayName("All bean methods should be public")
    void allBeanMethodsShouldBePublic() {
        // Act
        boolean allPublic = java.util.Arrays.stream(securityConfig.getClass().getMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .allMatch(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()));

        // Assert
        assertTrue(allPublic);
    }
}
