package dev.bpmcrafters.processengineapi.adapter.c7.common.task

import dev.bpmcrafters.processengineapi.adapter.c7.common.threading.withThreadContextClassLoader
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.TaskInformation

fun executeExternalTaskCommand(
  taskId: String,
  subscriptions: SubscriptionRepository,
  isMissingTask: (Exception) -> Boolean,
  command: () -> Unit
) {
  try {
    command()
  } catch (failure: Exception) {
    if (!isMissingTask(failure)) throw failure

    val missing = ExternalTaskNotFoundException(taskId, failure)
    subscriptions.deactivateSubscriptionForTask(taskId)?.let { subscription ->
      try {
        withThreadContextClassLoader(subscription.termination) {
          subscription.termination.accept(TaskInformation(taskId, emptyMap()).withReason(TaskInformation.DELETE))
        }
      } catch (cleanupFailure: Exception) {
        missing.addSuppressed(cleanupFailure)
      }
    }
    throw missing
  }
}
