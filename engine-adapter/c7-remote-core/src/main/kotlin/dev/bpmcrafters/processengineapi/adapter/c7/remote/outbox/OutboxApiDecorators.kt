package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.EngineCommand.*
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SendSignalCmd
import dev.bpmcrafters.processengineapi.correlation.SignalApi
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

class OutboxUserTaskCompletionApiImpl(
  private val delegate: UserTaskCompletionApi,
  private val dispatcher: TransactionalEngineCommandDispatcher,
) : UserTaskCompletionApi {

  override fun completeTask(cmd: CompleteTaskCmd): CompletableFuture<Empty> =
    dispatcher.dispatch("completion of user task ${cmd.taskId}", { CompleteUserTask(cmd.taskId, cmd.get()) }) {
      delegate.completeTask(cmd)
    }

  override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): CompletableFuture<Empty> =
    dispatcher.dispatch(
      "error of user task ${cmd.taskId}",
      { CompleteUserTaskByError(cmd.taskId, cmd.errorCode, cmd.errorMessage, cmd.get()) }
    ) { delegate.completeTaskByError(cmd) }
}

class OutboxCorrelationApiImpl(
  private val delegate: CorrelationApi,
  private val dispatcher: TransactionalEngineCommandDispatcher,
) : CorrelationApi by delegate {

  override fun correlateMessage(cmd: CorrelateMessageCmd): CompletableFuture<Empty> =
    dispatcher.dispatch("correlation of message ${cmd.messageName}", {
      val correlation = cmd.correlation.get()
      CorrelateMessage(
        messageName = cmd.messageName,
        correlationKey = correlation.correlationKey,
        correlationVariable = correlation.correlationVariable,
        restrictions = delegate.ensureSupported(cmd.restrictions),
        payload = cmd.get(),
      )
    }) { delegate.correlateMessage(cmd) }
}

class OutboxSignalApiImpl(
  private val delegate: SignalApi,
  private val dispatcher: TransactionalEngineCommandDispatcher,
) : SignalApi by delegate {

  override fun sendSignal(cmd: SendSignalCmd): CompletableFuture<Empty> =
    dispatcher.dispatch("signal ${cmd.signalName}", {
      SendSignal(cmd.signalName, delegate.ensureSupported(cmd.restrictions), cmd.get())
    }) { delegate.sendSignal(cmd) }
}
