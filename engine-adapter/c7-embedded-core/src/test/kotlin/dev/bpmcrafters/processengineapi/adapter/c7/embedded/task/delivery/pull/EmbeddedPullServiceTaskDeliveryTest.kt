package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.externaltask.LockedExternalTask
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class EmbeddedPullServiceTaskDeliveryTest {

  private val taskDelivery = EmbeddedPullServiceTaskDelivery(
    externalTaskService = mock(),
    subscriptionRepository = mock(),
    executor = mock(),
    lockDurationInSeconds = 30,
    workerId = "worker",
    maxTasks = 10,
    retryTimeoutInSeconds = 60,
    retries = 3,
    metrics = mock()
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
    val task: LockedExternalTask = mock()
    whenever(task.topicName).thenReturn("topic")

    with(taskDelivery) {
      assertThat(subscription.matches(task)).isTrue()
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
    val task: LockedExternalTask = mock()
    whenever(task.topicName).thenReturn("topic")

    with(taskDelivery) {
      assertThat(subscription.matches(task)).isFalse()
    }
  }
}
