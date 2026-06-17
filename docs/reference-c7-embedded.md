---
title: Process Engine Adapter C7 Embedded
---

# Decisions and supported features

## Spring Boot compatibility

The embedded Spring Boot starter supports Spring Boot 3 and Spring Boot 4. For Spring Boot 4, the scheduler
auto-configuration uses adapter-owned threading conditions instead of Spring Boot's removed `Threading.VIRTUAL` and
`Threading.PLATFORM` enum values.

If `spring.threads.virtual.enabled=true` and the application runs on Java 21 or newer, the starter contributes a
`SimpleAsyncTaskScheduler` for Spring's default `taskScheduler` bean. Otherwise, it falls back to a
`ThreadPoolTaskScheduler`.

The public Spring Boot 4 example uses Camunda 7 Community Edition `7.24.0`, the last CE release published on Maven
Central. Enterprise patch versions require the consuming application to configure Camunda Enterprise repositories and
versions explicitly.

Camunda 7.24's Spring Boot starter still references the Spring Boot 3 Hibernate JPA auto-configuration class
`org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration`. Under Spring Boot 4, the embedded adapter
starter filters that Camunda auto-configuration and contributes an equivalent compatibility auto-configuration ordered
after Spring Boot 4's `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`.

The compatibility layer does not replace Camunda runtime dependencies. Embedded Spring Boot 4 applications still need a
working `DataSource` and transaction manager, for example via `spring-boot-starter-jdbc`. Applications that persist
custom object variables as JSON should add `camunda-engine-plugin-spin` and
`camunda-spin-dataformat-json-jackson`; the Spring Boot 4 example uses those dependencies to serialize the sample
`LocalDateTime` payload.

## Message Correlation

Correlation API implementation support the following restrictions:

| Key                       | Value                  | Description                                                                                                   |
|---------------------------|------------------------|---------------------------------------------------------------------------------------------------------------|
| `tenantId`                | The id of the tenant   | Correlates messages for process instances with given tenant id.                                               |
| `withoutTenantId`         | none                   | If restriction is present, correlate only with process instances without tenant id.                           |
| `useGlobalCorrelationKey` | `true` or `false`      | If set to false (default if not set), correlate using local variables, use global process variable otherwise. |


## Task Information

Currently, the Process Engine Adapter C7 Remote supports the following values in task information meta block, mapped from the Camunda C7 engine:

The `TaskInformation.getMeta()` provides meta information about the task in form of a `Map<String, String>` for maximum compatibility. The Original Type column denotes
the real type, you want to access if reading the field. For this purpose, `TaskInformation` offers special access methods `getMetaValueAsOffsetDate` and `getMetaValueAsStringSet`.


### User Tasks

| Key                  | Original Type  | Description                                                                 | Example                       |
|----------------------|----------------|-----------------------------------------------------------------------------|-------------------------------|
| activityId           | String         | Id of the element in BPMN (Task definition key)                             | approve_user_task             |
| processDefinitionId  | String         | Id of process definition (given at deployment time)                         | approval_process:912834729348 |
| processDefinitionKey | String         | Id of the process element in BPMN (Process Definition key)                  | approval_process              |
| tenantId             | String         | Tenant Id                                                                   | my_tenant                     |
| taskName             | String         | Name of the user task (from BPMN or modified by the create listener)        | Approve Order                 |
| taskDescription      | String         | Description of the user task (from BPMN or modified by the create listener) | Approve provided order.       |
| assignee             | String         | Assignee of the user task                                                   | USER12345                     |
| candidateUsers       | Set<String>    | Set of candidate users, separated by a `,`                                  | USER12345,USER12346,USER12347 |
| candidateGroups      | Set<String>    | Set of candidate groups, separated by a `,`                                 | marketing,sales               |
| creationDate         | OffsetDateTime | Time stamp of task creation formatted as ISO-8601 in UTC                    | 2025-05-01T10:00:00.000Z      |
| followUpDate         | OffsetDateTime | Time stamp of task follow-up formatted as ISO-8601 in UTC                   | 2025-05-02T10:00:00.000Z      |
| dueDate              | OffsetDateTime | Time stamp of task due formatted as ISO-8601 in UTC                         | 2025-05-05T10:00:00.000Z      |
| lastUpdatedDate      | OffsetDateTime | Time stamp of task last update formatted as ISO-8601 in UTC                 | 2025-05-05T10:00:00.000Z      |

### Service Tasks

| Key                  | Original Type  | Description                                                                | Example                       |
|----------------------|----------------|----------------------------------------------------------------------------|-------------------------------|
| activityId           | String         | Id of the element in BPMN (Task definition key)                            | approve_user_task             |
| processDefinitionId  | String         | Id of process definition (given at deployment time)                        | approval_process:912834729348 |
| processDefinitionKey | String         | Id of the process element in BPMN (Process Definition key)                 | approval_process              |
| tenantId             | String         | Tenant Id                                                                  | my_tenant                     |
| topicName            | String         | Topic name (from BPMN) for external task                                   | topic_approve                 |
| creationDate         | OffsetDateTime | Time stamp of task creation formatted as ISO-8601 in UTC                   | 2025-05-01T10:00:00.000Z      |

## Engine Command Executor

`EngineCommandExecutor` is an embedded-adapter-specific class that controls how the four core API calls
(`correlateMessage`, `sendSignal`, `startProcess`, `deploy`) are dispatched to the embedded Camunda 7 engine.

By default, all engine calls are submitted asynchronously to `ForkJoinPool.commonPool()`. This means they run on a
**different thread** from the caller and do **not** participate in the caller's `@Transactional` context — a rollback
on the calling thread will **not** roll back the engine operation.

### Customizing execution

Provide a Spring bean of type `EngineCommandExecutor` to override the default. The
auto-configured default is annotated with `@ConditionalOnMissingBean`, so your bean takes precedence automatically.

**Same-thread (synchronous) execution** — engine calls run on the calling thread and honour `@Transactional`:

```kotlin
@Bean
fun engineCommandExecutor(): EngineCommandExecutor =
  EngineCommandExecutor(Executor { it.run() })
```

**Virtual-thread execution** — lightweight concurrency without pinning platform threads:

```kotlin
@Bean
fun engineCommandExecutor(): EngineCommandExecutor =
  EngineCommandExecutor(Executors.newVirtualThreadPerTaskExecutor())
```
