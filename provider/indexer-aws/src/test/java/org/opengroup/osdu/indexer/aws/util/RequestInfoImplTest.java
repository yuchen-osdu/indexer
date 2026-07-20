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

package org.opengroup.osdu.indexer.aws.util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.AppException;


import java.util.HashMap;
import java.util.Map;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.matchers.apachecommons.ReflectionEquals;
import org.mockito.runners.MockitoJUnitRunner;


import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.opengroup.osdu.core.common.model.http.DpsHeaders.AUTHORIZATION;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RequestInfoImplTest {

    @InjectMocks
    private RequestInfoImpl request_info;
    
    @Mock
    private DpsHeaders headersMap;

    @Mock
    private AwsServiceAccountAuthToken awsServiceAccountAuthToken;

    @Before
    public void setup(){
        headersMap = mock(DpsHeaders.class);
        awsServiceAccountAuthToken = mock(AwsServiceAccountAuthToken.class);
        MockitoAnnotations.initMocks(this);
    }

    @Test(expected = AppException.class)
    public void getHeaders_null_headersMap(){
        RequestInfoImpl request_info_nullheader = new RequestInfoImpl();

        request_info_nullheader.getHeaders();
    }

    @Test
    public void getHeaders_not_null_headersMap(){

        Map<String, String> map = new HashMap<String,String>();

        DpsHeaders expected = DpsHeaders.createFromMap(map);

        DpsHeaders headers = request_info.getHeaders();

        assertFalse(new ReflectionEquals(expected).matches(headers));
    }

    @Test
    public void getHeaders_use_service_principal(){
        DpsHeaders headers = request_info.getHeaders();

        assertEquals("Bearer null", headers.getAuthorization());
        verify(this.awsServiceAccountAuthToken, times(1)).getAuthToken();
    }

    @Test
    public void getPartitionId_test(){

        String result = request_info.getPartitionId();

        assertNull(result);
    }

    @Test
    public void getHeadersMapWithDwdAuthZ_test(){
        
        Map<String, String> expected = new HashMap<String, String>();
        expected.put("content-type","application/json");

        Map<String, String> result = request_info.getHeadersMapWithDwdAuthZ();

        assertTrue(new ReflectionEquals(expected).matches(result));
    }

    @Test
    public void isCronRequest_test(){
        assertFalse(request_info.isCronRequest());
    }

    @Test
    public void isTaskQueueRequest_test(){
        assertFalse(request_info.isTaskQueueRequest());
    }

    @Test
    public void getHeadersWithDwdAuthZ_test() {

        DpsHeaders result = request_info.getHeadersWithDwdAuthZ();
        
        assertTrue(result.getHeaders().containsKey(AUTHORIZATION));
    }
}
