package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.EngineCommand.*
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.task.*
import java.time.Duration
import java.util.concurrent.CompletableFuture

class OutboxServiceTaskCompletionApiImpl(
  private val delegate: ServiceTaskCompletionApi,
  private val dispatcher: TransactionalEngineCommandDispatcher,
  private val failureRetrySupplier: FailureRetrySupplier,
) : ServiceTaskCompletionApi {

  override fun completeTask(cmd: CompleteTaskCmd): CompletableFuture<Empty> =
    dispatcher.dispatch("completion of service task ${cmd.taskId}", { CompleteServiceTask(cmd.taskId, cmd.get()) }) {
      delegate.completeTask(cmd)
    }

  override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): CompletableFuture<Empty> =
    dispatcher.dispatch(
      "error of service task ${cmd.taskId}",
      { CompleteServiceTaskByError(cmd.taskId, cmd.errorCode, cmd.errorMessage, cmd.get()) }
    ) { delegate.completeTaskByError(cmd) }

  override fun failTask(cmd: FailTaskCmd): CompletableFuture<Empty> =
    dispatcher.dispatch("failure of service task ${cmd.taskId}", { toCommand(cmd) }) { delegate.failTask(cmd) }

  private fun toCommand(cmd: FailTaskCmd): FailServiceTask {
    val failureRetry by lazy { failureRetrySupplier.apply(cmd.taskId) }
    return FailServiceTask(
      taskId = cmd.taskId,
      reason = cmd.reason,
      errorDetails = cmd.errorDetails,
      retryCount = cmd.retryCount ?: failureRetry.retryCount,
      retryBackoff = cmd.retryBackoff ?: Duration.ofSeconds(failureRetry.retryTimeout),
    )
  }
}
