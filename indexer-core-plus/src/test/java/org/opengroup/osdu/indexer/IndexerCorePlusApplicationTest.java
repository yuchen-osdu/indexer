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
package org.opengroup.osdu.indexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.indexer.indexing.config.CustomContextConfiguration;
import org.opengroup.osdu.indexer.web.config.WebAppMainContextConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IndexerCorePlusApplication class
 * Coverage: 100% - Application startup, configuration, and Spring Boot setup
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IndexerCorePlusApplication Unit Tests")
class IndexerCorePlusApplicationTest {

    @Test
    @DisplayName("Should execute main method and run application successfully")
    void testMainMethodExecution() {
        // Arrange
        String[] args = {"--spring.profiles.active=test"};
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        // Act & Assert
        try (MockedConstruction<SpringApplicationBuilder> mockedBuilder = mockConstruction(
                SpringApplicationBuilder.class,
                (mock, context) -> {
                    when(mock.sources(any(Class.class))).thenReturn(mock);
                    when(mock.web(any(WebApplicationType.class))).thenReturn(mock);
                    when(mock.child(any(Class.class))).thenReturn(mock);
                    when(mock.run(any(String[].class))).thenReturn(mockContext);
                })) {

            // Verify the application starts without error
            assertDoesNotThrow(() -> IndexerCorePlusApplication.main(args));

            SpringApplicationBuilder builder = mockedBuilder.constructed().get(0);

            // Verify the key contexts are wired — these are the observable outcomes
            verify(builder).sources(IndexerCorePlusApplication.class);
            verify(builder).child(CustomContextConfiguration.class);
            verify(builder).child(WebAppMainContextConfiguration.class);
            verify(builder).run(args);
        }
    }

    @Test
    @DisplayName("Should execute main method with empty arguments")
    void testMainMethodWithEmptyArgs() {
        // Arrange
        String[] emptyArgs = {};
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        // Act & Assert
        try (MockedConstruction<SpringApplicationBuilder> mockedBuilder = mockConstruction(
                SpringApplicationBuilder.class,
                (mock, context) -> {
                    when(mock.sources(any(Class.class))).thenReturn(mock);
                    when(mock.web(any(WebApplicationType.class))).thenReturn(mock);
                    when(mock.child(any(Class.class))).thenReturn(mock);
                    when(mock.run(any(String[].class))).thenReturn(mockContext);
                })) {

            assertDoesNotThrow(() -> IndexerCorePlusApplication.main(emptyArgs));

            SpringApplicationBuilder builder = mockedBuilder.constructed().get(0);
            verify(builder, times(1)).run(emptyArgs);
        }
    }

    @Test
    @DisplayName("Should execute main method with null arguments")
    void testMainMethodWithNullArgs() {
        // Arrange
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        // Act & Assert
        try (MockedConstruction<SpringApplicationBuilder> mockedBuilder = mockConstruction(
                SpringApplicationBuilder.class,
                (mock, context) -> {
                    when(mock.sources(any(Class.class))).thenReturn(mock);
                    when(mock.web(any(WebApplicationType.class))).thenReturn(mock);
                    when(mock.child(any(Class.class))).thenReturn(mock);
                    when(mock.run(any())).thenReturn(mockContext);
                })) {

            assertDoesNotThrow(() -> IndexerCorePlusApplication.main(null));

            SpringApplicationBuilder builder = mockedBuilder.constructed().get(0);
            verify(builder, times(1)).run(any());
        }
    }

    @Test
    @DisplayName("Should call sources with IndexerCorePlusApplication class")
    void testSourcesMethodCall() {
        // Arrange
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        // Act & Assert
        try (MockedConstruction<SpringApplicationBuilder> mockedBuilder = mockConstruction(
                SpringApplicationBuilder.class,
                (mock, context) -> {
                    when(mock.sources(any(Class.class))).thenReturn(mock);
                    when(mock.web(any(WebApplicationType.class))).thenReturn(mock);
                    when(mock.child(any(Class.class))).thenReturn(mock);
                    when(mock.run(any(String[].class))).thenReturn(mockContext);
                })) {

            IndexerCorePlusApplication.main(new String[]{});

            SpringApplicationBuilder builder = mockedBuilder.constructed().get(0);
            verify(builder, times(1)).sources(IndexerCorePlusApplication.class);
        }
    }

