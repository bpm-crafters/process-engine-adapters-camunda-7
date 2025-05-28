package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class NoOpUserTaskCompletionApiImpl : UserTaskCompletionApi {

  override fun completeTask(cmd: CompleteTaskCmd): Future<Empty> {
    return CompletableFuture.completedFuture(Empty)
  }

  override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): Future<Empty> {
    return CompletableFuture.completedFuture(Empty)
  }
}
