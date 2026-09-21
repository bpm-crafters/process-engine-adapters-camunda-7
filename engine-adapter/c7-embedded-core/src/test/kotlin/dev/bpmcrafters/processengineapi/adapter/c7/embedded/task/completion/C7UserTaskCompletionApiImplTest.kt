package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd
import org.camunda.bpm.engine.TaskService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.Executor

internal class C7UserTaskCompletionApiImplTest {

  private val taskService = mock<TaskService>()
  private val completionApi = C7UserTaskCompletionApiImpl(
    taskService = taskService,
    subscriptionRepository = mock<SubscriptionRepository>(),
    commandExecutor = EngineCommandExecutor(Executor { it.run() })
  )

  @Test
  fun `completeTaskByError passes error code, message and payload to the engine`() {
    completionApi.completeTaskByError(
      CompleteTaskByErrorCmd(
        taskId = "task",
        errorCode = "REJECTED",
        errorMessage = "Document incomplete",
        payloadSupplier = { mapOf("rejectionReason" to "missing signature") }
      )
    ).join()

    verify(taskService).handleBpmnError(
      "task",
      "REJECTED",
      "Document incomplete",
      mapOf("rejectionReason" to "missing signature")
    )
  }
}
