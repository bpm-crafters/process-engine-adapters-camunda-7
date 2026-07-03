package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion

import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.model.ExternalTaskFailureDto
import org.camunda.community.rest.variables.ValueMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Duration
import kotlin.test.assertEquals

internal class FeignServiceTaskCompletionApiImplTest {

  private val externalTaskApiClient = mock<ExternalTaskApiClient>()
  private val failureDtoCaptor = argumentCaptor<ExternalTaskFailureDto>()
  private val completionApi = FeignServiceTaskCompletionApiImpl(
    workerId = "worker",
    externalTaskApiClient = externalTaskApiClient,
    subscriptionRepository = mock<SubscriptionRepository>(),
    failureRetrySupplier = FixedFailureRetrySupplier,
    valueMapper = mock<ValueMapper>()
  )

  @Test
  fun `failTask writes provided retryBackoff in milliseconds to failure dto`() {
    completionApi.failTask(
      FailTaskCmd("task", "reason", "details", 2, Duration.ofSeconds(3))
    ).join()

    verify(externalTaskApiClient).handleFailure(eq("task"), failureDtoCaptor.capture())
    assertEquals(3_000, failureDtoCaptor.firstValue.retryTimeout)
  }

  @Test
  fun `failTask converts supplier retry timeout seconds to milliseconds in failure dto`() {
    completionApi.failTask(
      FailTaskCmd("task", "reason", "details", null, null)
    ).join()

    verify(externalTaskApiClient).handleFailure(eq("task"), failureDtoCaptor.capture())
    assertEquals(4, failureDtoCaptor.firstValue.retries)
    assertEquals(7_000, failureDtoCaptor.firstValue.retryTimeout)
  }

  private object FixedFailureRetrySupplier : FailureRetrySupplier {
    override fun apply(taskId: String) = FailureRetrySupplier.FailureRetry(
      retryCount = 4,
      retryTimeout = 7
    )
  }
}
