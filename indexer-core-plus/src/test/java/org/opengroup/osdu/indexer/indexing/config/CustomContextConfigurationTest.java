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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomContextConfigurationTest {

    @Mock
    private ApplicationContext applicationContext;

    private CustomContextConfiguration configuration;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        configuration = new CustomContextConfiguration(applicationContext);

        // Setup log capture
        logger = (Logger) LoggerFactory.getLogger(CustomContextConfiguration.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    @Test
    void setUp_shouldLogContextId() {
        // Arrange
        String contextId = "test-context-id-123";
        when(applicationContext.getId()).thenReturn(contextId);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{});

        // Act
        configuration.setUp();

        // Assert
        verify(applicationContext, atLeastOnce()).getId();

        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .isNotEmpty()
                .anyMatch(event ->
                        event.getLevel() == Level.DEBUG &&
                                event.getFormattedMessage().contains("Messaging context initialized with id: " + contextId)
                );
    }

    @Test
    void setUp_shouldLogContextStatus() {
        // Arrange
        String contextId = "test-context-id";
        when(applicationContext.getId()).thenReturn(contextId);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{});
        // Note: We stub toString() but don't verify it (Mockito doesn't allow verifying toString)
        when(applicationContext.toString()).thenReturn("Mock ApplicationContext");

        // Act
        configuration.setUp();

        // Assert - Just verify the log contains the expected message
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .anyMatch(event ->
                        event.getLevel() == Level.DEBUG &&
                                event.getFormattedMessage().contains("Messaging context status:")
                );
    }

    @Test
    void setUp_shouldLogBeanDefinitions() {
        // Arrange
        String[] beanNames = {"bean1", "bean2", "bean3"};
        when(applicationContext.getId()).thenReturn("test-context");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(beanNames);

        // Act
        configuration.setUp();

        // Assert
        verify(applicationContext, times(1)).getBeanDefinitionNames();

        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .anyMatch(event ->
                        event.getLevel() == Level.DEBUG &&
                                event.getFormattedMessage().contains("Messaging context beans definitions:")
                );
    }

    @Test
    void setUp_shouldRetrieveAllBeanDefinitionNames() {
        // Arrange
        String[] beanNames = {"bean1", "bean2", "bean3", "bean4"};
        when(applicationContext.getId()).thenReturn("test-context");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(beanNames);

        // Act
        configuration.setUp();

        // Assert
        verify(applicationContext, times(1)).getBeanDefinitionNames();
    }

    @Test
    void setUp_shouldHandleEmptyBeanDefinitions() {
        // Arrange
        String[] emptyBeanNames = {};
        when(applicationContext.getId()).thenReturn("test-context");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(emptyBeanNames);

        // Act
        configuration.setUp();

        // Assert
        verify(applicationContext, times(1)).getBeanDefinitionNames();

        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .anyMatch(event ->
                        event.getLevel() == Level.DEBUG &&
                                event.getFormattedMessage().contains("[]")
                );
    }

    @Test
    void setUp_shouldHandleLargeBeanDefinitionsList() {
        // Arrange
        String[] manyBeans = new String[100];
        for (int i = 0; i < 100; i++) {
            manyBeans[i] = "bean" + i;
        }
        when(applicationContext.getId()).thenReturn("test-context");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(manyBeans);

        // Act
        configuration.setUp();

        // Assert
        verify(applicationContext, times(1)).getBeanDefinitionNames();

        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents).hasSize(3); // Three debug log statements
    }

    @Test
    void setUp_shouldCallAllApplicationContextMethodsInCorrectOrder() {
        // Arrange
        String contextId = "ordered-test-context";
        String[] beanNames = {"bean1", "bean2"};

        when(applicationContext.getId()).thenReturn(contextId);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(beanNames);

        // Act
        configuration.setUp();

        // Assert
        var inOrder = inOrder(applicationContext);
        inOrder.verify(applicationContext).getId();
        inOrder.verify(applicationContext).getBeanDefinitionNames();
    }

    @Test
    void setUp_shouldLogAllThreeDebugMessages() {
        // Arrange
        when(applicationContext.getId()).thenReturn("test-id");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{"bean1"});

        // Act
        configuration.setUp();

        // Assert
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .hasSize(3)
                .allMatch(event -> event.getLevel() == Level.DEBUG);
    }

    @Test
    void setUp_shouldHandleNullContextId() {
        // Arrange
        when(applicationContext.getId()).thenReturn(null);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{});

        // Act
        configuration.setUp();

        // Assert
        verify(applicationContext, atLeastOnce()).getId();

        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .anyMatch(event ->
                        event.getLevel() == Level.DEBUG &&
                                event.getFormattedMessage().contains("null")
                );
    }

    @Test
    void setUp_shouldLogBeanNamesInArrayFormat() {
        // Arrange
        String[] beanNames = {"beanA", "beanB", "beanC"};
        when(applicationContext.getId()).thenReturn("test-context");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(beanNames);

        // Act
        configuration.setUp();

        // Assert
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .anyMatch(event -> {
                    String message = event.getFormattedMessage();
                    return message.contains("beanA") &&
                            message.contains("beanB") &&
                            message.contains("beanC");
                });
    }

    @Test
    void constructor_shouldAcceptApplicationContext() {
        // Act
        CustomContextConfiguration config = new CustomContextConfiguration(applicationContext);

        // Assert
        assertThat(config).isNotNull();
    }

    @Test
    void setUp_shouldBeCallableMultipleTimes() {
        // Arrange
        when(applicationContext.getId()).thenReturn("test-id");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{"bean1"});

        // Act
        configuration.setUp();
        configuration.setUp();

        // Assert
        verify(applicationContext, times(2)).getId();
        verify(applicationContext, times(2)).getBeanDefinitionNames();
    }

    @Test
    void setUp_shouldHandleSpecialCharactersInBeanNames() {
        // Arrange
        String[] beanNames = {"bean-with-dashes", "bean_with_underscores", "bean.with.dots"};
        when(applicationContext.getId()).thenReturn("test-context");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(beanNames);

        // Act
        configuration.setUp();

        // Assert
        verify(applicationContext, times(1)).getBeanDefinitionNames();

        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .anyMatch(event -> {
                    String message = event.getFormattedMessage();
                    return message.contains("bean-with-dashes") &&
                            message.contains("bean_with_underscores") &&
                            message.contains("bean.with.dots");
                });
    }

    @Test
    void setUp_shouldHandleContextWithLongId() {
        // Arrange
        String longContextId = "very-long-context-id-with-many-characters-" +
                "that-represents-a-complex-application-context-identifier-123456789";
        when(applicationContext.getId()).thenReturn(longContextId);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{});

        // Act
        configuration.setUp();

        // Assert
        verify(applicationContext, atLeastOnce()).getId();

        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .anyMatch(event -> event.getFormattedMessage().contains(longContextId));
    }

    @Test
    void setUp_shouldLogCorrectMessageFormats() {
        // Arrange
        String contextId = "test-context-id";
        String[] beanNames = {"bean1"};
        when(applicationContext.getId()).thenReturn(contextId);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(beanNames);

        // Act
        configuration.setUp();

        // Assert
        List<ILoggingEvent> logEvents = logAppender.list;

        // Check first log message format
        assertThat(logEvents.get(0).getFormattedMessage())
                .matches("Messaging context initialized with id: .*\\.");

        // Check second log message format
        assertThat(logEvents.get(1).getFormattedMessage())
                .matches("Messaging context status: .*\\.");

        // Check third log message format
        assertThat(logEvents.get(2).getFormattedMessage())
                .matches("Messaging context beans definitions: \\[.*\\]\\.");
    }

    @Test
    void setUp_shouldNotThrowExceptionWhenLoggingFails() {
        // Arrange
        when(applicationContext.getId()).thenReturn("test-id");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{"bean1"});

        // Remove appender to simulate logging failure
        logger.detachAppender(logAppender);

        // Act & Assert - should not throw exception
        configuration.setUp();

        verify(applicationContext, atLeastOnce()).getId();
        verify(applicationContext, times(1)).getBeanDefinitionNames();
    }

    @Test
    void setUp_shouldAccessContextIdBeforeBeanDefinitions() {
        // Arrange
        when(applicationContext.getId()).thenReturn("test-id");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{"bean1"});

        // Act
        configuration.setUp();

        // Assert - verify order of method calls
        var inOrder = inOrder(applicationContext);
        inOrder.verify(applicationContext).getId();
        inOrder.verify(applicationContext).getBeanDefinitionNames();
    }
}
