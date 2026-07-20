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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ThreadScopeContextHolder Tests")
class ThreadScopeContextHolderTest {

    @BeforeEach
    void setUp() {
        // Clean up ThreadLocal before each test
        ThreadScopeContextHolder.removeThreadScopeAttributes();
    }

    @AfterEach
    void tearDown() {
        // Clean up ThreadLocal after each test to prevent leaks
        ThreadScopeContextHolder.removeThreadScopeAttributes();
    }

    @Test
    @DisplayName("Should return ThreadScopeAttributes on first access")
    void testGetThreadScopeAttributes_ShouldReturnInitialValue() {
        // Act
        ThreadScopeAttributes attributes = ThreadScopeContextHolder.getThreadScopeAttributes();

        // Assert
        assertNotNull(attributes);
        assertNotNull(attributes.getBeanMap());
        assertTrue(attributes.getBeanMap().isEmpty());
    }

    @Test
    @DisplayName("Should return same instance on multiple calls from same thread")
    void testGetThreadScopeAttributes_ShouldReturnSameInstance() {
        // Act
        ThreadScopeAttributes attributes1 = ThreadScopeContextHolder.getThreadScopeAttributes();
        ThreadScopeAttributes attributes2 = ThreadScopeContextHolder.getThreadScopeAttributes();

        // Assert
        assertSame(attributes1, attributes2);
    }

    @Test
    @DisplayName("Should set and get custom ThreadScopeAttributes")
    void testSetThreadScopeAttributes_ShouldSetCustomInstance() {
        // Arrange
        ThreadScopeAttributes customAttributes = new ThreadScopeAttributes();
        customAttributes.getBeanMap().put("customBean", "testValue");

        // Act
        ThreadScopeContextHolder.setThreadScopeAttributes(customAttributes);
        ThreadScopeAttributes retrievedAttributes = ThreadScopeContextHolder.getThreadScopeAttributes();

        // Assert
        assertSame(customAttributes, retrievedAttributes);
        assertEquals("testValue", retrievedAttributes.getBeanMap().get("customBean"));
    }

    @Test
    @DisplayName("Should return current thread scope attributes successfully")
    void testCurrentThreadScopeAttributes_ShouldReturnAttributes() {
        // Act
        ThreadScopeAttributes attributes = ThreadScopeContextHolder.currentThreadScopeAttributes();

        // Assert
        assertNotNull(attributes);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when attributes are null")
    void testCurrentThreadScopeAttributes_ShouldThrowWhenNull() {
        // Arrange
        ThreadScopeContextHolder.removeThreadScopeAttributes();
        ThreadScopeContextHolder.setThreadScopeAttributes(null);

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                ThreadScopeContextHolder::currentThreadScopeAttributes
        );
        assertEquals("No thread scoped attributes.", exception.getMessage());
    }

    @Test
    @DisplayName("Should remove thread scope attributes successfully")
    void testRemoveThreadScopeAttributes_ShouldRemove() {
        // Arrange
        ThreadScopeAttributes attributes = ThreadScopeContextHolder.getThreadScopeAttributes();
        attributes.getBeanMap().put("testBean", "value");

        // Act
        ThreadScopeContextHolder.removeThreadScopeAttributes();

        // Assert - After removal, a new instance should be created on next access
        ThreadScopeAttributes newAttributes = ThreadScopeContextHolder.getThreadScopeAttributes();
        assertNotSame(attributes, newAttributes);
        assertTrue(newAttributes.getBeanMap().isEmpty());
    }

    @Test
    @DisplayName("Should clear attributes before removing")
    void testRemoveThreadScopeAttributes_ShouldClearBeforeRemoving() {
        // Arrange
        ThreadScopeAttributes attributes = ThreadScopeContextHolder.getThreadScopeAttributes();
        final boolean[] callbackExecuted = {false};

        attributes.registerRequestDestructionCallback("testCallback", () -> {
            callbackExecuted[0] = true;
        });

        // Act
        ThreadScopeContextHolder.removeThreadScopeAttributes();

        // Assert
        assertTrue(callbackExecuted[0], "Destruction callback should have been executed");
    }

    @Test
    @DisplayName("Should handle removal when attributes are null")
    void testRemoveThreadScopeAttributes_WithNullAttributes() {
        // Arrange
        ThreadScopeContextHolder.setThreadScopeAttributes(null);

        // Act & Assert - should not throw exception
        assertDoesNotThrow(ThreadScopeContextHolder::removeThreadScopeAttributes);
    }

