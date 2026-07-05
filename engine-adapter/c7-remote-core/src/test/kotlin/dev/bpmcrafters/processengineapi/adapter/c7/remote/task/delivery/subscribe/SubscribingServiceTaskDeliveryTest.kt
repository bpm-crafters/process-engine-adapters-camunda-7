package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.subscribe

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.client.ExternalTaskClient
import org.camunda.bpm.client.task.ExternalTaskHandler
import org.camunda.bpm.client.task.ExternalTask
import org.camunda.bpm.client.task.ExternalTaskService
import org.camunda.bpm.client.topic.TopicSubscriptionBuilder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SubscribingServiceTaskDeliveryTest {

  private val externalTaskClient: ExternalTaskClient = mock()
  private val subscriptionRepository = mock<dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository>()
  private val taskDelivery = SubscribingServiceTaskDelivery(
    externalTaskClient = externalTaskClient,
    subscriptionRepository = subscriptionRepository,
    lockDurationInSeconds = 30,
    retryTimeoutInSeconds = 60,
    retries = 3
  )

  @Test
  fun `matches handles workerLockDurationInMilliseconds`() {
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      restrictions = mapOf(CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS to "5000"),
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

  @Test
  fun `subscribe cleans up local state when handler throws`() {
    val state = mutableMapOf<String, TaskInformation>()
    val terminated = mutableListOf<TaskInformation>()
    val taskHandler = AtomicReference<ExternalTaskHandler>()
    val builder = mock<TopicSubscriptionBuilder>()
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      restrictions = mapOf(),
      taskDescriptionKey = "topic",
      payloadDescription = null,
      action = { taskInformation, _ ->
        state[taskInformation.taskId] = taskInformation
        throw RuntimeException("boom")
      },
      termination = { taskInformation ->
        state.remove(taskInformation.taskId)
        terminated.add(taskInformation)
      }
    )
    val externalTask = mock<ExternalTask>()
    val externalTaskService = mock<ExternalTaskService>()

    whenever(subscriptionRepository.getTaskSubscriptions()).thenReturn(listOf(subscription))
    whenever(externalTaskClient.subscribe("topic")).thenReturn(builder)
    whenever(builder.lockDuration(any())).thenReturn(builder)
    whenever(builder.handler(any())).thenAnswer {
      taskHandler.set(it.getArgument(0))
      builder
    }
    whenever(builder.open()).thenReturn(mock())
    whenever(externalTask.topicName).thenReturn("topic")
    whenever(externalTask.id).thenReturn("1")
    whenever(externalTask.retries).thenReturn(2)
    whenever(externalTask.allVariables).thenReturn(mapOf())
    whenever(subscriptionRepository.deactivateSubscriptionForTask("1")).thenReturn(subscription)

    taskDelivery.subscribe()
    taskHandler.get().execute(externalTask, externalTaskService)

    assertTrue(state.isEmpty())
    assertEquals(1, terminated.size)
    assertEquals(TaskInformation.DELETE, terminated.single().meta[TaskInformation.REASON])
    verify(externalTaskService).handleFailure("1", "Error delivering external task", "boom", 1, 60_000)
    verify(subscriptionRepository).deactivateSubscriptionForTask("1")
  }
}
