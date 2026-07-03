---
title: Process Engine Adapter C7 Remote
---

# Decisions and supported features

## Configuration

All remote adapter properties use the prefix `dev.bpm-crafters.process-api.adapter.c7remote`.

The remote starter expects a Camunda 7 REST client library on the classpath. The official Camunda external task client is optional and only needed when service tasks use subscribed delivery.

### Classpath options

| Use case | Required dependencies |
|----------|-----------------------|
| Remote adapter in general | `process-engine-adapter-camunda-platform-c7-remote-spring-boot-starter` plus a Feign-based Camunda 7 REST client starter |
| Service tasks with `remote_scheduled` | Same as above |
| Service tasks with `remote_subscribed` | Same as above, plus `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client:7.24.0` |

For Spring Boot 3.x the examples use:

```xml
<dependency>
  <groupId>io.holunda.c7</groupId>
  <artifactId>c7-rest-client-spring-boot-starter-feign</artifactId>
  <version>${c7.version}</version>
</dependency>
```

For Spring Boot 4 use the Boot-4 specific variant:

```xml
<dependency>
  <groupId>io.holunda.c7</groupId>
  <artifactId>c7-rest-client-spring-boot-starter-feign-4</artifactId>
  <version>${c7.version}</version>
</dependency>
```

### Feign client vs official external client for service tasks

| Topic | Feign client | Official external client |
|-------|--------------|--------------------------|
| Delivery strategy | `remote_scheduled` | `remote_subscribed` |
| Classpath | Requires the Feign REST client starter | Requires the Feign REST client starter and the official external task client starter |
| Service-task delivery | Adapter polls `/external-task/fetchAndLock` on its own schedule | Camunda client keeps topic subscriptions open and receives tasks through its own worker runtime |
| Service-task completion | Uses `ExternalTaskApiClient` through the REST client | Uses `ExternalTaskClient` / `ExternalTaskService` from Camunda |
| YAML for engine URL | `feign.client.config.default.url` | `camunda.bpm.client.base-url` in addition to `feign.client.config.default.url` |
| Scope in this adapter | Used by the remote adapter's REST-based APIs, including scheduled service-task and user-task delivery | Used only by subscribed service-task delivery |

### Minimal YAML

#### Scheduled delivery with Feign

```yaml
dev:
  bpm-crafters:
    process-api:
      adapter:
        c7remote:
          enabled: true
          service-tasks:
            delivery-strategy: remote_scheduled
            worker-id: remote-worker
          user-tasks:
            delivery-strategy: remote_scheduled

feign:
  client:
    config:
      default:
        url: "http://localhost:9090/engine-rest/"
```

#### Subscribed delivery with the official external client

```yaml
dev:
  bpm-crafters:
    process-api:
      adapter:
        c7remote:
          enabled: true
          service-tasks:
            delivery-strategy: remote_subscribed
            worker-id: remote-worker
          user-tasks:
            delivery-strategy: remote_scheduled

feign:
  client:
    config:
      default:
        url: "http://localhost:9090/engine-rest/"

camunda:
  bpm:
    client:
      base-url: "http://localhost:9090/engine-rest/"
```

### Delivery strategies

#### User tasks

| Value | Effect |
|-------|--------|
| `remote_scheduled` | Polls user tasks over REST and enables user-task completion/modification beans. |
| `custom` | Disables the built-in recurring delivery. Provide your own delivery and matching user-task beans if needed. |
| `disabled` | Disables user-task delivery and configures a no-op user-task completion API. |

#### Service tasks

| Value | Effect |
|-------|--------|
| `remote_scheduled` | Polls external tasks over REST with the Feign-based client. |
| `remote_subscribed` | Uses the official Camunda external task client and topic subscriptions. |
| `custom` | Disables the built-in recurring delivery. Provide your own delivery and matching service-task completion bean if needed. |
| `disabled` | Disables service-task delivery and configures a no-op service-task completion API. |

### Important properties

#### `enabled`

Turns the remote adapter on or off. Default: `false`.

#### `service-tasks.*`

| Property | Default | Description |
|----------|---------|-------------|
| `worker-id` | required | Worker id used for external task fetching and completion. |
| `max-task-count` | `100` | Maximum number of external tasks fetched per pull cycle. |
| `lock-time-in-seconds` | `10` | Lock duration for fetched external tasks. |
| `retry-timeout-in-seconds` | `10` | Timeout used by the default failure retry supplier. |
| `retries` | `3` | Initial retry count used by the default failure retry supplier. |
| `delivery-strategy` | required | One of `remote_scheduled`, `remote_subscribed`, `custom`, `disabled`. |
| `schedule-delivery-fixed-rate-in-seconds` | `13` | Polling interval for `remote_scheduled`. |
| `execute-initial-pull-on-startup` | `true` in property binding | Enable one startup pull before the recurring scheduler or subscriber takes over. Set it explicitly when you want startup delivery. |
| `deserialize-on-server` | `false` | Requests Camunda REST variable deserialization on the server side for scheduled pulls. |
| `worker-thread-pool-size` | `10` | Thread pool size for scheduled service-task worker execution. |
| `worker-thread-pool-queue-capacity` | `50` | Queue capacity for scheduled service-task worker execution. |

#### `user-tasks.*`

| Property | Default | Description |
|----------|---------|-------------|
| `delivery-strategy` | required | One of `remote_scheduled`, `custom`, `disabled`. |
| `schedule-delivery-fixed-rate-in-seconds` | `5` | Polling interval for `remote_scheduled`. |
| `execute-initial-pull-on-startup` | `true` in property binding | Enable one startup pull before the recurring scheduler takes over. Set it explicitly when you want startup delivery. |
| `deserialize-on-server` | `false` | Requests Camunda REST variable deserialization on the server side for user-task polling. |

### Example with explicit scheduling settings

```yaml
dev:
  bpm-crafters:
    process-api:
      adapter:
        c7remote:
          enabled: true
          service-tasks:
            delivery-strategy: remote_scheduled
            schedule-delivery-fixed-rate-in-seconds: 5
            worker-id: remote-worker
            lock-time-in-seconds: 10
            deserialize-on-server: false
            worker-thread-pool-queue-capacity: 5
            worker-thread-pool-size: 2
            max-task-count: 2
            execute-initial-pull-on-startup: true
          user-tasks:
            delivery-strategy: remote_scheduled
            schedule-delivery-fixed-rate-in-seconds: 5
            execute-initial-pull-on-startup: false
            deserialize-on-server: false

feign:
  client:
    config:
      default:
        url: "http://localhost:9090/engine-rest/"
```

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
