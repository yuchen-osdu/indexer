/*
 * Copyright 2017-2025, Google
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

package org.opengroup.osdu.util;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.client.util.Clock;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.Data;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

class JwtTokenUtil {

    private final static Predicate<String> contentAcceptanceTester = s -> s.trim().startsWith("{");
    private static String accessToken;

    static String getAccessToken() throws IOException {
        if (Strings.isNullOrEmpty(accessToken)) {
            accessToken = getServiceAccountAccessToken(getJwtForIntegrationTesterAccount());
        }
        return accessToken;
    }

    private static String getServiceAccountAccessToken(String key) throws IOException {

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {

            List<NameValuePair> parameters = new ArrayList<>();
            parameters.add(new BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer"));
            parameters.add(new BasicNameValuePair("assertion", key));

            HttpPost postRequest = new HttpPost("https://www.googleapis.com/oauth2/v4/token");
            postRequest.addHeader("Content-Type", "application/x-www-form-urlencoded");
            postRequest.setEntity(new UrlEncodedFormEntity(parameters));

            HttpResponse response = httpClient.execute(postRequest);
            String responseContent = IOUtils.toString(response.getEntity().getContent(), Charsets.toCharset("UTF-8"));

            JwtTokenUtil.ResponseToken responseToken = new Gson().fromJson(responseContent, JwtTokenUtil.ResponseToken.class);

            return responseToken.getId_token();
        }
    }

    private static String getJwtForIntegrationTesterAccount() throws IOException {
        String serviceAccountValue = new DecodedContentExtractor(Config.getKeyValue(), contentAcceptanceTester).getContent();
        return getJwt(serviceAccountValue);
    }

    private static String getJwt(String serviceAccountFile) throws IOException {
        long currentTime = Clock.SYSTEM.currentTimeMillis();

        InputStream stream = new ByteArrayInputStream(serviceAccountFile.getBytes());
        GoogleCredential credential = GoogleCredential.fromStream(stream);

        JsonWebSignature.Header header = new JsonWebSignature.Header();
        header.setAlgorithm("RS256");
        header.setType("JWT");
        header.setKeyId(credential.getServiceAccountPrivateKeyId());

        JsonWebSignature.Payload payload = new JsonWebToken.Payload();
        payload.setIssuedAtTimeSeconds(currentTime / 1000);
        payload.setExpirationTimeSeconds(currentTime / 1000 + 3600);
        payload.setAudience("https://www.googleapis.com/oauth2/v4/token");
        payload.setIssuer(credential.getServiceAccountId());
        payload.set("target_audience", "osdu");

        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        String signedJwt = null;
        try {
            signedJwt = JsonWebSignature.signUsingRsaSha256(credential.getServiceAccountPrivateKey(), jsonFactory, header, payload);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }

        return signedJwt;
    }

    @Data
    class ResponseToken {
        public String id_token;
    }
}
