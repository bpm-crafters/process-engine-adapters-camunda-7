# Feature 283: Lifecycle-managed adapter delivery

## Goal

Align Camunda 7 adapter delivery with the Process Engine API Spring Boot lifecycle. Task subscriptions must be
registered before the adapter performs its initial pull or starts recurring polling. Recurring polling must stop as
the process application stops.

## Behavior

Both the remote and embedded Spring Boot starters use the named lifecycle phases supplied by
`process-engine-api-lifecycle-spring-boot`.

- Task subscriptions are registered during `SUBSCRIPTION_REGISTRATION`.
- Initial service-task and user-task pulls react to `RUNNING`.
- Initial pulls are synchronous by default. The `RUNNING` phase is reached only after enabled synchronous initial
  pulls return.
- Recurring scheduled delivery starts at `RUNNING`, after the initial-pull listeners have run.
- Recurring scheduled delivery is cancelled at `STOPPING`. In-flight pulls are allowed to finish and are not
  interrupted.

The starters no longer use Boot's generic application-start event, Camunda's embedded process-application-start
event, `@Async`, or `@Scheduled` for these delivery operations.

## Configuration

The existing `execute-initial-pull-on-startup` property still determines whether an initial pull is enabled. Its
name is retained for compatibility; the pull now occurs on entry to `RUNNING`.

Each task type has the additional property below. It defaults to `false`.

| Property | Meaning |
|----------|---------|
| `service-tasks.execute-initial-pull-async` | Run the initial service-task pull on the service-task worker executor instead of waiting while entering `RUNNING`. |
| `user-tasks.execute-initial-pull-async` | Run the initial user-task pull on the user-task worker executor instead of waiting while entering `RUNNING`. |

The properties are available under both adapter prefixes:

- `dev.bpm-crafters.process-api.adapter.c7remote`
- `dev.bpm-crafters.process-api.adapter.c7embedded`

Example:

```yaml
dev:
  bpm-crafters:
    process-api:
      adapter:
        c7remote:
          service-tasks:
            execute-initial-pull-on-startup: true
            execute-initial-pull-async: false
```

## Validation

- Remote and embedded Spring integration tests verify that scheduled delivery is installed when `RUNNING` is
  published and cancelled when `STOPPING` is published.
- Property tests verify that `execute-initial-pull-async` defaults to `false` for service and user tasks.
- The lifecycle bridge integration tests verify the ordered lifecycle phases and events.