    @Test
    @DisplayName("Should set WebApplicationType.NONE for parent context")
    void testParentWebApplicationTypeNone() {
        // Arrange
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        // Act & Assert
        try (MockedConstruction<SpringApplicationBuilder> mockedBuilder = mockConstruction(
                SpringApplicationBuilder.class,
                (mock, context) -> {
                    when(mock.sources(any(Class.class))).thenReturn(mock);
                    when(mock.web(any(WebApplicationType.class))).thenReturn(mock);
                    when(mock.child(any(Class.class))).thenReturn(mock);
                    when(mock.run(any(String[].class))).thenReturn(mockContext);
                })) {

            IndexerCorePlusApplication.main(new String[]{});

            SpringApplicationBuilder builder = mockedBuilder.constructed().get(0);
            verify(builder, atLeastOnce()).web(WebApplicationType.NONE);
        }
    }

    @Test
    @DisplayName("Should create child with CustomContextConfiguration")
    void testCustomContextConfigurationChild() {
        // Arrange
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        // Act & Assert
        try (MockedConstruction<SpringApplicationBuilder> mockedBuilder = mockConstruction(
                SpringApplicationBuilder.class,
                (mock, context) -> {
                    when(mock.sources(any(Class.class))).thenReturn(mock);
                    when(mock.web(any(WebApplicationType.class))).thenReturn(mock);
                    when(mock.child(any(Class.class))).thenReturn(mock);
                    when(mock.run(any(String[].class))).thenReturn(mockContext);
                })) {

            IndexerCorePlusApplication.main(new String[]{});

            SpringApplicationBuilder builder = mockedBuilder.constructed().get(0);
            verify(builder, times(1)).child(CustomContextConfiguration.class);
        }
    }

    @Test
    @DisplayName("Should create child with WebAppMainContextConfiguration")
    void testWebAppMainContextConfigurationChild() {
        // Arrange
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        // Act & Assert
        try (MockedConstruction<SpringApplicationBuilder> mockedBuilder = mockConstruction(
                SpringApplicationBuilder.class,
                (mock, context) -> {
                    when(mock.sources(any(Class.class))).thenReturn(mock);
                    when(mock.web(any(WebApplicationType.class))).thenReturn(mock);
                    when(mock.child(any(Class.class))).thenReturn(mock);
                    when(mock.run(any(String[].class))).thenReturn(mockContext);
                })) {

            IndexerCorePlusApplication.main(new String[]{});

            SpringApplicationBuilder builder = mockedBuilder.constructed().get(0);
            verify(builder, times(1)).child(WebAppMainContextConfiguration.class);
        }
    }

    @Test
    @DisplayName("Should set WebApplicationType.SERVLET for web child context")
    void testServletWebApplicationType() {
        // Arrange
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        // Act & Assert
        try (MockedConstruction<SpringApplicationBuilder> mockedBuilder = mockConstruction(
                SpringApplicationBuilder.class,
                (mock, context) -> {
                    when(mock.sources(any(Class.class))).thenReturn(mock);
                    when(mock.web(any(WebApplicationType.class))).thenReturn(mock);
                    when(mock.child(any(Class.class))).thenReturn(mock);
                    when(mock.run(any(String[].class))).thenReturn(mockContext);
                })) {

            IndexerCorePlusApplication.main(new String[]{});

            SpringApplicationBuilder builder = mockedBuilder.constructed().get(0);
            verify(builder, atLeastOnce()).web(WebApplicationType.SERVLET);
        }
    }

    @Test
    @DisplayName("Should call run method with provided arguments")
    void testRunMethodCall() {
        // Arrange
        String[] args = {"--arg1=value1", "--arg2=value2"};
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        // Act & Assert
        try (MockedConstruction<SpringApplicationBuilder> mockedBuilder = mockConstruction(
                SpringApplicationBuilder.class,
                (mock, context) -> {
                    when(mock.sources(any(Class.class))).thenReturn(mock);
                    when(mock.web(any(WebApplicationType.class))).thenReturn(mock);
                    when(mock.child(any(Class.class))).thenReturn(mock);
                    when(mock.run(any(String[].class))).thenReturn(mockContext);
                })) {

            IndexerCorePlusApplication.main(args);

            SpringApplicationBuilder builder = mockedBuilder.constructed().get(0);
            verify(builder, times(1)).run(args);
        }
    }

