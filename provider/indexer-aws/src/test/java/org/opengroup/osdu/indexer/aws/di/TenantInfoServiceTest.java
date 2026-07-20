/**
* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*      http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.opengroup.osdu.indexer.aws.di;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RunWith(MockitoJUnitRunner.class)
public class TenantInfoServiceTest {

    @Mock
    private ITenantFactory tenantFactory;

    @Mock
    private DpsHeaders headers;

    @InjectMocks
    TenantInfoService service;

    @Test
    public void shouldReturnSetValue_when_getTenantInfo_isCalled() {
        TenantInfo expectedTenantInfo = new TenantInfo();
        Mockito.when(headers.getPartitionId()).thenReturn("opendes");
        Mockito.when(tenantFactory.getTenantInfo("opendes")).thenReturn(expectedTenantInfo);
        TenantInfo tenantInfo = service.getTenantInfo();
        assertEquals(expectedTenantInfo, tenantInfo);
    }

    @Test
    public void shouldReturnSetList_when_getAllTenantInfos_isCalled() {
        List<TenantInfo> expectedTenantInfoArrayList = new ArrayList<>();
        Mockito.when(tenantFactory.listTenantInfo()).thenReturn(expectedTenantInfoArrayList);
        List<TenantInfo> tenantInfoArrayList = service.getAllTenantInfos();
        assertEquals(expectedTenantInfoArrayList, tenantInfoArrayList);
    }
}
