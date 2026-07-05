---
title: Camunda Platform 7 as remote engine
---

If you start with a Camunda Platform 7, operated remotely, the following configuration is applicable for you.

First of all add the corresponding adapter to your project's classpath. In order to connect to remote engine,
you need a Camunda 7 REST client on the classpath. The current examples use the Holunda Feign starter. Add the official Camunda external task client only if you want subscribed service-task delivery (`remote_subscribed`):

```xml
<dependencies>
  <!-- the correct adapter -->
  <dependency>
    <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
    <artifactId>process-engine-adapter-camunda-platform-c7-remote-spring-boot-starter</artifactId>
    <version>${process-engine-api.version}</version>
  </dependency>
  <!-- rest client library -->
  <dependency>
    <groupId>io.holunda.c7</groupId>
    <artifactId>c7-rest-client-spring-boot-starter-feign</artifactId>
    <version>${c7.version}</version>
  </dependency>
  <!-- Optional, only for service-tasks.delivery-strategy=remote_subscribed -->
  <dependency>
    <groupId>org.camunda.bpm.springboot</groupId>
    <artifactId>camunda-bpm-spring-boot-starter-external-task-client</artifactId>
    <version>7.24.0</version>
  </dependency>
</dependencies>
```

If you build on Spring Boot 4, use `io.holunda.c7:c7-rest-client-spring-boot-starter-feign-4` instead of `...-feign`.

## Jackson and Spin combinations

For the remote adapter, Jackson 3 is the straightforward Spring Boot 4 path.

- The remote adapter does not depend on embedded Camunda Spin.
- Jackson 3 can therefore be used without the embedded Spin limitation.
- The main Spin restriction applies to embedded Camunda 7 JSON variable serialization, not to the remote adapter itself.

If you need Spin-based JSON variable serialization, that restriction remains on the remote engine side. The adapter's
own serialization can still follow the Spring Boot ecosystem used by the remote application.

And finally, add the following configuration to your configuration properties. Here is a version for `application.yaml`:

```yaml 
dev:
  bpm-crafters:
    process-api:
      adapter:
        c7remote:
          enabled: true
          service-tasks:
            delivery-strategy: remote_scheduled # or remote_subscribed with the official external task client
            schedule-delivery-fixed-rate-in-seconds: 5
            worker-id: remote-worker
            lock-time-in-seconds: 10
            max-task-count: 2
            deserialize-on-server: false
            worker-thread-pool-size: 2
            worker-thread-pool-queue-capacity: 5
            execute-initial-pull-on-startup: true
          user-tasks:
            delivery-strategy: remote_scheduled
            schedule-delivery-fixed-rate-in-seconds: 5
            execute-initial-pull-on-startup: false
            deserialize-on-server: false

# Needed for the remote REST client in all remote setups:
feign:
  client:
    config:
      default:
        url: "http://localhost:9090/engine-rest/"

# Only needed for service-tasks.delivery-strategy=remote_subscribed
camunda:
  bpm:
    client:
      base-url: "http://localhost:9090/engine-rest/"
```
