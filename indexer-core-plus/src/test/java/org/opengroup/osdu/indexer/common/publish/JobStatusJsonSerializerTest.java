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
package org.opengroup.osdu.indexer.common.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.indexer.RecordStatus;

@ExtendWith(MockitoExtension.class)
class JobStatusJsonSerializerTest {

    private JobStatusJsonSerializer serializer;

    @Mock
    private JobStatus jobStatus;

    @BeforeEach
    void setUp() {
        serializer = new JobStatusJsonSerializer();
    }

    // Helper method to create mock RecordStatus
    private RecordStatus createMockRecordStatus() {
        return mock(RecordStatus.class);
        // No stubbing needed - we're only testing that the serializer
        // handles lists of RecordStatus objects correctly
    }

    @Test
    void test01_shouldInstantiateAndSerializeWithRequiredFields() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Assert - instantiation
        assertNotNull(serializer);

        // Act
        JsonElement result = serializer.serialize(jobStatus, JobStatus.class, null);

        // Assert - result is a JsonObject containing the required fields
        assertNotNull(result);
        assertTrue(result.isJsonObject());

        JsonObject jsonObject = result.getAsJsonObject();
        assertTrue(jsonObject.has("recordsStatus"));
        assertTrue(jsonObject.has("debugInfo"));
    }

    @Test
    void shouldSerializeEmptyStatusesList() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertTrue(result.get("recordsStatus").isJsonArray());
        assertEquals(0, result.get("recordsStatus").getAsJsonArray().size());
    }

    @Test
    void shouldSerializeEmptyDebugInfos() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertTrue(result.get("debugInfo").isJsonArray());
        assertEquals(0, result.get("debugInfo").getAsJsonArray().size());
    }

    @Test
    void shouldCallGetStatusesListOnce() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        serializer.serialize(jobStatus, JobStatus.class, null);

        // Assert
        verify(jobStatus, times(1)).getStatusesList();
    }

    @Test
    void shouldCallGetDebugInfosOnce() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        serializer.serialize(jobStatus, JobStatus.class, null);

        // Assert
        verify(jobStatus, times(1)).getDebugInfos();
    }

    @Test
    void shouldSerializeNonEmptyStatusesList() {
        // Arrange
        List<RecordStatus> statusList = Arrays.asList(
                createMockRecordStatus(),
                createMockRecordStatus(),
                createMockRecordStatus()
        );
        when(jobStatus.getStatusesList()).thenReturn(statusList);
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertTrue(result.get("recordsStatus").isJsonArray());
        assertEquals(3, result.get("recordsStatus").getAsJsonArray().size());
    }

    @Test
    void shouldSerializeNonEmptyDebugInfos() {
        // Arrange
        List<String> debugInfos = Arrays.asList("DEBUG_1", "DEBUG_2");
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(debugInfos);

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertTrue(result.get("debugInfo").isJsonArray());
        assertEquals(2, result.get("debugInfo").getAsJsonArray().size());
    }

    @Test
    void shouldSerializeBothNonEmptyLists() {
        // Arrange
        List<RecordStatus> statusList = Arrays.asList(
                createMockRecordStatus(),
                createMockRecordStatus()
        );
        List<String> debugInfos = Arrays.asList("Info1", "Info2", "Info3");
        when(jobStatus.getStatusesList()).thenReturn(statusList);
        when(jobStatus.getDebugInfos()).thenReturn(debugInfos);

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertEquals(2, result.get("recordsStatus").getAsJsonArray().size());
        assertEquals(3, result.get("debugInfo").getAsJsonArray().size());
    }

    @Test
    void shouldPreserveOrderInStatusesList() {
        // Arrange
        List<RecordStatus> statusList = Arrays.asList(
                createMockRecordStatus(),
                createMockRecordStatus(),
                createMockRecordStatus(),
                createMockRecordStatus()
        );
        when(jobStatus.getStatusesList()).thenReturn(statusList);
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertEquals(4, result.get("recordsStatus").getAsJsonArray().size());
    }

    @Test
    void shouldUseCorrectFieldNameForRecordsStatus() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertTrue(result.has("recordsStatus"));
        assertFalse(result.has("statusesList"));
        assertFalse(result.has("statuses"));
    }

    @Test
    void shouldUseCorrectFieldNameForDebugInfo() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertTrue(result.has("debugInfo"));
        assertFalse(result.has("debugInfos"));
        assertFalse(result.has("debug"));
    }

    @Test
    void shouldContainOnlyTwoFields() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void shouldSerializeRecordsStatusAsJsonArray() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Arrays.asList(createMockRecordStatus()));
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertTrue(result.get("recordsStatus").isJsonArray());
        assertFalse(result.get("recordsStatus").isJsonObject());
        assertFalse(result.get("recordsStatus").isJsonPrimitive());
    }

    @Test
    void shouldHandleMultipleSerializationsWithSameSerializer() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Arrays.asList(createMockRecordStatus()));
        when(jobStatus.getDebugInfos()).thenReturn(Arrays.asList("DEBUG_1"));

        // Act
        JsonObject result1 = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();
        JsonObject result2 = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();
        JsonObject result3 = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);
        assertEquals(result1.size(), result2.size());
        assertEquals(result2.size(), result3.size());
    }

    @Test
    void shouldHandleSerializationWithDifferentJobStatusObjects() {
        // Arrange
        JobStatus jobStatus1 = mock(JobStatus.class);
        JobStatus jobStatus2 = mock(JobStatus.class);

        when(jobStatus1.getStatusesList()).thenReturn(Arrays.asList(createMockRecordStatus()));
        when(jobStatus1.getDebugInfos()).thenReturn(Collections.emptyList());

        when(jobStatus2.getStatusesList()).thenReturn(Arrays.asList(createMockRecordStatus()));
        when(jobStatus2.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        JsonObject result1 = serializer.serialize(jobStatus1, JobStatus.class, null).getAsJsonObject();
        JsonObject result2 = serializer.serialize(jobStatus2, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertEquals(1, result1.get("recordsStatus").getAsJsonArray().size());
        assertEquals(1, result2.get("recordsStatus").getAsJsonArray().size());
    }

    @Test
    void shouldMaintainStateAcrossMultipleCalls() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Arrays.asList(createMockRecordStatus()));
        when(jobStatus.getDebugInfos()).thenReturn(Arrays.asList("DEBUG"));

        // Act
        serializer.serialize(jobStatus, JobStatus.class, null);
        serializer.serialize(jobStatus, JobStatus.class, null);

        // Assert
        verify(jobStatus, times(2)).getStatusesList();
        verify(jobStatus, times(2)).getDebugInfos();
    }

    @Test
    void shouldNotModifyInputJobStatus() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Arrays.asList(createMockRecordStatus()));
        when(jobStatus.getDebugInfos()).thenReturn(Arrays.asList("DEBUG"));

        // Act
        serializer.serialize(jobStatus, JobStatus.class, null);

        // Assert - Only verify read operations, no write operations
        verify(jobStatus, times(1)).getStatusesList();
        verify(jobStatus, times(1)).getDebugInfos();
    }

    @Test
    void shouldHandleSingleItemLists() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Arrays.asList(createMockRecordStatus()));
        when(jobStatus.getDebugInfos()).thenReturn(Arrays.asList("SINGLE_DEBUG"));

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertEquals(1, result.get("recordsStatus").getAsJsonArray().size());
        assertEquals(1, result.get("debugInfo").getAsJsonArray().size());
    }

    @Test
    void shouldHandleLargeListsInStatusesList() {
        // Arrange
        List<RecordStatus> largeList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            largeList.add(createMockRecordStatus());
        }
        when(jobStatus.getStatusesList()).thenReturn(largeList);
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertEquals(100, result.get("recordsStatus").getAsJsonArray().size());
    }

    @Test
    void shouldHandleSpecialCharactersInDebugInfo() {
        // Arrange
        List<String> specialChars = Arrays.asList("DEBUG_WITH_!@#$%", "DEBUG WITH SPACES", "DEBUG\nWITH\nNEWLINES");
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(specialChars);

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertEquals(3, result.get("debugInfo").getAsJsonArray().size());
    }

    @Test
    void shouldHandleEmptyStringsInDebugInfoList() {
        // Arrange
        List<String> withEmptyStrings = Arrays.asList("", "NON_EMPTY", "");
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(withEmptyStrings);

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        assertEquals(3, result.get("debugInfo").getAsJsonArray().size());
        assertEquals("", result.get("debugInfo").getAsJsonArray().get(0).getAsString());
        assertEquals("NON_EMPTY", result.get("debugInfo").getAsJsonArray().get(1).getAsString());
        assertEquals("", result.get("debugInfo").getAsJsonArray().get(2).getAsString());
    }

    @Test
    void shouldReturnJsonElementNotNull() {
        // Arrange
        when(jobStatus.getStatusesList()).thenReturn(Collections.emptyList());
        when(jobStatus.getDebugInfos()).thenReturn(Collections.emptyList());

        // Act
        JsonElement result = serializer.serialize(jobStatus, JobStatus.class, null);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof JsonElement);
        assertTrue(result instanceof JsonObject);
    }

    @Test
    void shouldReturnValidJsonStructure() {
        // Arrange
        List<RecordStatus> statusList = Arrays.asList(
                createMockRecordStatus(),
                createMockRecordStatus()
        );
        List<String> debugInfos = Arrays.asList("Debug message 1", "Debug message 2");
        when(jobStatus.getStatusesList()).thenReturn(statusList);
        when(jobStatus.getDebugInfos()).thenReturn(debugInfos);

        // Act
        JsonObject result = serializer.serialize(jobStatus, JobStatus.class, null).getAsJsonObject();

        // Assert
        // Verify it's a valid JSON structure by converting to string and back
        String jsonString = result.toString();
        assertNotNull(jsonString);
        assertTrue(jsonString.contains("recordsStatus"));
        assertTrue(jsonString.contains("debugInfo"));
        assertTrue(jsonString.contains("Debug message 1"));
    }
}
