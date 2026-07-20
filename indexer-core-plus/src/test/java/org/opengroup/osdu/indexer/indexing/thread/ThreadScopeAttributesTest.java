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
package org.opengroup.osdu.indexer.indexing.thread;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ThreadScopeAttributes Tests")
class ThreadScopeAttributesTest {

    private ThreadScopeAttributes threadScopeAttributes;

    @BeforeEach
    void setUp() {
        threadScopeAttributes = new ThreadScopeAttributes();
    }

    @Test
    @DisplayName("Should initialize with empty bean map")
    void testConstructor_ShouldInitializeEmptyBeanMap() {
        // Assert
        assertNotNull(threadScopeAttributes.getBeanMap());
        assertTrue(threadScopeAttributes.getBeanMap().isEmpty());
    }

    @Test
    @DisplayName("Should return bean map")
    void testGetBeanMap_ShouldReturnMap() {
        // Act
        Map<String, Object> beanMap = threadScopeAttributes.getBeanMap();

        // Assert
        assertNotNull(beanMap);
        assertTrue(beanMap instanceof Map);
    }

    @Test
    @DisplayName("Should allow adding beans to bean map")
    void testGetBeanMap_ShouldAllowAddingBeans() {
        // Arrange
        Object testBean = new Object();
        String beanName = "testBean";

        // Act
        threadScopeAttributes.getBeanMap().put(beanName, testBean);

        // Assert
        assertEquals(1, threadScopeAttributes.getBeanMap().size());
        assertEquals(testBean, threadScopeAttributes.getBeanMap().get(beanName));
    }

    @Test
    @DisplayName("Should register destruction callback successfully")
    void testRegisterRequestDestructionCallback_ShouldRegisterCallback() {
        // Arrange
        String callbackName = "testCallback";
        Runnable callback = mock(Runnable.class);

        // Act
        threadScopeAttributes.registerRequestDestructionCallback(callbackName, callback);

        // Assert - Callback should be registered (verified during clear)
        threadScopeAttributes.clear();
        verify(callback, times(1)).run();
    }

    @Test
    @DisplayName("Should register multiple destruction callbacks")
    void testRegisterRequestDestructionCallback_WithMultipleCallbacks() {
        // Arrange
        Runnable callback1 = mock(Runnable.class);
        Runnable callback2 = mock(Runnable.class);
        Runnable callback3 = mock(Runnable.class);

        // Act
        threadScopeAttributes.registerRequestDestructionCallback("callback1", callback1);
        threadScopeAttributes.registerRequestDestructionCallback("callback2", callback2);
        threadScopeAttributes.registerRequestDestructionCallback("callback3", callback3);

        // Assert
        threadScopeAttributes.clear();
        verify(callback1, times(1)).run();
        verify(callback2, times(1)).run();
        verify(callback3, times(1)).run();
    }

    @Test
    @DisplayName("Should execute callbacks in order during clear")
    void testClear_ShouldExecuteCallbacksInOrder() {
        // Arrange
        StringBuilder executionOrder = new StringBuilder();
        Runnable callback1 = () -> executionOrder.append("1");
        Runnable callback2 = () -> executionOrder.append("2");
        Runnable callback3 = () -> executionOrder.append("3");

        threadScopeAttributes.registerRequestDestructionCallback("callback1", callback1);
        threadScopeAttributes.registerRequestDestructionCallback("callback2", callback2);
        threadScopeAttributes.registerRequestDestructionCallback("callback3", callback3);

        // Act
        threadScopeAttributes.clear();

        // Assert
        assertEquals("123", executionOrder.toString());
    }

    @Test
    @DisplayName("Should clear bean map when clear is called")
    void testClear_ShouldClearBeanMap() {
        // Arrange
        threadScopeAttributes.getBeanMap().put("bean1", new Object());
        threadScopeAttributes.getBeanMap().put("bean2", new Object());
        threadScopeAttributes.getBeanMap().put("bean3", new Object());

        // Act
        threadScopeAttributes.clear();

        // Assert
        assertTrue(threadScopeAttributes.getBeanMap().isEmpty());
    }

    @Test
    @DisplayName("Should clear destruction callbacks after execution")
    void testClear_ShouldClearDestructionCallbacks() {
        // Arrange
        Runnable callback = mock(Runnable.class);
        threadScopeAttributes.registerRequestDestructionCallback("callback", callback);

        // Act
        threadScopeAttributes.clear();
        threadScopeAttributes.clear(); // Call clear again

        // Assert - callback should only be invoked once (from first clear)
        verify(callback, times(1)).run();
    }

