package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.EngineCommand.*
import dev.bpmcrafters.processengineapi.task.*
import java.util.concurrent.CompletableFuture

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
