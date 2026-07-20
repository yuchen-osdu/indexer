/*
 * Copyright 2017-2025, Microsoft
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

package org.opengroup.osdu.indexer.azure.util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static junit.framework.TestCase.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RequestInfoImplTest {

    private static String deploymentEnvironmentField = "DEPLOYMENT_ENVIRONMENT";
    private static String deploymentEnvironmentValue = "LOCAL";
    private static String deploymentEnvironmentValueCloud = "CLOUD";
    private static String tenant = "tenant1";
    private static String bearerToken = "Bearer bearerToken";
    private static String expectedToken = "Bearer bearerToken";
    private static String partitionId = "opendes";
    private static String owner = "owner";

    @Mock
    private DpsHeaders standardHeaders;

    @Mock
    private IServiceAccountJwtClient serviceAccountJwtClient;

    @Mock
    private TenantInfo tenantInfo;

    @InjectMocks
    public RequestInfoImpl sut;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(RequestInfoImplTest.this);
        ReflectionTestUtils.setField(sut, deploymentEnvironmentField, deploymentEnvironmentValue);

    }

    @Test
    public void shouldReturnStandardHeaders_when_getHeaders_isCalled() {
        DpsHeaders headers = sut.getHeaders();
        assertEquals(headers, standardHeaders);
    }

    @Test
    public void shouldReturnSamePartitionIdReturnedByStandardHeaders_when_getPartitionId_isCalled() {
        when(standardHeaders.getPartitionId()).thenReturn(partitionId);

        String expectedPartitionId = sut.getPartitionId();

        verify(standardHeaders,times(1)).getPartitionId();
        assertEquals(partitionId, expectedPartitionId);
    }

    @Test
    public void shouldReturnSameHeadersReturnedByStandardHeaders_when_getHeadersMap_isCalled() {
        Map<String, String> headers = new HashMap();
        when(standardHeaders.getHeaders()).thenReturn(headers);

        Map<String, String> headersMap = sut.getHeadersMap();

        verify(standardHeaders,times(1)).getHeaders();
        assertEquals(headers, headersMap);
    }

    @Test
    public void should_invoke_getAuthorizationMethod_when_getHeadersWithDwdAuthZ_isCalled()
    {
        when(standardHeaders.getAuthorization()).thenReturn(owner);

        DpsHeaders dpsHeaders=sut.getHeadersWithDwdAuthZ();

        verify(standardHeaders,times(1)).getAuthorization();
        assertEquals(standardHeaders,dpsHeaders);
    }

    @Test
    public void should_invoke_getAuthorizationMethod_when_getHeadersMapWithDwdAuthZ_isCalled()
    {
        when(standardHeaders.getAuthorization()).thenReturn(owner);

        Map<String,String> dpsHeadersMap=sut.getHeadersMapWithDwdAuthZ();

        verify(standardHeaders,times(1)).getAuthorization();
        verify(standardHeaders,times(1)).getHeaders();
        assertEquals(standardHeaders.getHeaders(),dpsHeadersMap);
    }

    @Test
    public void shouldReturnFalse_when_isCronRequest() {
        Boolean cronRequest = sut.isCronRequest();
        assertFalse(cronRequest);
    }

    @Test
    public void shouldReturnFalse_when_isTaskQueueRequest() {
        Boolean taskQueueRequest = sut.isTaskQueueRequest();
        assertFalse(taskQueueRequest);
    }

    @Test
    public void shouldInvoke_getAuthorization_andReturnAuthHeader_when_checkOrGetAuthorizationHeader_withAuth_isCalled()
    {
        when(standardHeaders.getAuthorization()).thenReturn(owner);

        String authHeader=sut.checkOrGetAuthorizationHeader();

        verify(standardHeaders,times(1)).getAuthorization();
        assertEquals(authHeader,owner);
    }

    @Test(expected = AppException.class)
    public void shouldThrowException_checkOrGetAuthorizationHeader_isCalled_with_nullAsAuthHeader()
    {
//        Authorization token cannot be empty, hence it is expected to throw an App exception.
        when(standardHeaders.getAuthorization()).thenReturn(null);
        String authHeader=sut.checkOrGetAuthorizationHeader();
    }

    @Test
    public void checkOrGetAuthorizationHeader_withCloud()
    {
        ReflectionTestUtils.setField(sut,deploymentEnvironmentField,deploymentEnvironmentValueCloud);
        when(tenantInfo.getName()).thenReturn(tenant);
        when(serviceAccountJwtClient.getIdToken(tenant)).thenReturn(bearerToken);

        String bearerToken=sut.checkOrGetAuthorizationHeader();

        verify(tenantInfo,times(1)).getName();
        verify(serviceAccountJwtClient,times(1)).getIdToken(tenant);
        assertEquals(bearerToken, expectedToken);
    }
}
