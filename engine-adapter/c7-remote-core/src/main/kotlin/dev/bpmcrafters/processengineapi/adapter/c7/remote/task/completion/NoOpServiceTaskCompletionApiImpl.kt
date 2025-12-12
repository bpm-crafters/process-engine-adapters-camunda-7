package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import java.util.concurrent.CompletableFuture

class NoOpServiceTaskCompletionApiImpl : ServiceTaskCompletionApi {
  override fun completeTask(cmd: CompleteTaskCmd): CompletableFuture<Empty> {
    return CompletableFuture.completedFuture(Empty)
  }

  override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): CompletableFuture<Empty> {
    return CompletableFuture.completedFuture(Empty)
  }

  override fun failTask(cmd: FailTaskCmd): CompletableFuture<Empty> {
    return CompletableFuture.completedFuture(Empty)
  }
}
