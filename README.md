# Documentation Home

Official Documentation home at [https://osdu.pages.opengroup.org/platform/system/indexer-service/](https://osdu.pages.opengroup.org/platform/system/indexer-service/)

## Prerequisites

- [JDK 25](https://adoptium.net/)
- [Maven 3.9.16+](https://maven.apache.org/download.cgi)

Community / core-plus modules compile and run on Java 25. For core-plus local setup, see [indexer-core-plus/README.md](./indexer-core-plus/README.md).

# Introduction 

os-indexer-azure is a [Spring Boot](https://spring.io/projects/spring-boot) service that is responsible for indexing Records that enable the `os-search` service to execute OSDU R2 domain searches against Elasticsearch.

## Azure Implementation

The [os-indexer-azure README.md](./provider/indexer-azure/README.md) has all the information needed to get started
running the `os-indexer` Azure implementation

## Google Cloud Implementation

The Google Cloud provider has been moved to the [gc-osdu-services](https://community.opengroup.org/osdu/platform/deployment-and-operations/base-containers-gcp/gc-osdu-services) repository.

## AWS Implementation

The AWS provider has been removed from this repository.

## Open API 3.0 - Swagger
- Swagger UI : https://host/context-path/swagger (will redirect to https://host/context-path/swagger-ui/index.html)
- api-docs (JSON) : https://host/context-path/api-docs
- api-docs (YAML) : https://host/context-path/api-docs.yaml

All the Swagger and OpenAPI related common properties are managed here [swagger.properties](./indexer-core/src/main/resources/swagger.properties)

#### Server Url(full path vs relative path) configuration
- `api.server.fullUrl.enabled=true` It will generate full server url in the OpenAPI swagger
- `api.server.fullUrl.enabled=false` It will generate only the contextPath only
- default value is false (Currently only in Azure it is enabled)
[Reference]:(https://springdoc.org/faq.html#_how_is_server_url_generated) 
