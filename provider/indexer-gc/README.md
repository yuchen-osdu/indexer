# Indexer Service

Indexer GC is a [Spring Boot](https://spring.io/projects/spring-boot) service that is responsible for indexing Records that enable the `search` service to execute OSDU R3 domain searches against Elasticsearch.

## Table of Contents <a name="TOC"></a>

* [Getting started](#Getting-started)
* [Mappers](#Mappers)
* [Settings and Configuration](#Settings-and-Configuration)
* [Run service](#Run-service)
* [Testing](#Testing)
* [Deployment](#Deployment)
* [Entitlements groups](#Entitlements-groups)
* [Licence](#License)

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system.

# Configuration

## Service Configuration

### Google Cloud

[Google Cloud service configuration](docs/gc/README.md)

### Limitations of the current version

In the current version, the mappers are equipped with several drivers to the stores and the message broker:

* OQM (mapper to message brokers): Google PubSub; RabbitMQ

## Settings and Configuration

### Prerequisites

1. Mandatory

* JDK 8
* Lombok 1.16 or later
* Maven

2. For Google Cloud only

* GCloud SDK with java (latest version)

### Google Cloud Service Configuration

[Google Cloud service configuration](docs/gc/README.md)

## Run service

### Run Locally

Check that maven is installed:

```bash
$ mvn --version
Apache Maven 3.6.0
Maven home: /usr/share/maven
Java version: 1.8.0_212, vendor: AdoptOpenJDK, runtime: /usr/lib/jvm/jdk8u212-b04/jre
...
```

You may need to configure access to the remote maven repository that holds the OSDU dependencies. This file should live within `~/.mvn/community-maven.settings.xml`:

```bash
$ cat ~/.m2/settings.xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>community-maven-via-private-token</id>
            <!-- Treat this auth token like a password. Do not share it with anyone, including Microsoft support. -->
            <!-- The generated token expires on or before 11/14/2019 -->
             <configuration>
              <httpHeaders>
                  <property>
                      <name>Private-Token</name>
                      <value>${env.COMMUNITY_MAVEN_TOKEN}</value>
                  </property>
              </httpHeaders>
             </configuration>
        </server>
    </servers>
</settings>
```

* Update the Google cloud SDK to the latest version:

```bash
gcloud components update
```

* Set Google Project Id:

```bash
gcloud config set project <YOUR-PROJECT-ID>
```

* Perform a basic authentication in the selected project:

```bash
gcloud auth application-default login
```

* Navigate to indexer service's root folder and run:
 
```bash
mvn clean install   
```

* If you wish to see the coverage report then go to testing/target/site/jacoco-aggregate and open index.html

* If you wish to build the project without running tests

```bash
mvn clean install -DskipTests
```

After configuring your environment as specified above, you can follow these steps to build and run the application. These steps should be invoked from the *repository root.*

```bash
cd provider/indexer-gc
```

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED \
         --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
         -Dloader.main=org.opengroup.osdu.indexer.provider.gcp.IndexerGcpApplication \
         -jar /target/indexer-${VERSION}-spring-boot.jar
```
## Testing

Navigate to indexer service's root folder and run all the tests:

```bash
# build + install integration test core
$ (cd testing/indexer-test-core/ && mvn clean install)
```

### Running E2E Tests

This section describes how to run cloud OSDU E2E tests.

### Google Cloud test configuration

[Google Cloud service configuration](docs/gc/README.md)

## Deployment

Indexer Service is compatible with Cloud Run.

- To deploy into Cloud run, please, use this documentation:
  <https://cloud.google.com/run/docs/quickstarts/build-and-deploy>
- To deploy into GKE, please, use this documentation:
  <https://cloud.google.com/cloud-build/docs/deploying-builds/deploy-gke>

## Entitlements groups
Entitlements groups of Indexer Service Account:

| Required Roles                   |
|----------------------------------|
| service.entitlements.user        |
| service.schema-service.viewers   |
| service.storage.admin            |
| service.search.admin             |

## Licence

Copyright © Google LLC
Copyright © EPAM Systems

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
