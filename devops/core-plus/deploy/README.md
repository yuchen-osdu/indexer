# Deploy helm chart

## Introduction

This chart bootstraps the Indexer service on a [Kubernetes](https://kubernetes.io) cluster using [Helm](https://helm.sh) package manager.

## Prerequisites

The chart needs **Istio**. The **Kubernetes Gateway API** CRDs are only needed when `httpRoute.enabled` is `true`.
> It is possible to use other versions, but it hasn't been tested

- **HELM** (version: v3.7.1 or higher) [helm](https://helm.sh/docs/intro/install/)
- **Kubectl** (version: v1.21.0 or higher) [kubectl](https://kubernetes.io/docs/tasks/tools/#kubectl)

## Installation

Set the variables in the **values.yaml** file. Some values are prefilled, others you need to specify.

### Global variables

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|----------|
**global.domain** | your domain, ex `example.com`. Not used by the chart templates (the `VirtualService` that used it was removed). Use `global.hostname` for the route host | string | `""` | no
**global.hostname** | host the `HTTPRoute` attaches to when `httpRoute.hostnames` is empty, ex `osdu.example.com` | string | `""` | only if `httpRoute.enabled` is true and `httpRoute.hostnames` is empty
**global.limitsEnabled** | whether CPU and memory limits are enabled | boolean | `true` | yes

### Rosa flag

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|----------|
**rosa** | enables configuration specific to ROSA environments | boolean | `false` | yes

### Configmap variables

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|----------|
**data.logLevel** | logging level | string | `ERROR` | yes
**data.entitlementsHost** | entitlements service host address | string | `http://entitlements` | yes
**data.partitionHost** | partition service host address | string | `http://partition` | yes
**data.schemaHost** | schema service host address | string | `http://schema` | yes
**data.storageHost** | storage service host address | string | `http://storage` | yes
**data.securityHttpsCertificateTrust** | trust HTTPS certificates | string | `true` | yes
**data.javaToolOptions** | JVM options | string | `-XX:ActiveProcessorCount=8` | yes
**data.otelExporterEndpoint** | OpenTelemetry exporter endpoint | string | `""` | no
**data.otelExporterProtocol** | OpenTelemetry exporter protocol | string | `""` | no
**data.pubsubSearchTopicV2** | PubSub topic name for V2 messages (collaboration context) | string | `records-changed-v2` | yes

### Deployment variables

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|----------|
**data.requestsCpu** | amount of requested CPU | string | `35m` | yes
**data.requestsMemory** | amount of requested memory | string | `640Mi` | yes
**data.limitsCpu** | CPU limit | string | `1` | only if `global.limitsEnabled` is true
**data.limitsMemory** | memory limit | string | `1G` | only if `global.limitsEnabled` is true
**data.serviceAccountName** | name of your service account | string | `indexer` | yes
**data.imagePullPolicy** | when to pull image | string | `IfNotPresent` | yes
**data.image** | service image | string | `""` | yes
**data.redisIndexerHost** | Redis host. Change it to use an external Redis | string | `""` | no
**data.redisIndexerPort** | Redis port | integer | `6379` | no
**data.redisImage** | Redis image | string | `docker.io/library/redis:7` | no

### Config variables

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|----------|
**conf.appName** | name of the app, also the Service name | string | `indexer` | yes
**conf.configmap** | configmap to be used | string | `indexer-config` | yes
**conf.replicas** | number of replicas | integer | `1` | yes
**conf.elasticSecretName** | Elasticsearch secret name | string | `indexer-elastic-secret` | yes
**conf.keycloakSecretName** | Keycloak secret name | string | `indexer-keycloak-secret` | yes
**conf.rabbitmqSecretName** | RabbitMQ secret name | string | `rabbitmq-secret` | yes
**conf.indexerRedisSecretName** | Redis secret name | string | `indexer-redis-secret` | yes

### Routing (HTTPRoute)

The chart owns the routing for the service. It renders a Gateway API `HTTPRoute`, disabled by default. The chart no longer ships an Istio `VirtualService`. Access to the internal `_dps` paths stays restricted by the `AuthorizationPolicy` in this chart.

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|----------|
**httpRoute.enabled** | render the `HTTPRoute` | boolean | `false` | yes
**httpRoute.name** | route name. When empty the name is `<release>-route`, which differs from any route of the same name defined outside this chart | string | `""` | no
**httpRoute.parentRefs** | gateways the route attaches to | list | `cimpl-gateway` in `istio-system` | yes
**httpRoute.hostnames** | hosts the route serves. When empty, `global.hostname` is used | list | `[]` | one of `httpRoute.hostnames` or `global.hostname`
**httpRoute.routes** | one rule per entry, see below | list | `/api/indexer/` | yes

The render fails when `httpRoute.enabled` is true and both `httpRoute.hostnames` and `global.hostname` are empty, so the route is never exposed on all hosts.

Each entry of `httpRoute.routes` accepts:

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|----------|
**path** | path to match | string | - | yes
**pathType** | match type, `PathPrefix` or `Exact` | string | `PathPrefix` | no
**serviceName** | backend Service name | string | `conf.appName` | no
**port** | backend Service port | integer | `80` | no
**filters** | Gateway API filters, for example `URLRewrite` | list | - | no

Example, a route with a rewrite and another port:

```yaml
httpRoute:
  enabled: true
  hostnames:
    - osdu.example.com
  routes:
    - path: /api/indexer/
    - path: /other/
      port: 9002
      filters:
        - type: URLRewrite
          urlRewrite:
            path:
              type: ReplacePrefixMatch
              replacePrefixMatch: /
```

### Istio variables

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|----------|
**istio.proxyCPU** | CPU request for Envoy sidecars | string | `10m` | yes
**istio.proxyCPULimit** | CPU limit for Envoy sidecars | string | `200m` | yes
**istio.proxyMemory** | memory request for Envoy sidecars | string | `100Mi` | yes
**istio.proxyMemoryLimit** | memory limit for Envoy sidecars | string | `256Mi` | yes
**istio.sidecarInject** | inject the Envoy sidecar | boolean | `true` | yes

### Install the helm chart

Run this command from within this directory:

```console
helm install core-plus-indexer-deploy .
```

To expose the service through the Gateway API:

```console
helm install core-plus-indexer-deploy . --set httpRoute.enabled=true --set global.hostname=osdu.example.com
```

To check the rendered route without installing:

```console
helm template core-plus-indexer-deploy . --set httpRoute.enabled=true --set global.hostname=osdu.example.com -s templates/httproute.yaml
```
