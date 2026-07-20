/*
 * Copyright 2017-2025, The Open Group
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

import org.opengroup.osdu.models.schema.SchemaIdentity;
import org.opengroup.osdu.models.schema.SchemaModel;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Collections.singletonList;


public class SchemaServiceClient {

    private static final Logger LOGGER = Logger.getLogger(SchemaServiceClient.class.getName());

    private final RestTemplate template;
    private final String schemaBaseUrl;

    public SchemaServiceClient(HTTPClient client) {
        template = new RestTemplateBuilder()
                .errorHandler(new NotFoundIgnoringResponseErrorHandler())
                .additionalInterceptors((request, body, execution) -> {
                    request.getHeaders().add(HttpHeaders.AUTHORIZATION, client.getAccessToken());
                    request.getHeaders().put(HttpHeaders.ACCEPT, singletonList(MediaType.APPLICATION_JSON_VALUE));
                    request.getHeaders().add("data-partition-id", Config.getDataPartitionIdTenant1());
                    return execution.execute(request, body);
                })
                .build();
        schemaBaseUrl = Config.getSchemaBaseURL();
    }

    public boolean exists(SchemaIdentity identity) {
        String uri = buildSchemaUri(identity.getId());
        LOGGER.log(Level.INFO, "Checking whether the schema exists having identity={0}", identity);
        ResponseEntity<?> response = template.exchange(uri, HttpMethod.GET, null, Object.class);
        LOGGER.log(Level.INFO, "Finished checking whether the schema exists having identity={0}, response={1}", new Object[]{identity, response});
        return response.getStatusCode() == HttpStatus.OK;
    }

    public void create(SchemaModel schema) {
        String uri = buildSchemaUri();
        LOGGER.log(Level.INFO, "Creating the schema={0}", schema);
        HttpHeaders headers = new HttpHeaders();
        headers.put(HttpHeaders.CONTENT_TYPE, singletonList(MediaType.APPLICATION_JSON_VALUE));
        HttpEntity<SchemaModel> httpEntity = new HttpEntity<>(schema, headers);
        template.exchange(uri, HttpMethod.PUT, httpEntity, Object.class);
        LOGGER.log(Level.INFO, "Finished creating the schema={0}", schema);
    }

    public void createIfNotExist(SchemaModel schema) {
        if (!exists(schema.getSchemaInfo().getSchemaIdentity())) {
            create(schema);
        }
    }

    private String buildSchemaUri(String id) {
        return UriComponentsBuilder.fromHttpUrl(schemaBaseUrl)
                .path("/schema/{schema-id}")
                .buildAndExpand(id).toUriString();
    }

    private String buildSchemaUri() {
        return UriComponentsBuilder.fromHttpUrl(schemaBaseUrl)
                .path("/schema")
                .build().toUriString();
    }

}
