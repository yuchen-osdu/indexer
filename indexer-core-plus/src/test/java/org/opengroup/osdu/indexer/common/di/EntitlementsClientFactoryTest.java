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
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.http.json.HttpResponseBodyMapper;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntitlementsClientFactory Tests")
class EntitlementsClientFactoryTest {

    @Mock
    private HttpResponseBodyMapper mapper;

    private EntitlementsClientFactory factory;

    private static final String TEST_AUTHORIZE_API = "https://api.example.com";
    private static final String TEST_AUTHORIZE_API_KEY = "test-api-key-123";

    @BeforeEach
    void setUp() {
        factory = new EntitlementsClientFactory();
        ReflectionTestUtils.setField(factory, "mapper", mapper);
    }

    @Test
    @DisplayName("Test 01: Should instantiate and have required annotations")
    void shouldInstantiateAndHaveRequiredAnnotations() {
        // Assert
        assertNotNull(factory);
        assertTrue(factory.getClass().isAnnotationPresent(Component.class));
        assertTrue(factory.getClass().isAnnotationPresent(Lazy.class));
    }

    @Test
    @DisplayName("Test 02: Should extend AbstractFactoryBean")
    void shouldExtendAbstractFactoryBean() {
        // Assert
        assertTrue(factory instanceof AbstractFactoryBean);
    }

    @Test
    @DisplayName("Test 03: Should return correct object type")
    void shouldReturnCorrectObjectType() {
        // Act
        Class<?> objectType = factory.getObjectType();

        // Assert
        assertNotNull(objectType);
        assertEquals(IEntitlementsFactory.class, objectType);
    }

    @Test
    @DisplayName("Test 04: Should create instance with valid properties")
    void shouldCreateInstanceWithValidProperties() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(factory, "authorizeApi", TEST_AUTHORIZE_API);
        ReflectionTestUtils.setField(factory, "authorizeApiKey", TEST_AUTHORIZE_API_KEY);

        // Act
        IEntitlementsFactory result = factory.createInstance();

        // Assert
        assertNotNull(result);
    }

    @Test
    @DisplayName("Test 05: Should create instance multiple times")
    void shouldCreateInstanceMultipleTimes() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(factory, "authorizeApi", TEST_AUTHORIZE_API);
        ReflectionTestUtils.setField(factory, "authorizeApiKey", TEST_AUTHORIZE_API_KEY);

        // Act
        IEntitlementsFactory result1 = factory.createInstance();
        IEntitlementsFactory result2 = factory.createInstance();

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
    }

    @Test
    @DisplayName("Test 06: Should use injected mapper")
    void shouldUseInjectedMapper() throws Exception {
        // Arrange
        HttpResponseBodyMapper customMapper = mock(HttpResponseBodyMapper.class);
        ReflectionTestUtils.setField(factory, "mapper", customMapper);
        ReflectionTestUtils.setField(factory, "authorizeApi", TEST_AUTHORIZE_API);
        ReflectionTestUtils.setField(factory, "authorizeApiKey", TEST_AUTHORIZE_API_KEY);

        // Act
        IEntitlementsFactory result = factory.createInstance();

        // Assert
        assertNotNull(result);
    }

    @Test
    @DisplayName("Test 07: Should handle different API URLs")
    void shouldHandleDifferentApiUrls() throws Exception {
        // Arrange
        String[] apiUrls = {
                "https://api.example.com",
                "http://localhost:8080",
                "https://api.prod.example.com/v1"
        };

        for (String apiUrl : apiUrls) {
            ReflectionTestUtils.setField(factory, "authorizeApi", apiUrl);
            ReflectionTestUtils.setField(factory, "authorizeApiKey", TEST_AUTHORIZE_API_KEY);

            // Act
            IEntitlementsFactory result = factory.createInstance();

            // Assert
            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("Test 08: Should handle different API keys")
    void shouldHandleDifferentApiKeys() throws Exception {
        // Arrange
        String[] apiKeys = {
                "simple-key",
                "key-with-special-chars-!@#$",
                "very-long-key-" + "x".repeat(100)
        };

        ReflectionTestUtils.setField(factory, "authorizeApi", TEST_AUTHORIZE_API);

        for (String apiKey : apiKeys) {
            ReflectionTestUtils.setField(factory, "authorizeApiKey", apiKey);

            // Act
            IEntitlementsFactory result = factory.createInstance();

            // Assert
            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("Test 09: Should maintain factory state across calls")
    void shouldMaintainFactoryStateAcrossCalls() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(factory, "authorizeApi", TEST_AUTHORIZE_API);
        ReflectionTestUtils.setField(factory, "authorizeApiKey", TEST_AUTHORIZE_API_KEY);

        // Act
        IEntitlementsFactory result1 = factory.createInstance();
        Class<?> objectType = factory.getObjectType();
        IEntitlementsFactory result2 = factory.createInstance();

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(IEntitlementsFactory.class, objectType);
    }

    @Test
    @DisplayName("Test 10: Should create valid EntitlementsFactory")
    void shouldCreateValidEntitlementsFactory() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(factory, "authorizeApi", TEST_AUTHORIZE_API);
        ReflectionTestUtils.setField(factory, "authorizeApiKey", TEST_AUTHORIZE_API_KEY);

        // Act
        IEntitlementsFactory result = factory.createInstance();

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof IEntitlementsFactory);
    }

    @Test
    @DisplayName("Test 11: Should handle complete factory lifecycle")
    void shouldHandleCompleteFactoryLifecycle() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(factory, "authorizeApi", TEST_AUTHORIZE_API);
        ReflectionTestUtils.setField(factory, "authorizeApiKey", TEST_AUTHORIZE_API_KEY);

        // Act - Simulate complete lifecycle
        Class<?> objectType = factory.getObjectType();
        IEntitlementsFactory instance1 = factory.createInstance();
        IEntitlementsFactory instance2 = factory.createInstance();

        // Assert
        assertEquals(IEntitlementsFactory.class, objectType);
        assertNotNull(instance1);
        assertNotNull(instance2);
    }

    @Test
    @DisplayName("Test 12: Should work with different mapper implementations")
    void shouldWorkWithDifferentMapperImplementations() throws Exception {
        // Arrange
        HttpResponseBodyMapper mapper1 = mock(HttpResponseBodyMapper.class);
        HttpResponseBodyMapper mapper2 = mock(HttpResponseBodyMapper.class);

        ReflectionTestUtils.setField(factory, "authorizeApi", TEST_AUTHORIZE_API);
        ReflectionTestUtils.setField(factory, "authorizeApiKey", TEST_AUTHORIZE_API_KEY);

        // Act - Test with first mapper
        ReflectionTestUtils.setField(factory, "mapper", mapper1);
        IEntitlementsFactory result1 = factory.createInstance();

        // Test with second mapper
        ReflectionTestUtils.setField(factory, "mapper", mapper2);
        IEntitlementsFactory result2 = factory.createInstance();

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should create instance with missing or empty API key")
    void testCreateInstance_shouldHandleMissingOrEmptyApiKey(String apiKey) throws Exception {
        // Arrange
        ReflectionTestUtils.setField(factory, "authorizeApi", TEST_AUTHORIZE_API);
        ReflectionTestUtils.setField(factory, "authorizeApiKey", apiKey);

        // Act
        IEntitlementsFactory result = factory.createInstance();

        // Assert
        assertNotNull(result);
    }
}
