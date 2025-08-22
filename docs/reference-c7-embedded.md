---
title: Process Engine Adapter C7 Embedded
---

# Decisions and supported features

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
