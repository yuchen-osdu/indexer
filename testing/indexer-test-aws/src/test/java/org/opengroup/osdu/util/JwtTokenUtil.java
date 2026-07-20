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

package org.opengroup.osdu.util;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClientBuilder;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;

import java.util.Map;

/**
 * Obtains JWT access tokens from AWS Cognito using the AWS SDK v2 directly.
 * Replaces the previous dependency on os-core-lib-aws's AWSCognitoClient to
 * avoid pulling in Spring Boot 3 / Spring 6 transitive dependencies.
 */
class JwtTokenUtil {
    static String getAccessToken() {
        String clientId = Config.getAWSCognitoClientId();
        String authFlow = Config.getAWSCognitoAuthFlow();
        String user = Config.getAWSCognitoUser();
        String password = Config.getAWSCognitoPassword();

        CognitoIdentityProviderClientBuilder builder = CognitoIdentityProviderClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create());

        String regionStr = System.getenv("AWS_COGNITO_REGION");
        if (regionStr != null) {
            builder.region(Region.of(regionStr));
        }

        try (CognitoIdentityProviderClient provider = builder.build()) {
            InitiateAuthResponse response = provider.initiateAuth(InitiateAuthRequest.builder()
                    .clientId(clientId)
                    .authFlow(AuthFlowType.fromValue(authFlow))
                    .authParameters(Map.of("USERNAME", user, "PASSWORD", password))
                    .build());
            return response.authenticationResult().accessToken();
        }
    }
}