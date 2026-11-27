package dev.bpmcrafters.processengineapi.adapter.c7.common.task

import dev.bpmcrafters.processengineapi.adapter.c7.common.threading.withThreadContextClassLoader
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Handles missing-task responses from external task commands and ends the local delivery.
 */
class ExternalTaskCommandExecutor(
  private val subscriptions: SubscriptionRepository,
  private val onMissingTask: (String) -> Unit,
  private val isMissingTask: (Exception, String) -> Boolean
) {
  constructor(
    subscriptions: SubscriptionRepository,
    isMissingTask: (Exception, String) -> Boolean
  ) : this(subscriptions, {}, isMissingTask)

  fun execute(taskId: String, command: () -> Unit) {
    try {
      command()
    } catch (failure: Exception) {
      if (!isMissingTask(failure, taskId)) throw failure

      logger.debug { "PROCESS-ENGINE-C7-COMMON-001: external task $taskId no longer exists." }
      val missing = ExternalTaskNotFoundException(taskId, failure)
      try {
        onMissingTask(taskId)
      } catch (cleanupFailure: Exception) {
        missing.addSuppressed(cleanupFailure)
      }
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
}
