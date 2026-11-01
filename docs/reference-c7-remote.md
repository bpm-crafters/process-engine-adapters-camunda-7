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

### Jackson compatibility

The remote adapter starter supports both Jackson 2 and Jackson 3 for adapter-side serialization.

- Spring Boot 3 applications typically resolve to Jackson 2.
- Spring Boot 4 applications typically resolve to Jackson 3.
- The remote adapter does not require embedded Camunda Spin, so the embedded Spin limitation does not apply to the adapter runtime here.

That means Jackson 3 is a supported and expected choice for remote Spring Boot 4 applications. If the remote Camunda 7
engine itself uses Spin for JSON variables, that remains a concern of the remote engine runtime, not of the adapter
starter.

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

### Engine command outbox

Engine calls are REST calls and not atomic with your business transaction. With the outbox active, these calls write to
the outbox when made inside a transaction, and are delivered after commit, at least once:

- Service tasks: `completeTask`, `completeTaskByError`, `failTask`
- User tasks: `completeTask`, `completeTaskByError`
- `correlateMessage`
- `sendSignal`

Without a transaction they go straight to the engine and a warning is logged. `startProcess` is not covered because it
returns the process instance synchronously. User task modifications are not covered because they are order-sensitive.

Like the process-engine-worker `IdempotencyRegistry`, the outbox is off by default and replaceable by a bean:

| Setup | Result |
|-------|--------|
| nothing | Off, no behaviour change. |
| `outbox.enabled=true` | JDBC outbox on the application `DataSource`, delivered by the adapter relay. |
| own `EngineCommandOutbox` bean | Your outbox is used, and you run the delivery. |
| `outbox.enabled=false` | Off, even with an own bean. |

To use an existing outbox, implement `EngineCommandOutbox` and store the `EngineCommand` in the transaction of the
caller. `pendingTaskIds` must report undelivered task commands. Your relay passes each command to the
`EngineCommandDelivery` bean, which returns `DELIVERED`, `UNAVAILABLE` (retry without limit) or `REJECTED` (retry a
limited number of times).

The outbox decorators are `@Primary`. Injecting an adapter API by its `c7remote-*` qualifier bypasses the outbox.

| Property | Default | Description |
|----------|---------|-------------|
| `outbox.enabled` | unset | See above. |
| `outbox.relay-interval` | `1s` | Delay between relay runs and first retry backoff. |
| `outbox.batch-size` | `100` | Maximum commands delivered per relay run. |
| `outbox.lease-duration` | `60s` | Reservation of a claimed command. Must exceed the REST client timeout. |
| `outbox.max-attempts` | `20` | Engine error answers until a command becomes `DEAD`. |
| `outbox.deserialization-filter` | empty | `ObjectInputFilter` pattern for stored commands. Must allow the `EngineCommand` classes. |

The JDBC outbox needs `spring-jdbc` and this table:

```sql
-- Postgres; for H2 use BLOB instead of BYTEA
CREATE TABLE c7remote_engine_outbox (
  id              VARCHAR(36) PRIMARY KEY,
  type            VARCHAR(32) NOT NULL,
  task_id         VARCHAR(64),
  payload         BYTEA NOT NULL,
  status          VARCHAR(16) NOT NULL,
  attempts        INT NOT NULL,
  rejections      INT NOT NULL,
  next_attempt_at TIMESTAMP NOT NULL,
  lease_until     TIMESTAMP,
  last_error      VARCHAR(4000)
);
CREATE INDEX c7remote_engine_outbox_due ON c7remote_engine_outbox (status, next_attempt_at);
CREATE INDEX c7remote_engine_outbox_task ON c7remote_engine_outbox (task_id);
```

| Engine answer | Result |
|---------------|--------|
| 404 on a task command | Counts as delivered. |
| No connection, 401, 403, 404 otherwise, 502, 503, 504 | Retry without limit, capped at 5 minutes. |
| Any other failure, including 400, 500 and local mapping errors | Retry up to `max-attempts`, then `DEAD`. |

Retries with backoff also cover a message whose receiver is not ready yet. A missing user task answers
`POST /task/{id}/complete` with 500, so it ends as `DEAD` instead of being dropped.

Requirements:

- With process-engine-worker, set `completeTasksBeforeCommit=true` or use `Completion.BEFORE_COMMIT`. The default
  completes after commit, outside a transaction, so the outbox is bypassed with a warning.
- Do not call these APIs from an after-commit callback. The transaction still looks active there, so the entry would
  be written to a finished transaction and lost.
- All instances must share a stable `worker-id`. The engine rejects other ids with HTTP 400.
- The table must be in the business data source, otherwise atomicity is silently lost.
- Payload values must be `java.io.Serializable`.

Limits:

- A completed future and the termination callback mean handed to the outbox, not acknowledged by the engine.
- Commands have no order, also not within one transaction.
- A lost response or an expired lease can deliver a command twice. For task commands this is harmless. A message can
  hit another receiver or start a second instance, and a signal reaches all receivers again, so these must tolerate
  duplicates.
- `failTask` retries are fixed at enqueue, so a rolled back transaction may use up one count.
- A `DEAD` task is not pending, so the engine redelivers it. `DEAD` messages and signals are not recovered. Check
  `last_error` and replay after fixing the cause:

```sql
UPDATE c7remote_engine_outbox
SET status = 'PENDING', rejections = 0, next_attempt_at = CURRENT_TIMESTAMP
WHERE status = 'DEAD' AND id = '<id>';
```

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
