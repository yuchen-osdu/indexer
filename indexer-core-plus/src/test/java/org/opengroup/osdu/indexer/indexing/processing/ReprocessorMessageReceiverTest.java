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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.model.indexer.RecordReindexRequest;
import org.opengroup.osdu.indexer.api.ReindexApi;
import org.opengroup.osdu.indexer.indexing.scope.ThreadDpsHeaders;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReprocessorMessageReceiverTest {

  @Mock
  private ThreadDpsHeaders dpsHeaders;

  @Mock
  private TokenProvider tokenProvider;

  @Mock
  private ReindexApi reindexApi;

  private ReprocessorMessageReceiver reprocessorMessageReceiver;

  private final Gson gson = new Gson();

  @BeforeEach
  void setUp() {
    reprocessorMessageReceiver = new ReprocessorMessageReceiver(
            dpsHeaders,
            tokenProvider,
            reindexApi
    );
  }

  @Test
  void sendMessage_shouldCallReindexApiWithCorrectParameters() throws Exception {
    // Arrange
    RecordReindexRequest reindexRequest = createTestRecordReindexRequest();
    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.ok().build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    ArgumentCaptor<RecordReindexRequest> captor = ArgumentCaptor.forClass(RecordReindexRequest.class);
    verify(reindexApi, times(1)).reindex(captor.capture(), eq(false));

    RecordReindexRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest.getCursor()).isEqualTo(reindexRequest.getCursor());
    assertThat(capturedRequest.getKind()).isEqualTo(reindexRequest.getKind());
  }

  @Test
  void sendMessage_shouldPassFalseAsSecondParameter() throws Exception {
    // Arrange
    RecordReindexRequest reindexRequest = createTestRecordReindexRequest();
    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.ok().build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    verify(reindexApi).reindex(any(RecordReindexRequest.class), eq(false));
  }

  @Test
  void sendMessage_shouldDeserializeJsonDataCorrectly() throws Exception {
    // Arrange
    RecordReindexRequest reindexRequest = new RecordReindexRequest();
    reindexRequest.setCursor("test-cursor-123");
    reindexRequest.setKind("osdu:wks:dataset:1.0.0");

    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.ok().build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    ArgumentCaptor<RecordReindexRequest> captor = ArgumentCaptor.forClass(RecordReindexRequest.class);
    verify(reindexApi).reindex(captor.capture(), eq(false));

    assertThat(captor.getValue().getCursor()).isEqualTo("test-cursor-123");
    assertThat(captor.getValue().getKind()).isEqualTo("osdu:wks:dataset:1.0.0");
  }

  @Test
  void sendMessage_shouldHandleComplexReindexRequest() throws Exception {
    // Arrange
    RecordReindexRequest reindexRequest = new RecordReindexRequest();
    reindexRequest.setCursor("complex-cursor-456");
    reindexRequest.setKind("osdu:wks:master-data:1.0.0");

    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.ok().build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    ArgumentCaptor<RecordReindexRequest> captor = ArgumentCaptor.forClass(RecordReindexRequest.class);
    verify(reindexApi).reindex(captor.capture(), eq(false));

    assertThat(captor.getValue().getCursor()).isEqualTo("complex-cursor-456");
    assertThat(captor.getValue().getKind()).isEqualTo("osdu:wks:master-data:1.0.0");
  }

  @Test
  void sendMessage_shouldPropagateExceptionWhenApiCallFails() throws IOException {
    // Arrange
    RecordReindexRequest reindexRequest = createTestRecordReindexRequest();
    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenThrow(new RuntimeException("API call failed"));

    // Act & Assert
    assertThatThrownBy(() -> reprocessorMessageReceiver.sendMessage(oqmMessage))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("API call failed");

    verify(reindexApi, times(1)).reindex(any(RecordReindexRequest.class), eq(false));
  }

  @Test
  void sendMessage_shouldHandleIOException() throws IOException {
    // Arrange
    RecordReindexRequest reindexRequest = createTestRecordReindexRequest();
    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenThrow(new IOException("Network error"));

    // Act & Assert
    assertThatThrownBy(() -> reprocessorMessageReceiver.sendMessage(oqmMessage))
            .isInstanceOf(IOException.class)
            .hasMessage("Network error");

    verify(reindexApi, times(1)).reindex(any(RecordReindexRequest.class), eq(false));
  }

  @Test
  void sendMessage_shouldHandleJsonParsingException() throws IOException {
    // Arrange
    OqmMessage oqmMessage = mock(OqmMessage.class);
    when(oqmMessage.getData()).thenReturn("invalid-json{{{");

    // Act & Assert
    assertThatThrownBy(() -> reprocessorMessageReceiver.sendMessage(oqmMessage))
            .isInstanceOf(JsonSyntaxException.class);

    verify(reindexApi, never()).reindex(any(RecordReindexRequest.class), anyBoolean());
  }

  @Test
  void sendMessage_shouldHandleNullData() throws Exception {
    // Arrange
    OqmMessage oqmMessage = mock(OqmMessage.class);
    when(oqmMessage.getData()).thenReturn(null);

    when(reindexApi.reindex(any(), eq(false)))
            .thenReturn(ResponseEntity.ok().build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    ArgumentCaptor<RecordReindexRequest> captor = ArgumentCaptor.forClass(RecordReindexRequest.class);
    verify(reindexApi).reindex(captor.capture(), eq(false));
    assertThat(captor.getValue()).isNull();
  }

  @Test
  void sendMessage_shouldHandleEmptyJsonObject() throws Exception {
    // Arrange
    OqmMessage oqmMessage = mock(OqmMessage.class);
    when(oqmMessage.getData()).thenReturn("{}");

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.ok().build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    ArgumentCaptor<RecordReindexRequest> captor = ArgumentCaptor.forClass(RecordReindexRequest.class);
    verify(reindexApi).reindex(captor.capture(), eq(false));

    RecordReindexRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest).isNotNull();
  }

  @Test
  void sendMessage_shouldHandleAcceptedStatus() throws Exception {
    // Arrange
    RecordReindexRequest reindexRequest = createTestRecordReindexRequest();
    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    verify(reindexApi, times(1)).reindex(any(RecordReindexRequest.class), eq(false));
  }

  @Test
  void sendMessage_shouldHandleCreatedStatus() throws Exception {
    // Arrange
    RecordReindexRequest reindexRequest = createTestRecordReindexRequest();
    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    verify(reindexApi, times(1)).reindex(any(RecordReindexRequest.class), eq(false));
  }

  @Test
  void sendMessage_shouldHandleInternalServerError() throws Exception {
    // Arrange
    RecordReindexRequest reindexRequest = createTestRecordReindexRequest();
    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    verify(reindexApi, times(1)).reindex(any(RecordReindexRequest.class), eq(false));
  }

  @Test
  void sendMessage_shouldHandleMultipleCallsWithDifferentRequests() throws Exception {
    // Arrange
    RecordReindexRequest request1 = new RecordReindexRequest();
    request1.setCursor("cursor-1");
    request1.setKind("kind-1");

    RecordReindexRequest request2 = new RecordReindexRequest();
    request2.setCursor("cursor-2");
    request2.setKind("kind-2");

    OqmMessage message1 = createOqmMessageWithReindexRequest(request1);
    OqmMessage message2 = createOqmMessageWithReindexRequest(request2);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.ok().build());

    // Act
    reprocessorMessageReceiver.sendMessage(message1);
    reprocessorMessageReceiver.sendMessage(message2);

    // Assert
    verify(reindexApi, times(2)).reindex(any(RecordReindexRequest.class), eq(false));
  }

  @Test
  void sendMessage_shouldHandleNoContentResponse() throws Exception {
    // Arrange
    RecordReindexRequest reindexRequest = createTestRecordReindexRequest();
    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.status(HttpStatus.NO_CONTENT).build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    verify(reindexApi, times(1)).reindex(any(RecordReindexRequest.class), eq(false));
  }

  @Test
  void sendMessage_shouldHandleBadRequestResponse() throws Exception {
    // Arrange
    RecordReindexRequest reindexRequest = createTestRecordReindexRequest();
    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    verify(reindexApi, times(1)).reindex(any(RecordReindexRequest.class), eq(false));
  }

  @Test
  void sendMessage_shouldDeserializeRequestWithNullCursor() throws Exception {
    // Arrange
    RecordReindexRequest reindexRequest = new RecordReindexRequest();
    reindexRequest.setCursor(null);
    reindexRequest.setKind("osdu:wks:dataset:1.0.0");

    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.ok().build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    ArgumentCaptor<RecordReindexRequest> captor = ArgumentCaptor.forClass(RecordReindexRequest.class);
    verify(reindexApi).reindex(captor.capture(), eq(false));

    assertThat(captor.getValue().getCursor()).isNull();
    assertThat(captor.getValue().getKind()).isEqualTo("osdu:wks:dataset:1.0.0");
  }

  @Test
  void sendMessage_shouldDeserializeRequestWithNullKind() throws Exception {
    // Arrange
    RecordReindexRequest reindexRequest = new RecordReindexRequest();
    reindexRequest.setCursor("test-cursor");
    reindexRequest.setKind(null);

    OqmMessage oqmMessage = createOqmMessageWithReindexRequest(reindexRequest);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.ok().build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    ArgumentCaptor<RecordReindexRequest> captor = ArgumentCaptor.forClass(RecordReindexRequest.class);
    verify(reindexApi).reindex(captor.capture(), eq(false));

    assertThat(captor.getValue().getCursor()).isEqualTo("test-cursor");
    assertThat(captor.getValue().getKind()).isNull();
  }

  @Test
  void sendMessage_shouldHandleJsonWithExtraFields() throws Exception {
    // Arrange
    String jsonWithExtraFields = "{\"cursor\":\"test-cursor\",\"kind\":\"test-kind\",\"extraField\":\"extraValue\"}";

    OqmMessage oqmMessage = mock(OqmMessage.class);
    when(oqmMessage.getData()).thenReturn(jsonWithExtraFields);

    when(reindexApi.reindex(any(RecordReindexRequest.class), eq(false)))
            .thenReturn(ResponseEntity.ok().build());

    // Act
    reprocessorMessageReceiver.sendMessage(oqmMessage);

    // Assert
    ArgumentCaptor<RecordReindexRequest> captor = ArgumentCaptor.forClass(RecordReindexRequest.class);
    verify(reindexApi).reindex(captor.capture(), eq(false));

    assertThat(captor.getValue().getCursor()).isEqualTo("test-cursor");
    assertThat(captor.getValue().getKind()).isEqualTo("test-kind");
  }

  // Helper methods
  private RecordReindexRequest createTestRecordReindexRequest() {
    RecordReindexRequest request = new RecordReindexRequest();
    request.setCursor("test-cursor");
    request.setKind("osdu:wks:dataset:1.0.0");
    return request;
  }

  private OqmMessage createOqmMessageWithReindexRequest(RecordReindexRequest reindexRequest) {
    OqmMessage oqmMessage = mock(OqmMessage.class);
    String jsonData = gson.toJson(reindexRequest);
    when(oqmMessage.getData()).thenReturn(jsonData);
    return oqmMessage;
  }
}
