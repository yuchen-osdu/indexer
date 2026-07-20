# os-indexer-azure

os-indexer-azure is a [Spring Boot](https://spring.io/projects/spring-boot) service that is responsible for indexing Records that enable the `os-search` service to execute OSDU R2 domain searches against Elasticsearch.

## Running Locally

### Requirements

In order to run this service locally, you will need the following:

- [Maven 3.8.0+](https://maven.apache.org/download.cgi)
- [Java 17](https://adoptopenjdk.net/)
- Infrastructure dependencies, deployable through the relevant [infrastructure template](https://community.opengroup.org/osdu/platform/deployment-and-operations/infra-azure-provisioning/-/blob/master/docs/service-automation.md)
- While not a strict dependency, example commands in this document use [bash](https://www.gnu.org/software/bash/)

### General Tips

**Environment Variable Management**
The following tools make environment variable configuration simpler
 - [direnv](https://direnv.net/) - for a shell/terminal environment
 - [EnvFile](https://plugins.jetbrains.com/plugin/7861-envfile) - for [Intellij IDEA](https://www.jetbrains.com/idea/)

**Lombok**
This project uses [Lombok](https://projectlombok.org/) for code generation. You may need to configure your IDE to take advantage of this tool.
 - [Intellij configuration](https://projectlombok.org/setup/intellij)
 - [VSCode configuration](https://projectlombok.org/setup/vscode)


### Environment Variables

In order to run the service locally, you will need to have the following environment variables defined.

**Note** The following command can be useful to pull secrets from keyvault:
```bash
az keyvault secret show --vault-name $KEY_VAULT_NAME --name $KEY_VAULT_SECRET_NAME --query value -otsv
```

**Required to run service**

| name | value | description | sensitive? | source |
| ---  | ---   | ---         | ---        | ---    |
| `runtime.env.local` | false (change this to `true` when running locally) | Var to check if app is running locally | no | - |
| `server.servlet.contextPath` | `/api/indexer/v2/` | Servlet context path | no | - |
| `schema_service_url` | ex `https://schema.azurewebsites.net` | Endpoint of schema service | no | output of infrastructure deployments |
| `SCHEMA_HOST` | `${schema_service_url}/schema` | Endpoint of schema API | no | - |
| `SEARCH_HOST` | `${search_service_endpoint}` | Endpoint of search API | no | - |
| `storage_service_url` | ex `https://storage.azurewebsites.net` | Endpoint of storage service | no | output of infrastructure deployments |
| `STORAGE_SCHEMA_HOST` | `${storage_service_url}/schemas` | Endpoint of schema API | no | - |
| `STORAGE_QUERY_RECORD_HOST` | `${storage_service_url}/query/records` | Endpoint of records API | no | - |
| `STORAGE_QUERY_RECORD_FOR_CONVERSION_HOST` | `${storage_service_url}/query/records:batch` | Endpoint of records batch API | no | - |
| `STORAGE_RECORDS_BATCH_SIZE` | 20 | Batch size for storage API `POST {endpoint}/query/records:batch` | no | - |
| `STORAGE_RECORDS_BY_KIND_BATCH_SIZE` | 100 | Batch size for storage API `GET {endpoint}/query/records`. If this is not present, defaults to value of `STORAGE_RECORDS_BATCH_SIZE` | no | - |
| `KEYVAULT_URI` | ex `https://foo-kv.vault.azure.net/` | . | . | . |
| `appinsights_key` | `********` | App Insights key | yes | output of infrastructure deployments |
| `aad_client_id` | `********` | AAD client application ID | yes | output of infrastructure deployment |
| `cosmosdb_database` | ex `dev-osdu-r2-db` | Cosmos database for documents | no | output of infrastructure deployment |
| `servicebus_topic_name` | `recordstopic` | Service Bus topic name | no | output of infrastructure deployments |
| `entitlements_service_endpoint` | ex `https://entitlements.azurewebsites.net` | Entitlements service endpoint | no | Service Bus topic name |
| `AZURE_CLIENT_ID` | `********` | Identity to run the service locally. This enables access to Azure resources. You only need this if running locally | yes | keyvault secret: `$KEYVAULT_URI/secrets/app-dev-sp-username` |
| `AZURE_TENANT_ID` | `********` | AD tenant to authenticate users from | yes | keyvault secret: `$KEYVAULT_URI/secrets/app-dev-sp-tenant-id` |
| `AZURE_CLIENT_SECRET` | `********` | Secret for `$AZURE_CLIENT_ID` | yes | keyvault secret: `$KEYVAULT_URI/secrets/app-dev-sp-password` |
| `partition_service_endpoint` | ex `https://foo-partition.azurewebsites.net` | Partition Service API endpoint | no | output of infrastructure deployment |
| `azure.activedirectory.app-resource-id` | `********` | AAD client application ID | yes | output of infrastructure deployment |
| `azure_istioauth_enabled` | `true` | Flag to Disable AAD auth | no | -- |

**Required to run integration tests**

| name | value | description | sensitive? | source |
| ---  | ---   | ---         | ---        | ---    |
| `AZURE_AD_TENANT_ID` | `********` | AD tenant to authenticate users from | yes | -- |
| `AZURE_TESTER_SERVICEPRINCIPAL_SECRET` | `********` | Secret for `$INTEGRATION_TESTER` | yes | -- |
| `INTEGRATION_TESTER` | `********` | System identity to assume for API calls. Note: this user must have entitlements configured already | no | -- |
| `AZURE_AD_APP_RESOURCE_ID` | `********` | AAD client application ID | yes | output of infrastructure deployment |
| `ELASTIC_USER_NAME` | ex `elastic` | Elasticsearch cluster username | yes | output of infrastructure deployment |
| `ELASTIC_PASSWORD` | `********` | Elasticsearch cluster password | yes | output of infrastructure deployment |
| `ELASTIC_HOST` | ex `foobar.ece.msft-osdu-test.org` | Elasticsearch cluster endpoint | no | output of infrastructure deployment |
| `ELASTIC_PORT` | ex `9243` | Elasticsearch cluster port | no | output of infrastructure deployment |
| `aad_client_id` | `********` | AAD client application ID | yes | output of infrastructure deployment |
| `DEFAULT_DATA_PARTITION_ID_TENANT1` | ex `opendes` | Primary data partition for queries | no | Data in search index |
| `DEFAULT_DATA_PARTITION_ID_TENANT2` | ex `common` | Secondary data partition for queries | no | Data in search index |
| `STORAGE_HOST` | ex `https://storage.azurewebsites.net/` | Storage service endpoint | no | output of infrastructure deployment |
| `SCHEMA_HOST` | ex `https://schema.azurewebsites.net/` | Endpoint of schema API | no | - |
| `SEARCH_HOST` | ex `https://search.azurewebsites.net/` | Endpoint of search API | no | - |
| `ENVIRONMENT` | `CLOUD` | Deployment environment | no | - |
| `ENTITLEMENTS_DOMAIN` | `contoso.com` | OSDU R2 service domain | no | - |
| `LEGAL_TAG` | `opendes-public-usa-dataset-7643990` | Legal tag used for test records | no | Needs to be in DB. The referenced tag should already exist. |
| `OTHER_RELEVANT_DATA_COUNTRIES` | `US` | ? | no | - |
| `CUCUMBER_OPTIONS` | `--tags '~@indexer-extended'` OR `--tags '~@* and @indexer-extended'` | By default `--tags '~@* and @indexer-extended'` to enable experimental feature testing | no | - |

### Configure Maven

Check that maven is installed:
```bash
$ mvn --version
Apache Maven 3.8.0
Maven home: /usr/share/maven
Java version: 17.0.7
...
```

### Build and run the application

After configuring your environment as specified above, you can follow these steps to build and run the application. These steps should be invoked from the *repository root.*

```bash
# build + test + install core service code
$ mvn clean install

# build + test + package azure service code
$ (cd provider/indexer-azure/ && mvn clean package)

# run service
#
# Note: this assumes that the environment variables for running the service as outlined
#       above are already exported in your environment.
$ java -jar $(find provider/indexer-azure/target/ -name *-spring-boot.jar) --add-opens java.base/java.lang=ALL-UNNAMED --add-opens  java.base/java.lang.reflect=ALL-UNNAMED

# Alternately you can run using the Mavan Task
$ mvn spring-boot:run
```

### Test the application

After the service has started it should be accessible via a web browser by visiting [http://localhost:8080/api/indexer/v2/swagger](http://localhost:8080/api/indexer/v2/swagger). If the request does not fail, you can then run the integration tests.

> **Note**: the integration tests for `os-indexer-azure` work by validating that records submitted to `os-storage-azure` can eventually be queried by `os-search-azure`. This only works if the messages emitted by `os-storage-azure` can be consumed by `os-indexer-queue-azure`, which will submit the indexing request to `os-indexer-azure`.
>
> In order to make sure that the integration tests are running against your local environment, you will need to make sure that the there is an instance of `os-indexer-queue-azure` that is configured to call your deployment of `os-indexer-azure`, and that this instance of `os-indexer-queue-azure` is the only consumer of the Service Bus topic.
>
> There are a few ways to do this:
>   - Stop the `os-indexer-queue-azure` function in the Azure portal and run it locally. You'll need to remember to restart the Azure deployed `os-indexer-queue-azure` when you are finished testing
>   - Deploy your own infrastructure stack and configure all the services *except* `os-indexer-queue-azure` and `os-indexer-azure` to run in Azure. Then, run `os-indexer-queue-azure` locally
>   - Rely on the integration tests to run through the CI/CD pipeline

```bash
# build + install integration test core
$ (cd testing/indexer-test-core/ && mvn clean install)

# build + run Azure integration tests.
#
# Note: this assumes that the environment variables for integration tests as outlined
#       above are already exported in your environment.
$ (cd testing/indexer-test-azure/ && mvn clean test)
```

## Open API 3.0 - Swagger
- Swagger UI:  http://localhost:8080/api/indexer/v2/swagger (will redirect to  http://localhost:8080/api/indexer/v2/swagger-ui/index.html)
- api-docs (JSON) :  http://localhost:8080/api/indexer/v2/api-docs
- api-docs (YAML) :  http://localhost:8080/api/indexer/v2/api-docs.yaml

All the Swagger and OpenAPI related common properties are managed here [swagger.properties](../../indexer-core/src/main/resources/swagger.properties)

## Debugging

Jet Brains - the authors of Intellij IDEA, have written an [excellent guide](https://www.jetbrains.com/help/idea/debugging-your-first-java-application.html) on how to debug java programs.


## Deploying service to Azure

Service deployments into Azure are standardized to make the process the same for all services if using ADO and are closely related to the infrastructure deployed. The steps to deploy into Azure can be [found here](https://github.com/azure/osdu-infrastructure)

The default ADO pipeline is /devops/azure-pipeline.yml


### Manual Deployment Steps

__Environment Settings__

The following environment variables are necessary to properly deploy a service to an Azure OSDU Environment.

```bash
# Group Level Variables
export AZURE_TENANT_ID=""
export AZURE_SUBSCRIPTION_ID=""
export AZURE_SUBSCRIPTION_NAME=""
export AZURE_PRINCIPAL_ID=""
export AZURE_PRINCIPAL_SECRET=""
export AZURE_APP_ID=""
export AZURE_BASENAME_21=""
export AZURE_BASENAME=""
export AZURE_BASE=""
export AZURE_ELASTIC_HOST=""
export AZURE_ELASTIC_PASSWORD=""

# Pipeline Level Variable
export AZURE_SERVICE="indexer"
export AZURE_BUILD_SUBDIR="provider/indexer-azure"
export AZURE_TEST_SUBDIR="testing/indexer-test-azure"

# Required for Azure Deployment
export AZURE_CLIENT_ID="${AZURE_PRINCIPAL_ID}"
export AZURE_CLIENT_SECRET="${AZURE_PRINCIPAL_SECRET}"
export AZURE_RESOURCE_GROUP="${AZURE_BASENAME}-osdu-r2-app-rg"
export AZURE_APPSERVICE_PLAN="${AZURE_BASENAME}-osdu-r2-sp"
export AZURE_APPSERVICE_NAME="${AZURE_BASENAME_21}-au-${AZURE_SERVICE}"

# Required for Testing
export AZURE_AD_TENANT_ID="$AZURE_TENANT_ID"
export INTEGRATION_TESTER="$AZURE_PRINCIPAL_ID"
export AZURE_TESTER_SERVICEPRINCIPAL_SECRET="$AZURE_PRINCIPAL_SECRET"
export AZURE_AD_APP_RESOURCE_ID="$AZURE_APP_ID"
export aad_client_id="$AZURE_APP_ID"
export STORAGE_HOST="https://{AZURE_BASENAME_21}-au-storage.azurewebsites.net/"
export ELASTIC_HOST="$AZURE_ELASTIC_HOST"
export ELASTIC_PORT="9243"
export ELASTIC_USER_NAME="elastic"
export ELASTIC_PASSWORD="$AZURE_ELASTIC_PASSWORD"
export DEFAULT_DATA_PARTITION_ID_TENANT1="opendes"
export DEFAULT_DATA_PARTITION_ID_TENANT2="common"
export ENVIRONMENT="CLOUD"
export ENTITLEMENTS_DOMAIN="contoso.com"
export LEGAL_TAG="opendes-public-usa-dataset-7643990"
export OTHER_RELEVANT_DATA_COUNTRIES="US"
```


__Azure Service Deployment__


1. Deploy the service using the Maven Plugin  _(azure_deploy)_

```bash
cd $AZURE_BUILD_SUBDIR
mvn azure-webapp:deploy \
  -DAZURE_TENANT_ID=$AZURE_TENANT_ID \
  -Dazure.appservice.subscription=$AZURE_SUBSCRIPTION_ID \
  -DAZURE_CLIENT_ID=$AZURE_CLIENT_ID \
  -DAZURE_CLIENT_SECRET=$AZURE_CLIENT_SECRET \
  -Dazure.appservice.resourcegroup=$AZURE_RESOURCE_GROUP \
  -Dazure.appservice.plan=$AZURE_APPSERVICE_PLAN \
  -Dazure.appservice.appname=$AZURE_APPSERVICE_NAME
```

2. Configure the Web App to start the SpringBoot Application _(azure_config)_

```bash
az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET --tenant $AZURE_TENANT_ID

# Set the JAR FILE as required
TARGET=$(find ./target/ -name '*-spring-boot.jar')
JAR_FILE=${TARGET##*/}

JAVA_COMMAND="java -jar /home/site/wwwroot/${JAR_FILE}"
JSON_TEMPLATE='{"appCommandLine":"%s"}'
JSON_FILE="config.json"
echo $(printf "$JSON_TEMPLATE" "$JAVA_COMMAND") > $JSON_FILE

az webapp config set --resource-group $AZURE_RESOURCE_GROUP --name $AZURE_APPSERVICE_NAME --generic-configurations @$JSON_FILE
```

3. Execute the Integration Tests against the Service Deployment _(azure_test)_

```bash
mvn clean test -f $AZURE_TEST_SUBDIR/pom.xml
```



## License
Copyright © Microsoft Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
