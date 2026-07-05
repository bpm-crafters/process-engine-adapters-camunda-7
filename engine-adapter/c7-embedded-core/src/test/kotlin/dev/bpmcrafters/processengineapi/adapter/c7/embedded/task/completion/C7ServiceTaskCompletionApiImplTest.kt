package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import org.camunda.bpm.engine.ExternalTaskService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Duration
import java.util.concurrent.Executor

internal class C7ServiceTaskCompletionApiImplTest {

  private val externalTaskService = mock<ExternalTaskService>()
  private val completionApi = C7ServiceTaskCompletionApiImpl(
    workerId = "worker",
    externalTaskService = externalTaskService,
    subscriptionRepository = mock<SubscriptionRepository>(),
    failureRetrySupplier = FixedFailureRetrySupplier,
    commandExecutor = EngineCommandExecutor(Executor { it.run() })
  )

  @Test
  fun `failTask passes provided retryBackoff in milliseconds to camunda engine`() {
    completionApi.failTask(
      FailTaskCmd("task", "reason", "details", 2, Duration.ofSeconds(3))
    ).join()

    verify(externalTaskService).handleFailure("task", "worker", "reason", "details", 2, 3_000)
  }

  @Test
  fun `failTask converts supplier retry timeout seconds to milliseconds`() {
    completionApi.failTask(
      FailTaskCmd("task", "reason", "details", null, null)
    ).join()

    verify(externalTaskService).handleFailure("task", "worker", "reason", "details", 4, 7_000)
  }

  private object FixedFailureRetrySupplier : FailureRetrySupplier {
    override fun apply(taskId: String) = FailureRetrySupplier.FailureRetry(
      retryCount = 4,
      retryTimeout = 7
    )
  }
}
