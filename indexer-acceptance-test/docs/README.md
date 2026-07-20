### Running E2E Tests

You will need to have the following environment variables defined.

| name                                | value                                                            | description                                                                                       | sensitive? | source                              |
|-------------------------------------|------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|------------|-------------------------------------|
| `HOST`                              | eg. `https://osdu.com`                                           | -                                                                                                 | no         | -                                   |
| `INDEXER_HOST`                      | eg. `https://os-indexer-dot-opendes.appspot.com/api/indexer/v2/` | Indexer API endpoint                                                                              | no         | output of infrastructure deployment |
| `SEARCH_HOST`                       | eg. `https://osdu.com/api/search/v2/`                            | -                                                                                                 | no         | -                                   |
| `STORAGE_HOST`                      | ex `http://os-storage-dot-opendes.appspot.com/api/storage/v2/`   | Storage API endpoint                                                                              | no         | output of infrastructure deployment |
| `SECURITY_HTTPS_CERTIFICATE_TRUST`  | ex `false`                                                       | Elastic client connection uses TrustSelfSignedStrategy(), if it is 'true'                         | no         | output of infrastructure deployment |
| `DEFAULT_DATA_PARTITION_ID_TENANT1` | ex `opendes`                                                     | HTTP Header 'Data-Partition-ID'                                                                   | no         | -                                   |
| `DEFAULT_DATA_PARTITION_ID_TENANT2` | ex `opendes`                                                     | HTTP Header 'Data-Partition-ID'                                                                   | no         | -                                   |
| `ENTITLEMENTS_DOMAIN`               | eg. `group`                                                      | -                                                                                                 | no         | -                                   |
| `LEGAL_TAG`                         | ex `opendes-demo-legaltag`                                       | valid legal tag with a other relevant data countries from `DEFAULT_OTHER_RELEVANT_DATA_COUNTRIES` | no         | -                                   |
| `OTHER_RELEVANT_DATA_COUNTRIES`     | ex `US`                                                          | valid legal tag with a other relevant data countries                                              | no         | -                                   |

Authentication can be provided as OIDC config:

| name                                            | value                                   | description                         | sensitive? | source                              |
|-------------------------------------------------|-----------------------------------------|-------------------------------------|------------|-------------------------------------|
| `PRIVILEGED_USER_OPENID_PROVIDER_CLIENT_ID`     | `********`                              | Client Id for `$INTEGRATION_TESTER` | yes        | -                                   |
| `PRIVILEGED_USER_OPENID_PROVIDER_CLIENT_SECRET` | `********`                              | secret for `$INTEGRATION_TESTER`    | yes        | -                                   |
| `TEST_OPENID_PROVIDER_URL`                      | `https://keycloak.com/auth/realms/osdu` | OpenID provider url                 | yes        | -                                   |
| `ELASTIC_HOST`                                  | eg. `elastic.domain.com`                | Host Elasticsearch                  | yes        | output of infrastructure deployment |
| `ELASTIC_USER_NAME`                             | `********`                              | User name for Elasticsearch         | yes        | output of infrastructure deployment |
| `ELASTIC_PASSWORD`                              | `********`                              | Password for Elasticsearch          | yes        | output of infrastructure deployment |
| `ELASTIC_PORT`                                  | ex `9243`                               | Port Elasticsearch                  | yes        | output of infrastructure deployment |
Or tokens can be used directly from env variables:

| name                    | value      | description           | sensitive? | source |
|-------------------------|------------|-----------------------|------------|--------|
| `PRIVILEGED_USER_TOKEN` | `********` | PRIVILEGED_USER Token | yes        | -      |

#### Entitlements configuration for Integration Accounts

| INTEGRATION_TESTER            | NO_DATA_ACCESS_TESTER |
|-------------------------------|-----------------------|
| users                         |                       |
| users.datalake.ops            |                       |
| service.storage.creator       |                       |
| service.entitlements.user     |                       |
| service.search.user           |                       |
| service.search.admin          |                       |
| data.test1                    |                       |
| data.integration.test         |                       |
| users@{tenant1}@{groupId}.com |                       |

Execute following command to build code and run all the integration tests:

 ```bash
 # Note: this assumes that the environment variables for integration tests as outlined
 #       above are already exported in your environment.
 # build + install integration test core
 $ (cd indexer-acceptance-test && mvn clean verify)
 ```

## License

Copyright © Google LLC

Copyright © EPAM Systems

Copyright © ExxonMobil

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