    @Test
    @DisplayName("Should maintain separate attributes per thread")
    void testThreadIsolation_DifferentThreadsShouldHaveDifferentAttributes() throws InterruptedException {
        // Arrange
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<ThreadScopeAttributes> thread1Attributes = new AtomicReference<>();
        AtomicReference<ThreadScopeAttributes> thread2Attributes = new AtomicReference<>();

        // Act
        Thread thread1 = new Thread(() -> {
            ThreadScopeAttributes attrs = ThreadScopeContextHolder.getThreadScopeAttributes();
            attrs.getBeanMap().put("thread", "thread1");
            thread1Attributes.set(attrs);
            latch.countDown();
        });

        Thread thread2 = new Thread(() -> {
            ThreadScopeAttributes attrs = ThreadScopeContextHolder.getThreadScopeAttributes();
            attrs.getBeanMap().put("thread", "thread2");
            thread2Attributes.set(attrs);
            latch.countDown();
        });

        thread1.start();
        thread2.start();
        latch.await(5, TimeUnit.SECONDS);

        // Assert
        assertNotSame(thread1Attributes.get(), thread2Attributes.get());
        assertEquals("thread1", thread1Attributes.get().getBeanMap().get("thread"));
        assertEquals("thread2", thread2Attributes.get().getBeanMap().get("thread"));
    }

    @Test
    @DisplayName("Should inherit attributes in child threads with InheritableThreadLocal")
    void testInheritableThreadLocal_ChildThreadShouldInheritAttributes() throws InterruptedException {
        // Arrange
        ThreadScopeAttributes parentAttributes = ThreadScopeContextHolder.getThreadScopeAttributes();
        parentAttributes.getBeanMap().put("parentBean", "parentValue");

        AtomicReference<ThreadScopeAttributes> childAttributes = new AtomicReference<>();
        AtomicReference<Object> childBeanValue = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Act
        Thread childThread = new Thread(() -> {
            ThreadScopeAttributes attrs = ThreadScopeContextHolder.getThreadScopeAttributes();
            childAttributes.set(attrs);
            childBeanValue.set(attrs.getBeanMap().get("parentBean"));
            latch.countDown();
        });

        childThread.start();
        latch.await(5, TimeUnit.SECONDS);

        // Assert
        assertNotNull(childAttributes.get());
        // InheritableThreadLocal passes the same instance to child thread
        assertSame(parentAttributes, childAttributes.get());
        assertEquals("parentValue", childBeanValue.get());
    }

    @Test
    @DisplayName("Should handle concurrent access from multiple threads")
    void testConcurrentAccess_MultipleThreads() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<ThreadScopeAttributes> attributesList = new ArrayList<>();

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    ThreadScopeAttributes attrs = ThreadScopeContextHolder.getThreadScopeAttributes();
                    attrs.getBeanMap().put("threadId", threadId);
                    synchronized (attributesList) {
                        attributesList.add(attrs);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Assert
        assertEquals(threadCount, attributesList.size());
        // Each thread should have its own unique instance
        long uniqueInstances = attributesList.stream().distinct().count();
        assertEquals(threadCount, uniqueInstances);
    }

    @Test
    @DisplayName("Should allow multiple set and get operations")
    void testMultipleSetAndGet_ShouldWork() {
        // Arrange & Act
        ThreadScopeAttributes attrs1 = new ThreadScopeAttributes();
        attrs1.getBeanMap().put("key1", "value1");
        ThreadScopeContextHolder.setThreadScopeAttributes(attrs1);

        ThreadScopeAttributes retrieved1 = ThreadScopeContextHolder.getThreadScopeAttributes();

        ThreadScopeAttributes attrs2 = new ThreadScopeAttributes();
        attrs2.getBeanMap().put("key2", "value2");
        ThreadScopeContextHolder.setThreadScopeAttributes(attrs2);

        ThreadScopeAttributes retrieved2 = ThreadScopeContextHolder.getThreadScopeAttributes();

        // Assert
        assertSame(attrs1, retrieved1);
        assertSame(attrs2, retrieved2);
        assertNotSame(attrs1, attrs2);
        assertEquals("value2", retrieved2.getBeanMap().get("key2"));
    }

    @Test
    @DisplayName("Should handle remove and re-access scenario")
    void testRemoveAndReAccess_ShouldCreateNewInstance() {
        // Arrange
        ThreadScopeAttributes originalAttributes = ThreadScopeContextHolder.getThreadScopeAttributes();
        originalAttributes.getBeanMap().put("original", "data");

        // Act
        ThreadScopeContextHolder.removeThreadScopeAttributes();
        ThreadScopeAttributes newAttributes = ThreadScopeContextHolder.getThreadScopeAttributes();

        // Assert
        assertNotSame(originalAttributes, newAttributes);
        assertTrue(newAttributes.getBeanMap().isEmpty());
    }

    @Test
    @DisplayName("Should maintain thread scope after setting custom attributes")
    void testThreadScope_AfterSettingCustomAttributes() {
        // Arrange
        ThreadScopeAttributes customAttrs = new ThreadScopeAttributes();
        customAttrs.getBeanMap().put("custom", "value");

        // Act
        ThreadScopeContextHolder.setThreadScopeAttributes(customAttrs);
        ThreadScopeAttributes current = ThreadScopeContextHolder.currentThreadScopeAttributes();

        // Assert
        assertSame(customAttrs, current);
        assertEquals("value", current.getBeanMap().get("custom"));
    }

