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
package org.opengroup.osdu.indexer.web.di;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;

@ExtendWith(MockitoExtension.class)
class TenantInfoServiceTest {

    @Mock
    private ITenantFactory tenantFactory;

    @Mock
    private DpsHeaders headers;

    @InjectMocks
    private TenantInfoService tenantInfoService;

    private TenantInfo mockTenantInfo;
    private static final String TEST_PARTITION_ID = "test-partition-123";

    @BeforeEach
    void setUp() {
        mockTenantInfo = mock(TenantInfo.class);
    }

    // ==================== Test getTenantInfo() ====================

    @Test
    void getTenantInfo_shouldReturnTenantInfo() {
        // Arrange
        when(headers.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(tenantFactory.getTenantInfo(TEST_PARTITION_ID)).thenReturn(mockTenantInfo);

        // Act
        TenantInfo result = tenantInfoService.getTenantInfo();

        // Assert
        assertNotNull(result);
        assertEquals(mockTenantInfo, result);
        verify(headers).getPartitionId();
        verify(tenantFactory).getTenantInfo(TEST_PARTITION_ID);
    }

    @Test
    void getTenantInfo_shouldCallTenantFactoryWithCorrectPartitionId() {
        // Arrange
        String customPartitionId = "custom-partition-456";
        when(headers.getPartitionId()).thenReturn(customPartitionId);
        when(tenantFactory.getTenantInfo(customPartitionId)).thenReturn(mockTenantInfo);

        // Act
        TenantInfo result = tenantInfoService.getTenantInfo();

        // Assert
        assertNotNull(result);
        verify(tenantFactory).getTenantInfo(customPartitionId);
    }

    @Test
    void getTenantInfo_shouldHandleNullPartitionId() {
        // Arrange
        when(headers.getPartitionId()).thenReturn(null);
        when(tenantFactory.getTenantInfo(null)).thenReturn(mockTenantInfo);

        // Act
        TenantInfo result = tenantInfoService.getTenantInfo();

        // Assert
        assertNotNull(result);
        verify(headers).getPartitionId();
        verify(tenantFactory).getTenantInfo(null);
    }

    @Test
    void getTenantInfo_shouldHandleEmptyPartitionId() {
        // Arrange
        when(headers.getPartitionId()).thenReturn("");
        when(tenantFactory.getTenantInfo("")).thenReturn(mockTenantInfo);

        // Act
        TenantInfo result = tenantInfoService.getTenantInfo();

        // Assert
        assertNotNull(result);
        verify(tenantFactory).getTenantInfo("");
    }

    @Test
    void getTenantInfo_shouldReturnNullWhenTenantNotFound() {
        // Arrange
        when(headers.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(tenantFactory.getTenantInfo(TEST_PARTITION_ID)).thenReturn(null);

        // Act
        TenantInfo result = tenantInfoService.getTenantInfo();

        // Assert
        assertNull(result);
        verify(tenantFactory).getTenantInfo(TEST_PARTITION_ID);
    }

    @Test
    void getTenantInfo_shouldCallMethodsInCorrectOrder() {
        // Arrange
        when(headers.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(tenantFactory.getTenantInfo(TEST_PARTITION_ID)).thenReturn(mockTenantInfo);

        // Act
        tenantInfoService.getTenantInfo();

        // Assert - Verify order of invocations
        var inOrder = inOrder(headers, tenantFactory);
        inOrder.verify(headers).getPartitionId();
        inOrder.verify(tenantFactory).getTenantInfo(TEST_PARTITION_ID);
    }

    @Test
    void getTenantInfo_shouldHandleMultipleCalls() {
        // Arrange
        when(headers.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(tenantFactory.getTenantInfo(TEST_PARTITION_ID)).thenReturn(mockTenantInfo);

        // Act
        TenantInfo result1 = tenantInfoService.getTenantInfo();
        TenantInfo result2 = tenantInfoService.getTenantInfo();

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1, result2);
        verify(headers, times(2)).getPartitionId();
        verify(tenantFactory, times(2)).getTenantInfo(TEST_PARTITION_ID);
    }

    // ==================== Test getAllTenantInfos() ====================

    @Test
    void getAllTenantInfos_shouldReturnListOfTenantInfos() {
        // Arrange
        TenantInfo tenant1 = mock(TenantInfo.class);
        TenantInfo tenant2 = mock(TenantInfo.class);
        TenantInfo tenant3 = mock(TenantInfo.class);
        Collection<TenantInfo> tenantCollection = Arrays.asList(tenant1, tenant2, tenant3);

        when(tenantFactory.listTenantInfo()).thenReturn(tenantCollection);

        // Act
        List<TenantInfo> result = tenantInfoService.getAllTenantInfos();

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(tenant1));
        assertTrue(result.contains(tenant2));
        assertTrue(result.contains(tenant3));
        verify(tenantFactory).listTenantInfo();
    }

    @Test
    void getAllTenantInfos_shouldReturnEmptyListWhenNoTenants() {
        // Arrange
        Collection<TenantInfo> emptyCollection = Collections.emptyList();
        when(tenantFactory.listTenantInfo()).thenReturn(emptyCollection);

        // Act
        List<TenantInfo> result = tenantInfoService.getAllTenantInfos();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        verify(tenantFactory).listTenantInfo();
    }

    @Test
    void getAllTenantInfos_shouldReturnNewArrayList() {
        // Arrange
        TenantInfo tenant1 = mock(TenantInfo.class);
        Collection<TenantInfo> tenantCollection = Collections.singletonList(tenant1);
        when(tenantFactory.listTenantInfo()).thenReturn(tenantCollection);

        // Act
        List<TenantInfo> result = tenantInfoService.getAllTenantInfos();

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof ArrayList);
        assertEquals(1, result.size());
    }

    @Test
    void getAllTenantInfos_shouldHandleSingleTenant() {
        // Arrange
        TenantInfo singleTenant = mock(TenantInfo.class);
        Collection<TenantInfo> tenantCollection = Collections.singletonList(singleTenant);
        when(tenantFactory.listTenantInfo()).thenReturn(tenantCollection);

        // Act
        List<TenantInfo> result = tenantInfoService.getAllTenantInfos();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(singleTenant, result.get(0));
    }

    @Test
    void getAllTenantInfos_shouldHandleMultipleCalls() {
        // Arrange
        TenantInfo tenant1 = mock(TenantInfo.class);
        TenantInfo tenant2 = mock(TenantInfo.class);
        Collection<TenantInfo> tenantCollection = Arrays.asList(tenant1, tenant2);
        when(tenantFactory.listTenantInfo()).thenReturn(tenantCollection);

        // Act
        List<TenantInfo> result1 = tenantInfoService.getAllTenantInfos();
        List<TenantInfo> result2 = tenantInfoService.getAllTenantInfos();

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1.size(), result2.size());
        verify(tenantFactory, times(2)).listTenantInfo();
    }

