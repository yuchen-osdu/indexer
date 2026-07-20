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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.v2.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.v2.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.http.HttpClient;
import org.opengroup.osdu.core.common.http.HttpRequest;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.http.IHttpClient;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class AwsServiceAccountAuthToken {
    private String clientCredentialsSecret;
    private String clientCredentialsClientId;
    private String tokenUrl;
    private String oauthCustomScope;
    private String token= null;
    private long expirationTimeMillis;
    private static final Logger LOGGER = LoggerFactory.getLogger(AwsServiceAccountAuthToken.class);

    @PostConstruct
    private void init() {
        K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
        try {
            this.clientCredentialsClientId = provider.getParameterAsString("CLIENT_CREDENTIALS_ID");
            this.clientCredentialsSecret = provider.getCredentialsAsMap("CLIENT_CREDENTIALS_SECRET").get("client_credentials_client_secret");
            this.tokenUrl = provider.getParameterAsString("OAUTH_TOKEN_URI");
            this.oauthCustomScope = provider.getParameterAsString("OAUTH_CUSTOM_SCOPE");
        } catch (K8sParameterNotFoundException | JsonProcessingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "ParameterNotFoundException", e.getMessage());
        }
      }

    public String getAuthToken() throws AppException {
        if (expiredToken()){
            Map<String,String> headers = new HashMap<>();
            String authorizationHeaderContents=getEncodedAuthorization(this.clientCredentialsClientId, this.clientCredentialsSecret);
            headers.put("Authorization","Basic "+ authorizationHeaderContents);
            headers.put("Content-Type", "application/x-www-form-urlencoded");
    
            IHttpClient httpClient = new HttpClient();
            HttpRequest rq = HttpRequest.post().url(this.tokenUrl).headers(headers).body(
                    String.format("%s=%s&%s=%s", "grant_type", "client_credentials", "scope", this.oauthCustomScope)).build();
            HttpResponse result = httpClient.send(rq);
            try {
                AccessToken accessToken = this.getResult(result, AccessToken.class);
                this.token = accessToken.getAccess_token();
                int duration = Integer.parseInt(accessToken.getExpires_in());
                this.expirationTimeMillis = System.currentTimeMillis()+duration*1000;
            }catch(Exception e) {
                LOGGER.error("Could not parse AccessToken result");
            }
        }
        return this.token; 
    }
    
    private boolean expiredToken() {
        if(this.token == null)
            return true;
        // get a new token if token has 2 minutes to expire
        long diff = this.expirationTimeMillis - System.currentTimeMillis();
        long diffMinutes = (diff / 1000) / 60;
            return diffMinutes <= 2;
    }

    private String getEncodedAuthorization(String clientID, String clientSecret)
    {
        return Base64.getEncoder().encodeToString((clientID+":"+ clientSecret).getBytes());
    }

    private <T> T getResult(HttpResponse result, Class<T> type) throws AppException {
        Gson gson = new Gson();
        if (result.isSuccessCode()) {
            try {
                return gson.fromJson(result.getBody(), type);
            } catch (JsonSyntaxException e) {
                throw new IllegalArgumentException("array parsing error in getResult of HttpResonse, not a valid array");
            }
        } else {
            throw new AppException(result.getResponseCode(), "Invalid Response", result.getBody());
        }
    }
}
