### Running E2E Tests

These acceptance tests use the shared **os-core-test** library
(`org.opengroup.osdu:os-core-test`) for authentication, service configuration and HTTP access.
Service hosts are no longer configured per service. Instead a single `HOST` (plus
`DATA_PARTITION_ID`) is provided and os-core-test derives each service URL from it:

| service       | derived URL                       |
|---------------|-----------------------------------|
| indexer       | `{HOST}/api/indexer/v2/`          |
| storage       | `{HOST}/api/storage/v2/`          |
| schema        | `{HOST}/api/schema-service/v1/`   |
| search        | `{HOST}/api/search/v2/`           |
| entitlements  | `{HOST}/api/entitlements/v2/`     |

> Migration note: the former `INDEXER_HOST`, `STORAGE_HOST`, `SEARCH_HOST` and `SCHEMA_HOST`
> variables are no longer used. Set `HOST` and `DATA_PARTITION_ID` instead. A ready-to-edit
> template is provided in [`.env.example`](../cimpl-variables.yml); copy it to `.env` or export the
> variables in your environment.

You will need the following environment variables defined.

| name                               | value                                    | description                                                                                | sensitive? | source                              |
|------------------------------------|------------------------------------------|--------------------------------------------------------------------------------------------|------------|-------------------------------------|
| `HOST`                             | eg. `https://osdu.com`                   | Base host for all OSDU services (indexer, storage, schema, search, entitlements)           | no         | output of infrastructure deployment |
| `DATA_PARTITION_ID`                | ex `opendes`                             | Default `data-partition-id` header used by os-core-test clients                            | no         | -                                   |
| `SECURITY_HTTPS_CERTIFICATE_TRUST` | ex `true` for self-signed/passthrough ES | Elastic client connection trusts self-signed Elasticsearch certificates when set to `true` | no         | output of infrastructure deployment |
| `ENTITLEMENTS_DOMAIN`              | eg. `group`                              | Group domain used when building record ACLs (falls back to `GROUP_ID`, then `group`)       | no         | -                                   |

Authentication can be provided as OIDC config:

| name                                            | value                                   | description                         | sensitive? | source |
|-------------------------------------------------|-----------------------------------------|-------------------------------------|------------|--------|
| `PRIVILEGED_USER_OPENID_PROVIDER_CLIENT_ID`     | `********`                              | Client Id for `$INTEGRATION_TESTER` | yes        | -      |
| `PRIVILEGED_USER_OPENID_PROVIDER_CLIENT_SECRET` | `********`                              | secret for `$INTEGRATION_TESTER`    | yes        | -      |
| `PRIVILEGED_USER_OPENID_PROVIDER_SCOPE`         | ex `openid` (optional)                  | OAuth scope(s) for PRIVILEGED_USER  | no         | -      |
| `TEST_OPENID_PROVIDER_URL`                      | `https://keycloak.com/auth/realms/osdu` | OpenID provider url                 | yes        | -      |

Or a token can be used directly from an env variable (skips OpenID):

| name                    | value      | description           | sensitive? | source |
|-------------------------|------------|-----------------------|------------|--------|
| `PRIVILEGED_USER_TOKEN` | `********` | PRIVILEGED_USER Token | yes        | -      |

Elasticsearch connection (used for index verification, no os-core-test equivalent):

| name                                            | value                    | description                                                                            | sensitive? | source                              |
|-------------------------------------------------|--------------------------|----------------------------------------------------------------------------------------|------------|-------------------------------------|
| `ELASTIC_HOST`                                  | eg. `elastic.domain.com` | Host Elasticsearch                                                                     | yes        | output of infrastructure deployment |
| `ELASTIC_USER_NAME`                             | `********`               | User name for Elasticsearch                                                            | yes        | output of infrastructure deployment |
| `ELASTIC_PASSWORD`                              | `********`               | Password for Elasticsearch                                                             | yes        | output of infrastructure deployment |
| `ELASTIC_PORT`                                  | ex `9243`                | Port Elasticsearch                                                                     | yes        | output of infrastructure deployment |
| `ELASTIC_SSL_ENABLED` / `ELASTIC_8_SSL_ENABLED` | ex `true`                | Enables HTTPS when connecting to Elasticsearch; acceptance tests expect TLS by default | no         | output of infrastructure deployment |

> For environments where Elasticsearch is exposed through a TLS passthrough gateway or a
> self-signed certificate, run the acceptance tests with `SECURITY_HTTPS_CERTIFICATE_TRUST=true`
> and keep `ELASTIC_SSL_ENABLED` / `ELASTIC_8_SSL_ENABLED` set to `true`. This only relaxes
> certificate trust; if the Elasticsearch certificate SANs do not include the external hostname,
> the tests must still connect through a SAN-matching host such as `localhost` via port-forwarding.

#### User role verification

On start-up os-core-test verifies that the test user holds the required entitlements roles. The
expected roles are declared in [`src/test/resources/required-roles.json`](../src/test/resources/required-roles.json)
and must stay in sync with the integration account below. Set `"enabled": false` in that file (or
`USER_ROLES_CHECK_ENABLED=false`) to skip the check.

#### Entitlements configuration for Integration Accounts

| INTEGRATION_TESTER (PRIVILEGED_USER) | NO_DATA_ACCESS_TESTER |
|--------------------------------------|-----------------------|
| users                                |                       |
| users.datalake.ops                   |                       |
| service.storage.creator              |                       |
| service.entitlements.user            |                       |
| service.search.user                  |                       |
| service.search.admin                 |                       |

Execute following command to build code and run all the integration tests:

 ```bash
 # Note: this assumes that the environment variables for integration tests as outlined
 #       above are already exported in your environment (or present in a .env file).
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
