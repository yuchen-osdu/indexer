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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectFactory;

@DisplayName("ThreadScope Test")
class ThreadScopeTest {

    private ThreadScope threadScope;

    @Mock
    private ObjectFactory<Object> mockObjectFactory;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        threadScope = new ThreadScope();
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    @DisplayName("get() should create new bean when not in context")
    void testGet_WhenBeanNotInContext_ShouldCreateNewBean() {
        // Arrange
        String beanName = "testBean";
        Object expectedBean = new Object();

        ThreadScopeAttributes mockAttributes = mock(ThreadScopeAttributes.class);
        Map<String, Object> beanMap = new HashMap<>();

        when(mockAttributes.getBeanMap()).thenReturn(beanMap);

        try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
            mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                    .thenReturn(mockAttributes);
            when(mockObjectFactory.getObject()).thenReturn(expectedBean);

            // Act
            Object result = threadScope.get(beanName, mockObjectFactory);

            // Assert
            assertNotNull(result);
            assertEquals(expectedBean, result);
            assertTrue(beanMap.containsKey(beanName));
            assertEquals(expectedBean, beanMap.get(beanName));
            verify(mockObjectFactory, times(1)).getObject();
        }
    }

    @Test
    @DisplayName("get() should return existing bean when already in context")
    void testGet_WhenBeanExistsInContext_ShouldReturnExistingBean() {
        // Arrange
        String beanName = "testBean";
        Object existingBean = new Object();

        ThreadScopeAttributes mockAttributes = mock(ThreadScopeAttributes.class);
        Map<String, Object> beanMap = new HashMap<>();
        beanMap.put(beanName, existingBean);

        when(mockAttributes.getBeanMap()).thenReturn(beanMap);

        try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
            mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                    .thenReturn(mockAttributes);

            // Act
            Object result = threadScope.get(beanName, mockObjectFactory);

            // Assert
            assertNotNull(result);
            assertEquals(existingBean, result);
            verify(mockObjectFactory, never()).getObject();
        }
    }

    @Test
    @DisplayName("get() should handle multiple different beans")
    void testGet_MultipleBeans_ShouldStoreSeparately() {
        // Arrange
        String beanName1 = "bean1";
        String beanName2 = "bean2";
        Object bean1 = new Object();
        Object bean2 = new Object();

        ThreadScopeAttributes mockAttributes = mock(ThreadScopeAttributes.class);
        Map<String, Object> beanMap = new HashMap<>();

        when(mockAttributes.getBeanMap()).thenReturn(beanMap);

        ObjectFactory<Object> factory1 = mock(ObjectFactory.class);
        ObjectFactory<Object> factory2 = mock(ObjectFactory.class);

        try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
            mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                    .thenReturn(mockAttributes);
            when(factory1.getObject()).thenReturn(bean1);
            when(factory2.getObject()).thenReturn(bean2);

            // Act
            Object result1 = threadScope.get(beanName1, factory1);
            Object result2 = threadScope.get(beanName2, factory2);

            // Assert
            assertEquals(bean1, result1);
            assertEquals(bean2, result2);
            assertEquals(2, beanMap.size());
            assertTrue(beanMap.containsKey(beanName1));
            assertTrue(beanMap.containsKey(beanName2));
        }
    }

    @Test
    @DisplayName("get() should return same instance on multiple calls")
    void testGet_MultipleCalls_ShouldReturnSameInstance() {
        // Arrange
        String beanName = "testBean";
        Object bean = new Object();

        ThreadScopeAttributes mockAttributes = mock(ThreadScopeAttributes.class);
        Map<String, Object> beanMap = new HashMap<>();

        when(mockAttributes.getBeanMap()).thenReturn(beanMap);

        try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
            mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                    .thenReturn(mockAttributes);
            when(mockObjectFactory.getObject()).thenReturn(bean);

            // Act
            Object result1 = threadScope.get(beanName, mockObjectFactory);
            Object result2 = threadScope.get(beanName, mockObjectFactory);
            Object result3 = threadScope.get(beanName, mockObjectFactory);

            // Assert
            assertSame(result1, result2);
            assertSame(result2, result3);
            verify(mockObjectFactory, times(1)).getObject();
        }
    }

    @Test
    @DisplayName("remove() should remove and return existing bean")
    void testRemove_WhenBeanExists_ShouldRemoveAndReturnBean() {
        // Arrange
        String beanName = "testBean";
        Object expectedBean = new Object();

        ThreadScopeAttributes mockAttributes = mock(ThreadScopeAttributes.class);
        Map<String, Object> beanMap = new HashMap<>();
        beanMap.put(beanName, expectedBean);

        when(mockAttributes.getBeanMap()).thenReturn(beanMap);

        try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
            mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                    .thenReturn(mockAttributes);

            // Act
            Object removedBean = threadScope.remove(beanName);

            // Assert
            assertNotNull(removedBean);
            assertEquals(expectedBean, removedBean);
            assertFalse(beanMap.containsKey(beanName));
            assertTrue(beanMap.isEmpty());
        }
    }

    @Test
    @DisplayName("remove() should return null when bean does not exist")
    void testRemove_WhenBeanDoesNotExist_ShouldReturnNull() {
        // Arrange
        String beanName = "nonExistentBean";

        ThreadScopeAttributes mockAttributes = mock(ThreadScopeAttributes.class);
        Map<String, Object> beanMap = new HashMap<>();

        when(mockAttributes.getBeanMap()).thenReturn(beanMap);

        try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
            mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                    .thenReturn(mockAttributes);

            // Act
            Object result = threadScope.remove(beanName);

            // Assert
            assertNull(result);
            assertTrue(beanMap.isEmpty());
        }
    }

    @Test
    @DisplayName("remove() should only remove specified bean")
    void testRemove_ShouldOnlyRemoveSpecifiedBean() {
        // Arrange
        String beanName1 = "bean1";
        String beanName2 = "bean2";
        Object bean1 = new Object();
        Object bean2 = new Object();

        ThreadScopeAttributes mockAttributes = mock(ThreadScopeAttributes.class);
        Map<String, Object> beanMap = new HashMap<>();
        beanMap.put(beanName1, bean1);
        beanMap.put(beanName2, bean2);

        when(mockAttributes.getBeanMap()).thenReturn(beanMap);

        try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
            mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                    .thenReturn(mockAttributes);

            // Act
            Object removed = threadScope.remove(beanName1);

            // Assert
            assertEquals(bean1, removed);
            assertFalse(beanMap.containsKey(beanName1));
            assertTrue(beanMap.containsKey(beanName2));
            assertEquals(1, beanMap.size());
        }
    }

    @Test
    @DisplayName("registerDestructionCallback() should register callback")
    void testRegisterDestructionCallback_ShouldRegisterCallback() {
        // Arrange
        String beanName = "testBean";
        Runnable mockCallback = mock(Runnable.class);

        ThreadScopeAttributes mockAttributes = mock(ThreadScopeAttributes.class);

        try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
            mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                    .thenReturn(mockAttributes);

            // Act & Assert
            assertDoesNotThrow(() -> threadScope.registerDestructionCallback(beanName, mockCallback));
            verify(mockAttributes, times(1)).registerRequestDestructionCallback(beanName, mockCallback);
        }
    }

    @Test
    @DisplayName("registerDestructionCallback() should handle multiple callbacks")
    void testRegisterDestructionCallback_MultipleCallbacks() {
        // Arrange
        String beanName1 = "bean1";
        String beanName2 = "bean2";
        Runnable callback1 = mock(Runnable.class);
        Runnable callback2 = mock(Runnable.class);

        ThreadScopeAttributes mockAttributes = mock(ThreadScopeAttributes.class);

        try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
            mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                    .thenReturn(mockAttributes);

            // Act
            threadScope.registerDestructionCallback(beanName1, callback1);
            threadScope.registerDestructionCallback(beanName2, callback2);

            // Assert
            verify(mockAttributes, times(1)).registerRequestDestructionCallback(beanName1, callback1);
            verify(mockAttributes, times(1)).registerRequestDestructionCallback(beanName2, callback2);
        }
    }

    @Test
    @DisplayName("resolveContextualObject() should always return null")
    void testResolveContextualObject_ShouldReturnNull() {
        // Act
        Object result1 = threadScope.resolveContextualObject("anyKey");
        Object result2 = threadScope.resolveContextualObject("anotherKey");
        Object result3 = threadScope.resolveContextualObject(null);

        // Assert
        assertNull(result1);
        assertNull(result2);
        assertNull(result3);
    }

    @Test
    @DisplayName("getConversationId() should return current thread name")
    void testGetConversationId_ShouldReturnCurrentThreadName() {
        // Arrange
        String expectedThreadName = Thread.currentThread().getName();

        // Act
        String conversationId = threadScope.getConversationId();

        // Assert
        assertNotNull(conversationId);
        assertEquals(expectedThreadName, conversationId);
    }

    @Test
    @DisplayName("getConversationId() should return different names for different threads")
    void testGetConversationId_DifferentThreads() throws InterruptedException {
        // Arrange
        CountDownLatch latch = new CountDownLatch(2);
        String[] threadNames = new String[2];
        String[] conversationIds = new String[2];

        // Act
        Thread thread1 = new Thread(() -> {
            threadNames[0] = Thread.currentThread().getName();
            conversationIds[0] = threadScope.getConversationId();
            latch.countDown();
        }, "TestThread-1");

        Thread thread2 = new Thread(() -> {
            threadNames[1] = Thread.currentThread().getName();
            conversationIds[1] = threadScope.getConversationId();
            latch.countDown();
        }, "TestThread-2");

        thread1.start();
        thread2.start();
        latch.await(5, TimeUnit.SECONDS);

        // Assert
        assertEquals(threadNames[0], conversationIds[0]);
        assertEquals(threadNames[1], conversationIds[1]);
        assertNotEquals(conversationIds[0], conversationIds[1]);
    }

    @Test
    @DisplayName("Full lifecycle: get, remove, and re-get bean")
    void testFullLifecycle_GetRemoveReGet() {
        // Arrange
        String beanName = "lifecycleBean";
        Object firstBean = new Object();
        Object secondBean = new Object();

        ThreadScopeAttributes mockAttributes = mock(ThreadScopeAttributes.class);
        Map<String, Object> beanMap = new HashMap<>();

        when(mockAttributes.getBeanMap()).thenReturn(beanMap);

        try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
            mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                    .thenReturn(mockAttributes);
            when(mockObjectFactory.getObject()).thenReturn(firstBean, secondBean);

            // Act & Assert
            Object initial = threadScope.get(beanName, mockObjectFactory);
            assertEquals(firstBean, initial);
            assertTrue(beanMap.containsKey(beanName));

            Object removed = threadScope.remove(beanName);
            assertEquals(firstBean, removed);
            assertFalse(beanMap.containsKey(beanName));

            Object recreated = threadScope.get(beanName, mockObjectFactory);
            assertEquals(secondBean, recreated);
            assertTrue(beanMap.containsKey(beanName));

            verify(mockObjectFactory, times(2)).getObject();
        }
    }

    @Test
    @DisplayName("Thread isolation: beans in different threads are separate")
    void testThreadIsolation() throws InterruptedException {
        // Arrange
        String beanName = "isolatedBean";
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);
        AtomicInteger factoryCallCount = new AtomicInteger(0);
        Object[] results = new Object[2];

        ObjectFactory<String> factory = () -> {
            factoryCallCount.incrementAndGet();
            return "Bean-" + Thread.currentThread().getName();
        };

        // Act
        Thread thread1 = new Thread(() -> {
            try {
                ThreadScopeAttributes mockAttributes1 = mock(ThreadScopeAttributes.class);
                Map<String, Object> thread1BeanMap = new HashMap<>();
                when(mockAttributes1.getBeanMap()).thenReturn(thread1BeanMap);

                try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
                    mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                            .thenReturn(mockAttributes1);

                    startLatch.await();
                    results[0] = threadScope.get(beanName, factory);
                }
            } catch (Exception e) {
                fail("Thread 1 failed: " + e.getMessage());
            } finally {
                finishLatch.countDown();
            }
        }, "Thread-1");

        Thread thread2 = new Thread(() -> {
            try {
                ThreadScopeAttributes mockAttributes2 = mock(ThreadScopeAttributes.class);
                Map<String, Object> thread2BeanMap = new HashMap<>();
                when(mockAttributes2.getBeanMap()).thenReturn(thread2BeanMap);

                try (MockedStatic<ThreadScopeContextHolder> mockedHolder = mockStatic(ThreadScopeContextHolder.class)) {
                    mockedHolder.when(ThreadScopeContextHolder::currentThreadScopeAttributes)
                            .thenReturn(mockAttributes2);

                    startLatch.await();
                    results[1] = threadScope.get(beanName, factory);
                }
            } catch (Exception e) {
                fail("Thread 2 failed: " + e.getMessage());
            } finally {
                finishLatch.countDown();
            }
        }, "Thread-2");

        thread1.start();
        thread2.start();
        startLatch.countDown();

        assertTrue(finishLatch.await(5, TimeUnit.SECONDS));

        // Assert
        assertNotNull(results[0]);
        assertNotNull(results[1]);
        assertNotEquals(results[0], results[1]);
        assertEquals(2, factoryCallCount.get());
    }
}
