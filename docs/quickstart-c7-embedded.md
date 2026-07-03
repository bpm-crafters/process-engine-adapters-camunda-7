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

## Spring Boot 4

The embedded adapter starter is compatible with Spring Boot 3 and Spring Boot 4. A runnable Spring Boot 4 example is
available in `examples/java-c7-embedded-sb4`.

For Spring Boot 4, the adapter starter replaces the failing Camunda 7.24 auto-configuration path that references the
Spring Boot 3 class `org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration`. The consuming
application still needs the normal embedded-engine runtime dependencies, including a JDBC starter or equivalent
`DataSource`/transaction-manager setup.

If process variables contain custom objects and `camunda.bpm.default-serialization-format` is set to `application/json`,
add Camunda Spin with the JSON-Jackson data format, for example:

```xml
  <dependency>
    <groupId>org.camunda.bpm</groupId>
    <artifactId>camunda-engine-plugin-spin</artifactId>
  </dependency>
  <dependency>
    <groupId>org.camunda.spin</groupId>
    <artifactId>camunda-spin-dataformat-json-jackson</artifactId>
  </dependency>
```

The Spring Boot 4 example uses the public Camunda 7 Community Edition artifacts in version `7.24.0`. This is the last
Camunda 7 Community Edition release published on Maven Central. If you use maintained Camunda 7 Enterprise patch
versions, configure the corresponding Enterprise repository and dependency versions in your application.

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
