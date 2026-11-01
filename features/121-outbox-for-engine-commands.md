# Feature 121: Outbox for engine commands

Opt-in, C7 remote only. Engine calls made inside a business transaction are stored in it and delivered after commit,
at least once. Covers service task completion, error and failure, user task completion and error, messages and signals.

Decisions:
- Off by default. `outbox.enabled=true` provides the JDBC outbox; an own `EngineCommandOutbox` bean replaces it, as with
  the process-engine-worker `IdempotencyRegistry`; `false` disables both.
- `startProcess` is excluded because it returns the instance; user task modification because it is order-sensitive.
- Leases instead of `SKIP LOCKED`: no transaction around REST. No ordering.
- Engine error answers are limited, as 500 also means a missing user task. Only unavailability retries forever.
- `failTask` retries are resolved at enqueue, as the supplier counts down on each call.

Usage and limits: `docs/reference-c7-remote.md`.