    @Test
    @DisplayName("Should handle clear with no beans or callbacks")
    void testClear_WithEmptyMaps() {
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> threadScopeAttributes.clear());
        assertTrue(threadScopeAttributes.getBeanMap().isEmpty());
    }

    @Test
    @DisplayName("Should handle callback that throws exception")
    void testClear_WithCallbackThatThrowsException() {
        // Arrange
        Runnable failingCallback = mock(Runnable.class);
        doThrow(new RuntimeException("Callback error")).when(failingCallback).run();

        Runnable successCallback = mock(Runnable.class);

        threadScopeAttributes.registerRequestDestructionCallback("failing", failingCallback);
        threadScopeAttributes.registerRequestDestructionCallback("success", successCallback);

        // Act & Assert - exception should propagate
        assertThrows(RuntimeException.class, () -> threadScopeAttributes.clear());
        verify(failingCallback, times(1)).run();
    }

    @Test
    @DisplayName("Should allow same callback name to be overwritten")
    void testRegisterRequestDestructionCallback_OverwriteSameName() {
        // Arrange
        Runnable callback1 = mock(Runnable.class);
        Runnable callback2 = mock(Runnable.class);
        String sameName = "duplicateCallback";

        // Act
        threadScopeAttributes.registerRequestDestructionCallback(sameName, callback1);
        threadScopeAttributes.registerRequestDestructionCallback(sameName, callback2);
        threadScopeAttributes.clear();

        // Assert - only the second callback should be executed
        verify(callback1, never()).run();
        verify(callback2, times(1)).run();
    }

    @Test
    @DisplayName("Should handle multiple clear calls")
    void testClear_MultipleCalls() {
        // Arrange
        Runnable callback = mock(Runnable.class);
        threadScopeAttributes.getBeanMap().put("bean1", new Object());
        threadScopeAttributes.registerRequestDestructionCallback("callback", callback);

        // Act
        threadScopeAttributes.clear();
        threadScopeAttributes.clear();
        threadScopeAttributes.clear();

        // Assert
        verify(callback, times(1)).run();
        assertTrue(threadScopeAttributes.getBeanMap().isEmpty());
    }

    @Test
    @DisplayName("Should maintain bean map reference after clear")
    void testClear_MaintainsBeanMapReference() {
        // Arrange
        Map<String, Object> beanMap = threadScopeAttributes.getBeanMap();
        threadScopeAttributes.getBeanMap().put("bean1", new Object());

        // Act
        threadScopeAttributes.clear();

        // Assert
        assertSame(beanMap, threadScopeAttributes.getBeanMap());
        assertTrue(beanMap.isEmpty());
    }

    @Test
    @DisplayName("Should handle beans of different types")
    void testBeanMap_WithDifferentTypes() {
        // Arrange & Act
        threadScopeAttributes.getBeanMap().put("stringBean", "test");
        threadScopeAttributes.getBeanMap().put("integerBean", 42);
        threadScopeAttributes.getBeanMap().put("objectBean", new Object());

        // Assert
        assertEquals(3, threadScopeAttributes.getBeanMap().size());
        assertEquals("test", threadScopeAttributes.getBeanMap().get("stringBean"));
        assertEquals(42, threadScopeAttributes.getBeanMap().get("integerBean"));
        assertNotNull(threadScopeAttributes.getBeanMap().get("objectBean"));
    }

    @Test
    @DisplayName("Should execute callbacks even when bean map is empty")
    void testClear_ExecutesCallbacksWithEmptyBeanMap() {
        // Arrange
        Runnable callback = mock(Runnable.class);
        threadScopeAttributes.registerRequestDestructionCallback("callback", callback);

        // Act
        threadScopeAttributes.clear();

        // Assert
        verify(callback, times(1)).run();
        assertTrue(threadScopeAttributes.getBeanMap().isEmpty());
    }

    @Test
    @DisplayName("Should clear beans even when no callbacks registered")
    void testClear_ClearsBeansWithNoCallbacks() {
        // Arrange
        threadScopeAttributes.getBeanMap().put("bean1", new Object());
        threadScopeAttributes.getBeanMap().put("bean2", new Object());

        // Act
        threadScopeAttributes.clear();

        // Assert
        assertTrue(threadScopeAttributes.getBeanMap().isEmpty());
    }

    @Test
    @DisplayName("Should handle null values in bean map")
    void testBeanMap_WithNullValues() {
        // Arrange & Act
        threadScopeAttributes.getBeanMap().put("nullBean", null);
        threadScopeAttributes.getBeanMap().put("realBean", new Object());

        // Assert
        assertEquals(2, threadScopeAttributes.getBeanMap().size());
        assertNull(threadScopeAttributes.getBeanMap().get("nullBean"));
        assertNotNull(threadScopeAttributes.getBeanMap().get("realBean"));
    }

    @Test
    @DisplayName("Should allow re-registration after clear")
    void testRegisterRequestDestructionCallback_AfterClear() {
        // Arrange
        Runnable callback1 = mock(Runnable.class);
        Runnable callback2 = mock(Runnable.class);

        threadScopeAttributes.registerRequestDestructionCallback("callback1", callback1);
        threadScopeAttributes.clear();

        // Act
        threadScopeAttributes.registerRequestDestructionCallback("callback2", callback2);
        threadScopeAttributes.clear();

        // Assert
        verify(callback1, times(1)).run();
        verify(callback2, times(1)).run();
    }

    @Test
    @DisplayName("Should handle complex callback logic")
    void testClear_WithComplexCallback() {
        // Arrange
        final int[] counter = {0};
        Runnable complexCallback = () -> {
            counter[0]++;
            if (counter[0] > 1) {
                throw new IllegalStateException("Callback executed multiple times");
            }
        };

        threadScopeAttributes.registerRequestDestructionCallback("complex", complexCallback);

        // Act
        threadScopeAttributes.clear();

        // Assert
        assertEquals(1, counter[0]);
    }
}
