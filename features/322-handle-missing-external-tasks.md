# Feature 322 Handle missing external tasks

## Goal

End the local delivery when Camunda confirms that an external task is gone. A worker may finish after a boundary event interrupts the task, the process instance is cancelled, or another worker completes the task after the original lock expires. In these cases a second call to report failure cannot update the task and can produce a misleading error log. [Issue #322](https://github.com/bpm-crafters/process-engine-adapters-camunda-7/issues/322)

## Proposed behavior

### Classify the response at the engine call

Translate a confirmed missing-task response from a Camunda service-task completion, BPMN error, or failure command into `ExternalTaskNotFoundException` in the C7 adapter's common module. The exception carries the addressed task ID and original cause. The adapter targets Process Engine API 1.8.

Apply the translation around the engine call for the current task in embedded delivery, remote polling through Feign, and remote subscriptions through the Camunda client. A business exception raised by the handler, including a 404 from another service, follows the normal failure path. Use a task-specific engine response to distinguish a missing task from other errors at the REST boundary.

### End the local delivery

Remove the active delivery by task ID and invoke its termination callback with reason `DELETE` once. Keep the subscription registered for future tasks. Complete the command's future exceptionally with the typed outcome so callers can see that the engine did not accept the command. If cleanup has already removed the delivery, the command still returns the same outcome.

### Skip the follow-up failure command

The three C7 delivery paths recognize the typed outcome for the current task through `ExecutionException` and `CompletionException` wrappers. The path that handles it logs one `DEBUG` line with the task ID and skips the follow-up `handleFailure` call. A missing-task response alone gives no evidence about why the task disappeared.

## Validation

- In each C7 delivery mode, let the engine remove a task before the handler awaits completion. Verify the typed outcome, a single termination callback with reason `DELETE`, no follow-up failure command, and a single `DEBUG` log for the missing task.
- Have the handler throw an unrelated 404. Verify that normal failure reporting and retry handling still run.
