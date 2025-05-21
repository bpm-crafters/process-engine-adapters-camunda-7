---
title: Camunda Platform 7 as remote engine
---

If you start with a Camunda Platform 7, operated remotely, the following configuration is applicable for you.

First of all add the corresponding adapter to your project's classpath. In order to connect to remote engine,
you will need to use some client. Assuming you are using Camunda Hub extension [camunda-platform-7-rest-client-spring-boot](https://github.com/camunda-community-hub/camunda-platform-7-rest-client-spring-boot),
you will also need to add some additional libraries. Here is the result:

```xml

<dependendcies>
  <!-- the correct adapter -->
  <dependency>
    <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
    <artifactId>process-engine-adapter-camunda-platform-c7-remote-spring-boot-starter</artifactId>
    <version>${process-engine-api.version}</version>
  </dependency>
  <!-- rest client library -->
  <dependency>
    <groupId>org.camunda.community.rest</groupId>
    <artifactId>camunda-platform-7-rest-client-spring-boot-starter</artifactId>
    <version>7.23.0</version>
  </dependency>
  <!-- Optional, if you want to use the official camunda client for service task delivery-->
  <dependency>
    <groupId>org.camunda.bpm.springboot</groupId>
    <artifactId>camunda-bpm-spring-boot-starter-external-task-client</artifactId>
    <version>7.23.0</version>
  </dependency>
</dependendcies>
```

And finally, add the following configuration to your configuration properties. Here is a version for `application.yaml`:

```yaml 
dev:
  bpm-crafters:
    process-api:
      adapter:
        c7remote:
          enabled: true
          service-tasks:
            delivery-strategy: remote_scheduled # or remote_subscribed if you want to use official camunda client
            schedule-delivery-fixed-rate-in-seconds: 10
            worker-id: embedded-worker
            lock-time-in-seconds: 10
          user-tasks:
            delivery-strategy: remote_scheduled
            schedule-delivery-fixed-rate-in-seconds: 10

# to tell the client library where the engine is located provide the correct details below:
feign:
  client:
    config:
      default:
        url: "http://localhost:9090/engine-rest/"

# Just needed if you use remote_subscribed as delivery-strategy for service-tasks
camunda:
  bpm:
    client:
      base-url: "http://localhost:9090/engine-rest/"
```