    @Test
    void getAllTenantInfos_shouldReturnIndependentLists() {
        // Arrange
        TenantInfo tenant1 = mock(TenantInfo.class);
        Collection<TenantInfo> tenantCollection = new ArrayList<>(Collections.singletonList(tenant1));
        when(tenantFactory.listTenantInfo()).thenReturn(tenantCollection);

        // Act
        List<TenantInfo> result1 = tenantInfoService.getAllTenantInfos();
        List<TenantInfo> result2 = tenantInfoService.getAllTenantInfos();

        // Modify first list
        result1.clear();

        // Assert - Second list should not be affected
        assertNotEquals(result1.size(), result2.size());
        assertEquals(0, result1.size());
        assertEquals(1, result2.size());
    }

    @Test
    void getAllTenantInfos_shouldHandleLargeCollection() {
        // Arrange
        List<TenantInfo> largeTenantList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            largeTenantList.add(mock(TenantInfo.class));
        }
        when(tenantFactory.listTenantInfo()).thenReturn(largeTenantList);

        // Act
        List<TenantInfo> result = tenantInfoService.getAllTenantInfos();

        // Assert
        assertNotNull(result);
        assertEquals(100, result.size());
        verify(tenantFactory).listTenantInfo();
    }

    // ==================== Integration and Edge Cases ====================

    @Test
    void shouldHandleBothMethodCallsIndependently() {
        // Arrange
        TenantInfo specificTenant = mock(TenantInfo.class);
        TenantInfo tenant1 = mock(TenantInfo.class);
        TenantInfo tenant2 = mock(TenantInfo.class);
        Collection<TenantInfo> allTenants = Arrays.asList(tenant1, tenant2);

        when(headers.getPartitionId()).thenReturn(TEST_PARTITION_ID);
        when(tenantFactory.getTenantInfo(TEST_PARTITION_ID)).thenReturn(specificTenant);
        when(tenantFactory.listTenantInfo()).thenReturn(allTenants);

        // Act
        TenantInfo specificResult = tenantInfoService.getTenantInfo();
        List<TenantInfo> allResults = tenantInfoService.getAllTenantInfos();

        // Assert
        assertNotNull(specificResult);
        assertNotNull(allResults);
        assertEquals(specificTenant, specificResult);
        assertEquals(2, allResults.size());
        verify(tenantFactory).getTenantInfo(TEST_PARTITION_ID);
        verify(tenantFactory).listTenantInfo();
    }

    @Test
    void getTenantInfo_shouldHandleSpecialCharactersInPartitionId() {
        // Arrange
        String specialPartitionId = "test-partition-!@#$%^&*()";
        when(headers.getPartitionId()).thenReturn(specialPartitionId);
        when(tenantFactory.getTenantInfo(specialPartitionId)).thenReturn(mockTenantInfo);

        // Act
        TenantInfo result = tenantInfoService.getTenantInfo();

        // Assert
        assertNotNull(result);
        verify(tenantFactory).getTenantInfo(specialPartitionId);
    }

    @Test
    void getTenantInfo_shouldHandleVeryLongPartitionId() {
        // Arrange
        String longPartitionId = "a".repeat(1000);
        when(headers.getPartitionId()).thenReturn(longPartitionId);
        when(tenantFactory.getTenantInfo(longPartitionId)).thenReturn(mockTenantInfo);

        // Act
        TenantInfo result = tenantInfoService.getTenantInfo();

        // Assert
        assertNotNull(result);
        verify(tenantFactory).getTenantInfo(longPartitionId);
    }

    @Test
    void getAllTenantInfos_shouldPreserveOrderFromFactory() {
        // Arrange
        TenantInfo tenant1 = mock(TenantInfo.class);
        TenantInfo tenant2 = mock(TenantInfo.class);
        TenantInfo tenant3 = mock(TenantInfo.class);
        List<TenantInfo> orderedTenants = Arrays.asList(tenant1, tenant2, tenant3);
        when(tenantFactory.listTenantInfo()).thenReturn(orderedTenants);

        // Act
        List<TenantInfo> result = tenantInfoService.getAllTenantInfos();

        // Assert
        assertEquals(tenant1, result.get(0));
        assertEquals(tenant2, result.get(1));
        assertEquals(tenant3, result.get(2));
        assertNotNull(tenantInfoService);
    }

    @Test
    void getAllTenantInfos_shouldHandleNullElementsInCollection() {
        // Arrange
        Collection<TenantInfo> tenantsWithNull = Arrays.asList(
                mock(TenantInfo.class),
                null,
                mock(TenantInfo.class)
        );
        when(tenantFactory.listTenantInfo()).thenReturn(tenantsWithNull);

        // Act
        List<TenantInfo> result = tenantInfoService.getAllTenantInfos();

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertNull(result.get(1));
    }
}
