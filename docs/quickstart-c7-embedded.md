---
title: Camunda Platform 7 as embedded engine
---

If you start with a Camunda Platform 7, operated in an embedded engine mode, by for example using the Camunda Spring Boot Starter,
the following configuration is applicable for you.

First of all, add the corresponding adapter and an embedded Camunda 7 starter to your project's classpath:

```xml 
<dependencies>
  <dependency>
    <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
    <artifactId>process-engine-adapter-camunda-platform-c7-embedded-spring-boot-starter</artifactId>
    <version>${process-engine-api.version}</version>
  </dependency>
  <dependency>
    <groupId>org.camunda.bpm.springboot</groupId>
    <artifactId>camunda-bpm-spring-boot-starter</artifactId>
    <version>7.24.0</version>
  </dependency>
</dependencies>
```

and finally, add the following configuration to your configuration properties. Here is a version for `application.yaml`:

```yaml 
dev:
  bpm-crafters:
    process-api:
      adapter:
        c7embedded:
          enabled: true
          service-tasks:
            delivery-strategy: embedded_scheduled
            worker-id: embedded-worker
            max-task-count: 100
            lock-time-in-seconds: 10
            retry-timeout-in-seconds: 30
            retries: 3
            execute-initial-pull-on-startup: true
            schedule-delivery-fixed-rate-in-seconds: 10
          user-tasks:
            delivery-strategy: embedded_scheduled
            execute-initial-pull-on-startup: true
            schedule-delivery-fixed-rate-in-seconds: 10

```