    @Test
    @DisplayName("Should execute destruction callbacks on remove")
    void testRemove_ShouldExecuteDestructionCallbacks() {
        // Arrange
        ThreadScopeAttributes attributes = ThreadScopeContextHolder.getThreadScopeAttributes();
        final int[] callbackCount = {0};

        attributes.registerRequestDestructionCallback("callback1", () -> callbackCount[0]++);
        attributes.registerRequestDestructionCallback("callback2", () -> callbackCount[0]++);
        attributes.registerRequestDestructionCallback("callback3", () -> callbackCount[0]++);

        // Act
        ThreadScopeContextHolder.removeThreadScopeAttributes();

        // Assert
        assertEquals(3, callbackCount[0]);
    }

    @Test
    @DisplayName("Should handle multiple remove calls safely")
    void testMultipleRemove_ShouldNotThrowException() {
        // Arrange
        ThreadScopeContextHolder.getThreadScopeAttributes();

        // Act & Assert
        assertDoesNotThrow(() -> {
            ThreadScopeContextHolder.removeThreadScopeAttributes();
            ThreadScopeContextHolder.removeThreadScopeAttributes();
            ThreadScopeContextHolder.removeThreadScopeAttributes();
        });
    }

    @Test
    @DisplayName("Constructor should be private")
    void testConstructor_ShouldBePrivate() throws NoSuchMethodException {
        // Act
        var constructor = ThreadScopeContextHolder.class.getDeclaredConstructor();

        // Assert
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    @DisplayName("Should return fresh instance after complete cleanup")
    void testCompleteCleanupCycle() {
        // Arrange
        ThreadScopeAttributes attrs1 = ThreadScopeContextHolder.getThreadScopeAttributes();
        attrs1.getBeanMap().put("bean1", "value1");
        attrs1.registerRequestDestructionCallback("callback", () -> {});

        // Act
        ThreadScopeContextHolder.removeThreadScopeAttributes();
        ThreadScopeAttributes attrs2 = ThreadScopeContextHolder.getThreadScopeAttributes();
        attrs2.getBeanMap().put("bean2", "value2");

        // Assert
        assertNotSame(attrs1, attrs2);
        assertNull(attrs2.getBeanMap().get("bean1"));
        assertEquals("value2", attrs2.getBeanMap().get("bean2"));
    }

    @Test
    @DisplayName("Should verify InheritableThreadLocal behavior with modifications")
    void testInheritableThreadLocal_WithModifications() throws InterruptedException {
        // Arrange
        ThreadScopeAttributes parentAttributes = ThreadScopeContextHolder.getThreadScopeAttributes();
        parentAttributes.getBeanMap().put("sharedBean", "initialValue");

        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch childModified = new CountDownLatch(1);
        CountDownLatch parentVerified = new CountDownLatch(1);

        // Act
        Thread childThread = new Thread(() -> {
            ThreadScopeAttributes childAttrs = ThreadScopeContextHolder.getThreadScopeAttributes();
            childStarted.countDown();

            // Modify in child thread
            childAttrs.getBeanMap().put("sharedBean", "modifiedByChild");
            childModified.countDown();

            try {
                parentVerified.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        childThread.start();
        childStarted.await(5, TimeUnit.SECONDS);
        childModified.await(5, TimeUnit.SECONDS);

        // Assert - Since it's the same instance, parent sees child's modifications
        assertEquals("modifiedByChild", parentAttributes.getBeanMap().get("sharedBean"));
        parentVerified.countDown();
        childThread.join(5000);
    }

    @Test
    @DisplayName("Should handle null set followed by get")
    void testNullSet_FollowedByGet() {
        // Act
        ThreadScopeContextHolder.setThreadScopeAttributes(null);

        // Assert - get() should return null, not create a new instance
        assertNull(ThreadScopeContextHolder.getThreadScopeAttributes());
    }

    @Test
    @DisplayName("Should create new instance for each independent thread")
    void testIndependentThreads_ShouldHaveOwnInstances() throws InterruptedException {
        // Arrange
        CountDownLatch latch = new CountDownLatch(3);
        List<ThreadScopeAttributes> instances = new ArrayList<>();

        // Act
        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                synchronized (instances) {
                    instances.add(ThreadScopeContextHolder.getThreadScopeAttributes());
                }
                latch.countDown();
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);

        // Assert
        assertEquals(3, instances.size());
        assertNotSame(instances.get(0), instances.get(1));
        assertNotSame(instances.get(1), instances.get(2));
        assertNotSame(instances.get(0), instances.get(2));
    }
}