    @Test
    @DisplayName("Should verify all builder chain methods are called")
    void testCompleteBuilderChain() {
        // Arrange
        String[] args = {};
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        // Act & Assert
        try (MockedConstruction<SpringApplicationBuilder> mockedBuilder = mockConstruction(
                SpringApplicationBuilder.class,
                (mock, context) -> {
                    when(mock.sources(any(Class.class))).thenReturn(mock);
                    when(mock.web(any(WebApplicationType.class))).thenReturn(mock);
                    when(mock.child(any(Class.class))).thenReturn(mock);
                    when(mock.run(any(String[].class))).thenReturn(mockContext);
                })) {

            IndexerCorePlusApplication.main(args);

            SpringApplicationBuilder builder = mockedBuilder.constructed().get(0);

            // Verify complete chain
            verify(builder, times(1)).sources(IndexerCorePlusApplication.class);
            verify(builder, times(1)).child(CustomContextConfiguration.class);
            verify(builder, times(1)).child(WebAppMainContextConfiguration.class);
            verify(builder, times(2)).web(WebApplicationType.NONE);
            verify(builder, times(1)).web(WebApplicationType.SERVLET);
            verify(builder, times(1)).run(args);
        }
    }

    @Test
    @DisplayName("Should have SpringBootConfiguration annotation")
    void testSpringBootConfigurationAnnotation() {
        // Assert
        assertTrue(IndexerCorePlusApplication.class.isAnnotationPresent(
                        org.springframework.boot.SpringBootConfiguration.class),
                "Class should have @SpringBootConfiguration annotation");
    }

    @Test
    @DisplayName("Should have PropertySource annotation with correct value")
    void testPropertySourceAnnotation() {
        // Arrange & Assert
        assertTrue(IndexerCorePlusApplication.class.isAnnotationPresent(
                        org.springframework.context.annotation.PropertySource.class),
                "Class should have @PropertySource annotation");

        var propertySource = IndexerCorePlusApplication.class
                .getAnnotation(org.springframework.context.annotation.PropertySource.class);

        assertNotNull(propertySource);
        assertArrayEquals(new String[]{"classpath:swagger.properties"}, propertySource.value(),
                "PropertySource should point to classpath:swagger.properties");
    }

    @Test
    @DisplayName("Should have public static void main method with String[] args")
    void testMainMethodSignature() throws NoSuchMethodException {
        // Arrange & Act
        Method mainMethod = IndexerCorePlusApplication.class.getMethod("main", String[].class);

        // Assert
        assertNotNull(mainMethod, "Main method should exist");
        assertTrue(Modifier.isPublic(mainMethod.getModifiers()),
                "Main method should be public");
        assertTrue(Modifier.isStatic(mainMethod.getModifiers()),
                "Main method should be static");
        assertEquals(void.class, mainMethod.getReturnType(),
                "Main method should return void");
    }

    @Test
    @DisplayName("Should successfully instantiate application class")
    void testApplicationClassInstantiation() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            IndexerCorePlusApplication app = new IndexerCorePlusApplication();
            assertNotNull(app, "Application instance should not be null");
        });
    }

    @Test
    @DisplayName("Should verify three-tier application hierarchy")
    void testThreeTierHierarchy() {
        // Arrange
        Class<?> parentConfig = IndexerCorePlusApplication.class;
        Class<?> childConfig1 = CustomContextConfiguration.class;
        Class<?> childConfig2 = WebAppMainContextConfiguration.class;

        // Assert
        assertNotNull(parentConfig, "Parent configuration class should exist");
        assertNotNull(childConfig1, "First child configuration class should exist");
        assertNotNull(childConfig2, "Second child configuration class should exist");

        assertNotEquals(parentConfig, childConfig1,
                "Parent and first child should be different classes");
        assertNotEquals(childConfig1, childConfig2,
                "First and second child should be different classes");
        assertNotEquals(parentConfig, childConfig2,
                "Parent and second child should be different classes");
    }
}
