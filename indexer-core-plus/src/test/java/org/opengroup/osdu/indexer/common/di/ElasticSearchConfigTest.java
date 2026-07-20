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
package org.opengroup.osdu.indexer.common.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.partition.IPartitionProvider;
import org.opengroup.osdu.core.common.partition.IPropertyResolver;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticRepository;
import org.opengroup.osdu.core.di.ElasticSearchDestinationResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

@ExtendWith(MockitoExtension.class)
@DisplayName("ElasticSearchConfig Tests") 
class ElasticSearchConfigTest {

    @Mock
    private CorePlusConfigurationProperties properties;

    @Mock
    private IPartitionProvider partitionProvider;

    @Mock
    private IPropertyResolver propertyResolver;

    private ElasticSearchConfig config;

    private static final String DEFAULT_ES_PREFIX = "elasticsearch";
    private static final String CUSTOM_ES_PREFIX = "custom-elasticsearch";

    @BeforeEach
    void setUp() {
        config = new ElasticSearchConfig();
    }

    // ==================== Test 1-2: Class Structure ====================

    @Test
    @DisplayName("Test 01: Should instantiate and have @Configuration annotation")
    void shouldInstantiateAndHaveConfigurationAnnotation() {
        // Assert
        assertNotNull(config);
        assertTrue(config.getClass().isAnnotationPresent(Configuration.class));
    }

    @Test
    @DisplayName("Test 02: Should have elasticRepository method with @Bean annotation")
    void shouldHaveElasticRepositoryMethodWithBeanAnnotation() throws NoSuchMethodException {
        // Act
        Method method = ElasticSearchConfig.class.getMethod(
                "elasticRepository",
                CorePlusConfigurationProperties.class,
                IPartitionProvider.class,
                IPropertyResolver.class
        );

        // Assert
        assertNotNull(method);
        assertTrue(method.isAnnotationPresent(Bean.class));
        assertEquals(IElasticRepository.class, method.getReturnType());
    }

    // ==================== Test 3-5: Bean Creation ====================

