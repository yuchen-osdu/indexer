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
package org.opengroup.osdu.indexer.indexing.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.indexer.indexing.thread.ThreadScope;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;

import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class ScopeModifierPostProcessorTest {

    @Mock
    private ConfigurableListableBeanFactory factory;

    @Mock
    private BeanDefinition beanDefinition1;

    @Mock
    private BeanDefinition beanDefinition2;

    @Mock
    private BeanDefinition beanDefinition3;

    @InjectMocks
    private ScopeModifierPostProcessor scopeModifierPostProcessor;

    @Captor
    private ArgumentCaptor<Scope> scopeCaptor;

    @Captor
    private ArgumentCaptor<String> scopeStringCaptor;

    @BeforeEach
    void setUp() {
        reset(factory, beanDefinition1, beanDefinition2, beanDefinition3);
    }

    @Test
    void postProcessBeanFactory_shouldRegisterThreadScopeWithCorrectNameAndInstance() {
        // Arrange
        when(factory.getBeanDefinitionNames()).thenReturn(new String[0]);

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert - registered with correct scope name and a valid ThreadScope instance
        verify(factory).registerScope(eq("scope_thread"), scopeCaptor.capture());
        Scope registeredScope = scopeCaptor.getValue();
        assertNotNull(registeredScope);
        assertInstanceOf(ThreadScope.class, registeredScope);
    }

    @Test
    void postProcessBeanFactory_shouldProcessAllBeanDefinitions() {
        // Arrange
        String[] beanNames = {"bean1", "bean2", "bean3"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("bean1")).thenReturn(beanDefinition1);
        when(factory.getBeanDefinition("bean2")).thenReturn(beanDefinition2);
        when(factory.getBeanDefinition("bean3")).thenReturn(beanDefinition3);
        when(beanDefinition1.getScope()).thenReturn("singleton");
        when(beanDefinition2.getScope()).thenReturn("singleton");
        when(beanDefinition3.getScope()).thenReturn("singleton");

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(factory).getBeanDefinition("bean1");
        verify(factory).getBeanDefinition("bean2");
        verify(factory).getBeanDefinition("bean3");
    }

    @Test
    void postProcessBeanFactory_shouldCompleteWithoutException() {
        // Arrange
        when(factory.getBeanDefinitionNames()).thenReturn(new String[0]);

        // Act & Assert - Should not throw any exception
        assertDoesNotThrow(() -> scopeModifierPostProcessor.postProcessBeanFactory(factory));
    }

    @Test
    void postProcessBeanFactory_shouldOverrideRequestScopeToThreadScope() {
        // Arrange
        String[] beanNames = {"requestScopedBean"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("requestScopedBean")).thenReturn(beanDefinition1);
        when(beanDefinition1.getScope()).thenReturn("request");
        when(beanDefinition1.getBeanClassName()).thenReturn("com.example.RequestScopedBean");

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(beanDefinition1).setScope(ScopeModifierPostProcessor.SCOPE_THREAD);
    }

    @Test
    void postProcessBeanFactory_shouldOverrideMultipleRequestScopedBeans() {
        // Arrange
        String[] beanNames = {"bean1", "bean2", "bean3"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("bean1")).thenReturn(beanDefinition1);
        when(factory.getBeanDefinition("bean2")).thenReturn(beanDefinition2);
        when(factory.getBeanDefinition("bean3")).thenReturn(beanDefinition3);
        when(beanDefinition1.getScope()).thenReturn("request");
        when(beanDefinition2.getScope()).thenReturn("request");
        when(beanDefinition3.getScope()).thenReturn("request");
        when(beanDefinition1.getBeanClassName()).thenReturn("Bean1");
        when(beanDefinition2.getBeanClassName()).thenReturn("Bean2");
        when(beanDefinition3.getBeanClassName()).thenReturn("Bean3");

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(beanDefinition1).setScope(ScopeModifierPostProcessor.SCOPE_THREAD);
        verify(beanDefinition2).setScope(ScopeModifierPostProcessor.SCOPE_THREAD);
        verify(beanDefinition3).setScope(ScopeModifierPostProcessor.SCOPE_THREAD);
    }

    @Test
    void postProcessBeanFactory_shouldNotOverrideNonRequestScopes() {
        // Arrange
        String[] beanNames = {"singletonBean", "prototypeBean"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("singletonBean")).thenReturn(beanDefinition1);
        when(factory.getBeanDefinition("prototypeBean")).thenReturn(beanDefinition2);
        when(beanDefinition1.getScope()).thenReturn("singleton");
        when(beanDefinition2.getScope()).thenReturn("prototype");

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(beanDefinition1, never()).setScope(anyString());
        verify(beanDefinition2, never()).setScope(anyString());
    }

    @Test
    void postProcessBeanFactory_shouldHandleMixedScopes() {
        // Arrange
        String[] beanNames = {"bean1", "bean2", "bean3", "bean4"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("bean1")).thenReturn(beanDefinition1);
        when(factory.getBeanDefinition("bean2")).thenReturn(beanDefinition2);
        when(factory.getBeanDefinition("bean3")).thenReturn(beanDefinition3);
        when(factory.getBeanDefinition("bean4")).thenReturn(mock(BeanDefinition.class));
        when(beanDefinition1.getScope()).thenReturn("request");
        when(beanDefinition2.getScope()).thenReturn("singleton");
        when(beanDefinition3.getScope()).thenReturn("request");
        when(beanDefinition1.getBeanClassName()).thenReturn("Bean1");
        when(beanDefinition3.getBeanClassName()).thenReturn("Bean3");
        when(factory.getBeanDefinition("bean4").getScope()).thenReturn("prototype");

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(beanDefinition1).setScope(ScopeModifierPostProcessor.SCOPE_THREAD);
        verify(beanDefinition2, never()).setScope(anyString());
        verify(beanDefinition3).setScope(ScopeModifierPostProcessor.SCOPE_THREAD);
        verify(factory.getBeanDefinition("bean4"), never()).setScope(anyString());
    }

    @Test
    void postProcessBeanFactory_shouldCheckScopeBeforeOverriding() {
        // Arrange
        String[] beanNames = {"bean1"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("bean1")).thenReturn(beanDefinition1);
        when(beanDefinition1.getScope()).thenReturn("request");
        when(beanDefinition1.getBeanClassName()).thenReturn("Bean1");

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(beanDefinition1).getScope();
        verify(beanDefinition1).setScope(ScopeModifierPostProcessor.SCOPE_THREAD);
    }

    // ==================== Test 11-15: Edge Cases ====================

    @Test
    void postProcessBeanFactory_shouldHandleEmptyBeanDefinitions() {
        // Arrange
        when(factory.getBeanDefinitionNames()).thenReturn(new String[0]);

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(factory).registerScope(eq(ScopeModifierPostProcessor.SCOPE_THREAD), any(ThreadScope.class));
        verify(factory).getBeanDefinitionNames();
        verifyNoMoreInteractions(factory);
    }

    @Test
    void postProcessBeanFactory_shouldHandleCaseSensitiveScope() {
        // Arrange
        String[] beanNames = {"bean1", "bean2"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("bean1")).thenReturn(beanDefinition1);
        when(factory.getBeanDefinition("bean2")).thenReturn(beanDefinition2);
        when(beanDefinition1.getScope()).thenReturn("Request"); // Capital R
        when(beanDefinition2.getScope()).thenReturn("REQUEST"); // All caps

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        // Should not override - case sensitive comparison
        verify(beanDefinition1, never()).setScope(anyString());
        verify(beanDefinition2, never()).setScope(anyString());
    }

    @Test
    void postProcessBeanFactory_shouldHandleLargeBeanDefinitionSet() {
        // Arrange
        String[] beanNames = new String[100];
        BeanDefinition[] beanDefinitions = new BeanDefinition[100];
        for (int i = 0; i < 100; i++) {
            beanNames[i] = "bean" + i;
            beanDefinitions[i] = mock(BeanDefinition.class);
            when(factory.getBeanDefinition("bean" + i)).thenReturn(beanDefinitions[i]);
            when(beanDefinitions[i].getScope()).thenReturn(i % 2 == 0 ? "request" : "singleton");
            if (i % 2 == 0) {
                when(beanDefinitions[i].getBeanClassName()).thenReturn("Bean" + i);
            }
        }
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        for (int i = 0; i < 100; i++) {
            if (i % 2 == 0) {
                verify(beanDefinitions[i]).setScope(ScopeModifierPostProcessor.SCOPE_THREAD);
            } else {
                verify(beanDefinitions[i], never()).setScope(anyString());
            }
        }
    }

    @Test
    void postProcessBeanFactory_shouldRegisterScopeBeforeProcessingBeans() {
        // Arrange
        String[] beanNames = {"bean1"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("bean1")).thenReturn(beanDefinition1);
        when(beanDefinition1.getScope()).thenReturn("request");
        when(beanDefinition1.getBeanClassName()).thenReturn("Bean1");

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert - Verify order: registerScope is called before getBeanDefinitionNames
        var inOrder = inOrder(factory, beanDefinition1);
        inOrder.verify(factory).registerScope(eq(ScopeModifierPostProcessor.SCOPE_THREAD), any(ThreadScope.class));
        inOrder.verify(factory).getBeanDefinitionNames();
        inOrder.verify(factory).getBeanDefinition("bean1");
        inOrder.verify(beanDefinition1).setScope(ScopeModifierPostProcessor.SCOPE_THREAD);
    }

    @Test
    void postProcessBeanFactory_shouldRegisterScopeOnlyOnce() {
        // Arrange
        when(factory.getBeanDefinitionNames()).thenReturn(new String[0]);

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(factory, times(1)).registerScope(eq(ScopeModifierPostProcessor.SCOPE_THREAD), any(ThreadScope.class));
    }

    @Test
    void scopeThreadConstant_shouldHaveCorrectValue() {
        // Assert
        assertEquals("scope_thread", ScopeModifierPostProcessor.SCOPE_THREAD);
    }

    @Test
    void postProcessBeanFactory_shouldHandleCustomScopes() {
        // Arrange
        String[] beanNames = {"bean1", "bean2", "bean3"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("bean1")).thenReturn(beanDefinition1);
        when(factory.getBeanDefinition("bean2")).thenReturn(beanDefinition2);
        when(factory.getBeanDefinition("bean3")).thenReturn(beanDefinition3);
        when(beanDefinition1.getScope()).thenReturn("custom-scope");
        when(beanDefinition2.getScope()).thenReturn("session");
        when(beanDefinition3.getScope()).thenReturn("application");

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(beanDefinition1, never()).setScope(anyString());
        verify(beanDefinition2, never()).setScope(anyString());
        verify(beanDefinition3, never()).setScope(anyString());
    }

    @Test
    void postProcessBeanFactory_shouldGetBeanClassNameForRequestScoped() {
        // Arrange
        String[] beanNames = {"bean1"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("bean1")).thenReturn(beanDefinition1);
        when(beanDefinition1.getScope()).thenReturn("request");
        when(beanDefinition1.getBeanClassName()).thenReturn("com.example.MyBean");

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(beanDefinition1).getBeanClassName();
    }

    @Test
    void postProcessBeanFactory_shouldHandleNullBeanClassName() {
        // Arrange
        String[] beanNames = {"bean1"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("bean1")).thenReturn(beanDefinition1);
        when(beanDefinition1.getScope()).thenReturn("request");
        when(beanDefinition1.getBeanClassName()).thenReturn(null);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> scopeModifierPostProcessor.postProcessBeanFactory(factory));
        verify(beanDefinition1).setScope(ScopeModifierPostProcessor.SCOPE_THREAD);
    }

    @Test
    void postProcessBeanFactory_shouldNotGetBeanClassNameForNonRequestScoped() {
        // Arrange
        String[] beanNames = {"bean1"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("bean1")).thenReturn(beanDefinition1);
        when(beanDefinition1.getScope()).thenReturn("singleton");

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(beanDefinition1, never()).getBeanClassName();
    }

    @Test
    void postProcessBeanFactory_shouldImplementBeanFactoryPostProcessor() {
        // Assert
        assertTrue(scopeModifierPostProcessor instanceof org.springframework.beans.factory.config.BeanFactoryPostProcessor);
    }

    @ParameterizedTest
    @MethodSource("provideScopesThatShouldNotBeModified")
    @DisplayName("Should not modify beans with invalid or non-modifiable scopes")
    void testPostProcessBeanFactory_shouldNotModifyInvalidOrNonModifiableScopes(
            String scope,
            String testDescription) {
        // Arrange
        String[] beanNames = {"bean1"};
        when(factory.getBeanDefinitionNames()).thenReturn(beanNames);
        when(factory.getBeanDefinition("bean1")).thenReturn(beanDefinition1);
        when(beanDefinition1.getScope()).thenReturn(scope);

        // Act
        scopeModifierPostProcessor.postProcessBeanFactory(factory);

        // Assert
        verify(beanDefinition1, never()).setScope(anyString());
    }

    private static Stream<Arguments> provideScopesThatShouldNotBeModified() {
        return Stream.of(
                Arguments.of(null, "null scope"),
                Arguments.of("", "empty string scope"),
                Arguments.of("   ", "whitespace scope"),
                Arguments.of("scope_thread", "scope_thread beans")
        );
    }
}
