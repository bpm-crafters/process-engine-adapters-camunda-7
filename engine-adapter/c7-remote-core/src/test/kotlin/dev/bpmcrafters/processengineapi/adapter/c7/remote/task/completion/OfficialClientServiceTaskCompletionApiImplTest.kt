package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion

import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Duration
import org.camunda.bpm.client.task.ExternalTaskService as ClientExternalTaskService

internal class OfficialClientServiceTaskCompletionApiImplTest {

  private val externalTaskService = mock<ClientExternalTaskService>()
  private val completionApi = OfficialClientServiceTaskCompletionApiImpl(
    externalTaskService = externalTaskService,
    subscriptionRepository = mock<SubscriptionRepository>(),
    failureRetrySupplier = FixedFailureRetrySupplier
  )

  @Test
  fun `failTask passes provided retryBackoff in milliseconds to official client`() {
    completionApi.failTask(
      FailTaskCmd("task", "reason", "details", 2, Duration.ofSeconds(3))
    ).join()

    verify(externalTaskService).handleFailure("task", "reason", "details", 2, 3_000)
  }

  @Test
  fun `failTask converts supplier retry timeout seconds to milliseconds for official client`() {
    completionApi.failTask(
      FailTaskCmd("task", "reason", "details", null, null)
    ).join()

    verify(externalTaskService).handleFailure("task", "reason", "details", 4, 7_000)
  }

  private object FixedFailureRetrySupplier : FailureRetrySupplier {
    override fun apply(taskId: String) = FailureRetrySupplier.FailureRetry(
      retryCount = 4,
      retryTimeout = 7
    )
  }
}
