package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.subscribe

import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.client.task.ExternalTask
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class SubscribingServiceTaskDeliveryTest {

  private val taskDelivery = SubscribingServiceTaskDelivery(
    externalTaskClient = mock(),
    subscriptionRepository = mock(),
    lockDurationInSeconds = 30,
    retryTimeoutInSeconds = 60,
    retries = 3
  )

  @Test
  fun `matches handles workerLockDurationInMilliseconds`() {
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      restrictions = mapOf("workerLockDurationInMilliseconds" to "5000"),
      taskDescriptionKey = "topic",
      payloadDescription = null,
      action = { _, _ -> },
      termination = { }
    )
    val externalTask: ExternalTask = mock()
    whenever(externalTask.topicName).thenReturn("topic")

    with(taskDelivery) {
      assertThat(subscription.matches(externalTask)).isTrue()
    }
  }

  @Test
  fun `matches returns false for unknown restriction`() {
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      restrictions = mapOf("unknown" to "value"),
      taskDescriptionKey = "topic",
      payloadDescription = null,
      action = { _, _ -> },
      termination = { }
    )
    val externalTask: ExternalTask = mock()
    whenever(externalTask.topicName).thenReturn("topic")

    with(taskDelivery) {
      assertThat(subscription.matches(externalTask)).isFalse()
    }
  }
}
