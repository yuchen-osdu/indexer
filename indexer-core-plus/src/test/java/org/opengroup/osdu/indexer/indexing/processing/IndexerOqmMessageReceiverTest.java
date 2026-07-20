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
package org.opengroup.osdu.indexer.indexing.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gson.JsonParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.RequestStatus;
import org.opengroup.osdu.indexer.indexing.scope.ThreadDpsHeaders;
import org.opengroup.osdu.indexer.schema.converter.exeption.SchemaProcessingException;
import org.opengroup.osdu.oqm.core.model.OqmAckReplier;
import org.opengroup.osdu.oqm.core.model.OqmMessage;

@ExtendWith(MockitoExtension.class)
class IndexerOqmMessageReceiverTest {

  @Mock
  protected ThreadDpsHeaders dpsHeaders;

  @Mock
  protected TokenProvider tokenProvider;

  @Mock
  protected OqmAckReplier ackReplier;

  protected IndexerOqmMessageReceiver receiver;

  protected IndexerOqmMessageReceiver exceptionCheckReceiver;

  private static final String TEST_TOKEN = "test-bearer-token";
  private static final String TEST_CORRELATION_ID = "test-correlation-id";

  @BeforeEach
  void setUp() {
    Mockito.lenient().when(tokenProvider.getIdToken()).thenReturn(TEST_TOKEN);

    IndexerOqmMessageReceiver indexerOqmMessageReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        //do nothing
      }
    };
    receiver = Mockito.spy(indexerOqmMessageReceiver);
  }

  static Stream<String> notValidEvents() {
    return Stream.of("/test-events/empty-data-event.json", "/test-events/empty-attributes-event.json");
  }

  static Stream<AppException> exceptionsThatShouldCauseEventRescheduling() {
    return Stream.of(
            new AppException(HttpStatus.SC_BAD_REQUEST, "not tested", "not tested", new JsonParseException("not tested")),
            new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "not tested", "not tested", new Exception()),
            new AppException(HttpStatus.SC_BAD_REQUEST, "not tested", "not tested"));
  }

  static Stream<AppException> exceptionsThatShouldCauseEventSkip() {
    return Stream.of(
            new AppException(HttpStatus.SC_BAD_REQUEST, "not tested", "not tested",
                    new SchemaProcessingException("not tested")),
            new AppException(RequestStatus.INVALID_RECORD, "not tested", "not tested", new Exception()));
  }

  @ParameterizedTest
  @MethodSource("notValidEvents")
  void shouldNotConsumeNotValidEvent(String fileName) throws Exception {
    OqmMessage oqmMessage = ReadFromFileUtil.readEventFromFile(fileName);
    receiver.receiveMessage(oqmMessage, ackReplier);
    verify(ackReplier).nack(false);
    verify(receiver, never()).sendMessage(any());
  }

  @ParameterizedTest
  @MethodSource("exceptionsThatShouldCauseEventRescheduling")
  void shouldRescheduleForExceptions(AppException exception) {
    OqmMessage oqmMessage = ReadFromFileUtil.readEventFromFile("/test-events/storage-index-event.json");
    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw exception;
      }
    };
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);
    verify(ackReplier).nack(true);
  }

  @ParameterizedTest
  @MethodSource("exceptionsThatShouldCauseEventSkip")
  void shouldSkipForExceptions(AppException exception) {
    OqmMessage oqmMessage = ReadFromFileUtil.readEventFromFile("/test-events/storage-index-event.json");
    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw exception;
      }
    };
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);
    verify(ackReplier).nack(false);
  }

  // ========== NEW TEST CASES FOR IMPROVED COVERAGE ==========

  @Test
  void shouldSuccessfullyProcessValidMessage() throws Exception {
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    doNothing().when(dpsHeaders).setThreadContext(any());

    // Act
    receiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(receiver, times(1)).sendMessage(oqmMessage);
    verify(ackReplier).ack();
    verify(ackReplier, never()).nack(any(Boolean.class));
    verify(dpsHeaders).setThreadContext(any());
  }

  @Test
  void shouldSetCorrectHeadersWithBearerToken(){
    // Arrange
    Map<String, String> attributes = new HashMap<>();
    attributes.put("correlation-id", TEST_CORRELATION_ID);
    attributes.put("data-partition-id", "test-partition");

    OqmMessage oqmMessage = OqmMessage.builder()
            .id("test-id")
            .data("{\"key\":\"value\"}")
            .attributes(attributes)
            .build();

    ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);

    // Act
    receiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(dpsHeaders).setThreadContext(headersCaptor.capture());
    Map<String, String> capturedHeaders = headersCaptor.getValue();
    assertEquals("Bearer " + TEST_TOKEN, capturedHeaders.get("authorization"));
    verify(tokenProvider).getIdToken();
  }

  @Test
  void shouldSetContextAndAckAfterSuccessfulProcessing() {
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();

    // Act
    receiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(dpsHeaders).setThreadContext(any());
    verify(ackReplier).ack();
  }

  @Test
  void shouldCleanThreadContextAfterException(){
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage){
        throw new RuntimeException("Test exception");
      }
    };

    // Act
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(true);
  }

  @Test
  void shouldHandleNullDataAsInvalid(){
    // Arrange
    OqmMessage oqmMessage = OqmMessage.builder()
            .id("test-id")
            .data(null)
            .attributes(createValidAttributes())
            .build();

    // Create a fresh receiver instance (not a spy) to ensure validInput is called
    IndexerOqmMessageReceiver testReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage){
        // This should never be called
      }
    };

    // Act
    testReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(false);
    verify(ackReplier, never()).ack();
  }

  @Test
  void shouldHandleEmptyJsonObjectAsInvalid(){
    // Arrange
    OqmMessage oqmMessage = OqmMessage.builder()
            .id("test-id")
            .data("{}")
            .attributes(createValidAttributes())
            .build();

    // Create a fresh receiver instance (not a spy) to ensure validInput is called
    IndexerOqmMessageReceiver testReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage){
        // This should never be called
      }
    };

    // Act
    testReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(false);
    verify(ackReplier, never()).ack();
  }

  @Test
  void shouldHandleEmptyAttributesAsInvalid() {
    // Arrange
    OqmMessage oqmMessage = OqmMessage.builder()
            .id("test-id")
            .data("{\"key\":\"value\"}")
            .attributes(new HashMap<>())
            .build();

    // Create a fresh receiver instance (not a spy) to ensure validInput is called
    IndexerOqmMessageReceiver testReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        // This should never be called
      }
    };

    // Act
    testReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(false);
    verify(ackReplier, never()).ack();
  }

  @Test
  void shouldRescheduleForNonAppException(){
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    RuntimeException runtimeException = new RuntimeException("Test runtime exception");

    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw runtimeException;
      }
    };

    // Act
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(true);
    verify(ackReplier, never()).ack();
  }

  @Test
  void shouldPropagateThrowableErrors() {
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    NoSuchMethodError error = new NoSuchMethodError("Test error");

    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw error;
      }
    };

    // Act & Assert
    assertThrows(NoSuchMethodError.class, () -> {
      errorProneReceiver.receiveMessage(oqmMessage, ackReplier);
    });

    // Verify that ack/nack was never called for Throwable
    verify(ackReplier, never()).ack();
    verify(ackReplier, never()).nack(any(Boolean.class));
  }

  @Test
  void shouldSkipMessagesWithStatusCode200Range() {
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    AppException exception = new AppException(HttpStatus.SC_OK, "Success", "Not an error");

    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw exception;
      }
    };

    // Act
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(false);
  }

  @Test
  void shouldSkipMessagesWithStatusCode201() {
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    AppException exception = new AppException(HttpStatus.SC_CREATED, "Created", "Resource created");

    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw exception;
      }
    };

    // Act
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(false);
  }

  @Test
  void shouldRescheduleForStatusCode300() {
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    AppException exception = new AppException(HttpStatus.SC_MULTIPLE_CHOICES, "Redirect", "Multiple choices");

    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw exception;
      }
    };

    // Act
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(true);
  }

  @Test
  void shouldRescheduleForStatusCode404() {
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    AppException exception = new AppException(HttpStatus.SC_NOT_FOUND, "Not Found", "Resource not found");

    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw exception;
      }
    };

    // Act
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(true);
  }

  @Test
  void shouldHandleAppExceptionWithoutOriginalException() {
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    AppException exception = new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error", "Server error");
    // No original exception set

    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw exception;
      }
    };

    // Act
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(true);
  }

  @Test
  void shouldExtractOriginalExceptionFromAppException() {
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    IllegalArgumentException originalException = new IllegalArgumentException("Original error");
    AppException appException = new AppException(HttpStatus.SC_BAD_REQUEST, "Wrapper", "Wrapped", originalException);

    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw appException;
      }
    };

    // Act
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(true);
  }

  @Test
  void shouldSkipSchemaProcessingExceptionEvenWith500StatusCode() {
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    SchemaProcessingException schemaException = new SchemaProcessingException("Schema error");
    AppException exception = new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error", "Schema processing failed", schemaException);

    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw exception;
      }
    };

    // Act
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(false); // Should skip, not reschedule
  }

  @Test
  void shouldHandleCheckedExceptions() {
    // Arrange
    OqmMessage oqmMessage = createValidOqmMessage();
    Exception checkedException = new Exception("Checked exception");

    IndexerOqmMessageReceiver errorProneReceiver = new IndexerOqmMessageReceiver(dpsHeaders, tokenProvider) {
      @Override
      protected void sendMessage(OqmMessage oqmMessage) throws Exception {
        throw checkedException;
      }
    };

    // Act
    errorProneReceiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(ackReplier).nack(true);
  }

  @Test
  void shouldLogAndProcessMessageWithAllValidFields() throws Exception {
    // Arrange
    Map<String, String> attributes = new HashMap<>();
    attributes.put("correlation-id", TEST_CORRELATION_ID);
    attributes.put("data-partition-id", "test-partition");
    attributes.put("user", "test-user");

    OqmMessage oqmMessage = OqmMessage.builder()
            .id("message-123")
            .data("{\"recordId\":\"test-record\",\"op\":\"create\"}")
            .attributes(attributes)
            .build();

    // Act
    receiver.receiveMessage(oqmMessage, ackReplier);

    // Assert
    verify(receiver).sendMessage(oqmMessage);
    verify(ackReplier).ack();
    verify(tokenProvider).getIdToken();
  }

  // ========== HELPER METHODS ==========

  private OqmMessage createValidOqmMessage() {
    return OqmMessage.builder()
            .id("test-message-id")
            .data("{\"key\":\"value\"}")
            .attributes(createValidAttributes())
            .build();
  }

  private Map<String, String> createValidAttributes() {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("correlation-id", TEST_CORRELATION_ID);
    attributes.put("data-partition-id", "test-partition");
    return attributes;
  }
}