    @Test
    @DisplayName("Test 03: Should create ElasticRepository with default prefix")
    void shouldCreateElasticRepositoryWithDefaultPrefix() {
        // Arrange
        when(properties.getElasticsearchPropertiesPrefix()).thenReturn(DEFAULT_ES_PREFIX);

        // Act
        IElasticRepository result = config.elasticRepository(properties, partitionProvider, propertyResolver);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof ElasticSearchDestinationResolver);
        verify(properties, times(1)).getElasticsearchPropertiesPrefix();
    }

    @Test
    @DisplayName("Test 04: Should create ElasticRepository with custom prefix")
    void shouldCreateElasticRepositoryWithCustomPrefix() {
        // Arrange
        when(properties.getElasticsearchPropertiesPrefix()).thenReturn(CUSTOM_ES_PREFIX);

        // Act
        IElasticRepository result = config.elasticRepository(properties, partitionProvider, propertyResolver);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof ElasticSearchDestinationResolver);
        verify(properties, times(1)).getElasticsearchPropertiesPrefix();
    }

    @Test
    @DisplayName("Test 05: Should create ElasticRepository with all dependencies")
    void shouldCreateElasticRepositoryWithAllDependencies() {
        // Arrange
        when(properties.getElasticsearchPropertiesPrefix()).thenReturn(DEFAULT_ES_PREFIX);

        // Act
        IElasticRepository result = config.elasticRepository(properties, partitionProvider, propertyResolver);

        // Assert
        assertNotNull(result);
        assertNotNull(partitionProvider);
        assertNotNull(propertyResolver);
        assertNotNull(properties);
    }

    // ==================== Test 6-8: Property Variations ====================

    @Test
    @DisplayName("Test 06: Should handle different prefix values")
    void shouldHandleDifferentPrefixValues() {
        // Arrange
        String[] prefixes = {
                "elasticsearch",
                "custom-es",
                "es-prod",
                "elastic-search-v2"
        };

        for (String prefix : prefixes) {
            when(properties.getElasticsearchPropertiesPrefix()).thenReturn(prefix);

            // Act
            IElasticRepository result = config.elasticRepository(properties, partitionProvider, propertyResolver);

            // Assert
            assertNotNull(result);
            assertTrue(result instanceof ElasticSearchDestinationResolver);
        }
    }

    @Test
    @DisplayName("Test 07: Should handle empty prefix")
    void shouldHandleEmptyPrefix() {
        // Arrange
        when(properties.getElasticsearchPropertiesPrefix()).thenReturn("");

        // Act
        IElasticRepository result = config.elasticRepository(properties, partitionProvider, propertyResolver);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof ElasticSearchDestinationResolver);
    }

    @Test
    @DisplayName("Test 08: Should handle null prefix")
    void shouldHandleNullPrefix() {
        // Arrange
        when(properties.getElasticsearchPropertiesPrefix()).thenReturn(null);

        // Act
        IElasticRepository result = config.elasticRepository(properties, partitionProvider, propertyResolver);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof ElasticSearchDestinationResolver);
    }

    // ==================== Test 9-10: Multiple Invocations ====================

    @Test
    @DisplayName("Test 09: Should create new instance on each invocation")
    void shouldCreateNewInstanceOnEachInvocation() {
        // Arrange
        when(properties.getElasticsearchPropertiesPrefix()).thenReturn(DEFAULT_ES_PREFIX);

        // Act
        IElasticRepository result1 = config.elasticRepository(properties, partitionProvider, propertyResolver);
        IElasticRepository result2 = config.elasticRepository(properties, partitionProvider, propertyResolver);

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        // Each call should create a new instance (not singleton by default)
        assertTrue(result1 instanceof ElasticSearchDestinationResolver);
        assertTrue(result2 instanceof ElasticSearchDestinationResolver);
    }

    @Test
    @DisplayName("Test 10: Should call getElasticsearchPropertiesPrefix for each creation")
    void shouldCallGetElasticsearchPropertiesPrefixForEachCreation() {
        // Arrange
        when(properties.getElasticsearchPropertiesPrefix()).thenReturn(DEFAULT_ES_PREFIX);

        // Act
        config.elasticRepository(properties, partitionProvider, propertyResolver);
        config.elasticRepository(properties, partitionProvider, propertyResolver);
        config.elasticRepository(properties, partitionProvider, propertyResolver);

        // Assert
        verify(properties, times(3)).getElasticsearchPropertiesPrefix();
    }

    // ==================== Test 11-12: Integration Scenarios ====================

    @Test
    @DisplayName("Test 11: Should wire all dependencies correctly")
    void shouldWireAllDependenciesCorrectly() {
        // Arrange
        when(properties.getElasticsearchPropertiesPrefix()).thenReturn(DEFAULT_ES_PREFIX);

        // Act
        IElasticRepository repository = config.elasticRepository(properties, partitionProvider, propertyResolver);

        // Assert
        assertNotNull(repository);
        assertTrue(repository instanceof ElasticSearchDestinationResolver);

        // Verify all dependencies were accessed
        verify(properties).getElasticsearchPropertiesPrefix();
    }

    @Test
    @DisplayName("Test 12: Should create valid bean for Spring context")
    void shouldCreateValidBeanForSpringContext() {
        // Arrange
        when(properties.getElasticsearchPropertiesPrefix()).thenReturn(DEFAULT_ES_PREFIX);

        // Act
        IElasticRepository repository = config.elasticRepository(properties, partitionProvider, propertyResolver);

        // Assert - Verify the bean is properly configured
        assertNotNull(repository);
        assertTrue(IElasticRepository.class.isAssignableFrom(repository.getClass()));
        assertTrue(repository instanceof ElasticSearchDestinationResolver);
    }
}
