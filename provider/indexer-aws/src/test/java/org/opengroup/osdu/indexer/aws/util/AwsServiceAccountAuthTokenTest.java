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

import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.http.HttpClient;
import org.opengroup.osdu.core.common.http.HttpRequest;
import org.opengroup.osdu.core.common.http.HttpResponse;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.PrintStream;


public class AwsServiceAccountAuthTokenTest {
    
    private AwsServiceAccountAuthToken authorizer;

    private String body_300expire = "{ \"expires_in\" : \"300\", \"access_token\" : \"abcd\", \"data\" : \"data\", \"attributes\" : { \"attribute\" : \"attribute\" } }";
    private String body_0expire = "{ \"expires_in\" : \"0\", \"access_token\" : \"abcd\", \"data\" : \"data\", \"attributes\" : { \"attribute\" : \"attribute\" } }";
    private String body_invalid = "{ \"expires_in\" :  }";

    @Test
    public void getAuthTokenTest_null_token_no_code() throws Exception  {

        try (MockedConstruction<HttpClient> httpClient = Mockito.mockConstruction(HttpClient.class, (mock, context) -> {
                                                                                                                when(mock.send(any(HttpRequest.class))).thenReturn(new HttpResponse());
                                                                                                            })) {
            
            this.authorizer = new AwsServiceAccountAuthToken();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            System.setOut(new PrintStream(stream));
            assertNull(this.authorizer.getAuthToken());
            String allWrittenLines = new String(stream.toByteArray());
            stream.flush();
            assertTrue(allWrittenLines.contains("Could not parse AccessToken result"));
            stream.close();
        }

    }


    @Test
    public void getAuthTokenTest_succeed_code() throws Exception  {

        HttpResponse response = new HttpResponse(null, body_300expire, "contentType", 200, null, null, 0);

        try (MockedConstruction<HttpClient> httpClient = Mockito.mockConstruction(HttpClient.class, (mock, context) -> {
                                                                                                                when(mock.send(any(HttpRequest.class))).thenReturn(response);
                                                                                                            })) {
            
            this.authorizer = new AwsServiceAccountAuthToken();
            assertEquals("abcd", this.authorizer.getAuthToken());
        }

    }

    @Test
    public void getAuthTokenTest_no_succeed_code() throws Exception  {

        HttpResponse response = new HttpResponse(null, body_300expire, "contentType", 400, null, null, 0);

        try (MockedConstruction<HttpClient> httpClient = Mockito.mockConstruction(HttpClient.class, (mock, context) -> {
                                                                                                                when(mock.send(any(HttpRequest.class))).thenReturn(response);
                                                                                                            })) {
            
            this.authorizer = new AwsServiceAccountAuthToken();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            System.setOut(new PrintStream(stream));
            assertNull(this.authorizer.getAuthToken());
            String allWrittenLines = new String(stream.toByteArray());
            stream.flush();
            assertTrue(allWrittenLines.contains("Could not parse AccessToken result"));
            stream.close();
        }

    }

    @Test
    public void getAuthTokenTest_token_not_expire() throws Exception  {

        HttpResponse response = new HttpResponse(null, body_300expire, "contentType", 200, null, null, 0);

        try (MockedConstruction<HttpClient> httpClient = Mockito.mockConstruction(HttpClient.class, (mock, context) -> {
                                                                                                                when(mock.send(any(HttpRequest.class))).thenReturn(response);
                                                                                                            })) {
            
            this.authorizer = new AwsServiceAccountAuthToken();
            String firstToken = this.authorizer.getAuthToken();
            assertEquals("abcd", this.authorizer.getAuthToken());
            assertSame(firstToken, this.authorizer.getAuthToken());

        }

    }

    @Test
    public void getAuthTokenTest_token_expire() throws Exception  {

        HttpResponse response = new HttpResponse(null, body_0expire, "contentType", 200, null, null, 0);

        try (MockedConstruction<HttpClient> httpClient = Mockito.mockConstruction(HttpClient.class, (mock, context) -> {
                                                                                                                when(mock.send(any(HttpRequest.class))).thenReturn(response);
                                                                                                            })) {
            
            this.authorizer = new AwsServiceAccountAuthToken();
            String firstToken = this.authorizer.getAuthToken();
            assertEquals("abcd", this.authorizer.getAuthToken());
            assertNotSame(firstToken, this.authorizer.getAuthToken());

        }

    }

    @Test
    public void getAuthTokenTest_invalid_body() throws Exception  {

        HttpResponse response = new HttpResponse(null, body_invalid, "contentType", 200, null, null, 0);

        try (MockedConstruction<HttpClient> httpClient = Mockito.mockConstruction(HttpClient.class, (mock, context) -> {
                                                                                                                when(mock.send(any(HttpRequest.class))).thenReturn(response);
                                                                                                            })) {

            this.authorizer = new AwsServiceAccountAuthToken();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            System.setOut(new PrintStream(stream));
            assertNull(this.authorizer.getAuthToken());
            String allWrittenLines = new String(stream.toByteArray());
            stream.flush();
            assertTrue(allWrittenLines.contains("Could not parse AccessToken result"));
            stream.close();

        }

    }

}
