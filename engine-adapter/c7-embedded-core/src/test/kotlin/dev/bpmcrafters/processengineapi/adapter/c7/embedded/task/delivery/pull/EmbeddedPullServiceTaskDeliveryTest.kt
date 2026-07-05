package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.ExternalTaskService
import org.camunda.bpm.engine.externaltask.LockedExternalTask
import org.camunda.bpm.engine.variable.Variables
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class EmbeddedPullServiceTaskDeliveryTest {

  private val externalTaskService: ExternalTaskService = mock()
  private val taskDelivery = EmbeddedPullServiceTaskDelivery(
    externalTaskService = externalTaskService,
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
      restrictions = mapOf(CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS to "5000"),
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

  @Test
  fun `failed delivery cleans up local state via termination handler`() {
    val subscriptionRepository = mock<dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository>()
    val delivery = EmbeddedPullServiceTaskDelivery(
      externalTaskService = externalTaskService,
      subscriptionRepository = subscriptionRepository,
      executor = mock(),
      lockDurationInSeconds = 30,
      workerId = "worker",
      maxTasks = 10,
      retryTimeoutInSeconds = 60,
      retries = 3,
      metrics = mock()
    )
    val state = mutableMapOf<String, TaskInformation>()
    val terminated = mutableListOf<TaskInformation>()
    val task = mock<LockedExternalTask>()
    whenever(task.id).thenReturn("1")
    whenever(task.topicName).thenReturn("topic")
    whenever(task.lockExpirationTime).thenReturn(Date(System.currentTimeMillis() + 20_000))
    whenever(task.retries).thenReturn(2)
    whenever(task.variables).thenReturn(Variables.createVariables())
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      restrictions = mapOf(),
      taskDescriptionKey = "topic",
      payloadDescription = null,
      action = { taskInformation, _ ->
        state[taskInformation.taskId] = taskInformation
        throw RuntimeException("Something went wrong")
      },
      termination = { taskInformation ->
        state.remove(taskInformation.taskId)
        terminated.add(taskInformation)
      }
    )
    whenever(subscriptionRepository.deactivateSubscriptionForTask("1")).thenReturn(subscription)

    delivery.createTaskActionHandlerCallable(task, subscription).call()

    assertTrue(state.isEmpty())
    assertEquals(1, terminated.size)
    assertEquals(TaskInformation.DELETE, terminated.single().meta[TaskInformation.REASON])
    verify(externalTaskService).handleFailure("1", "worker", "Something went wrong", 1, 60_000)
    verify(subscriptionRepository).deactivateSubscriptionForTask("1")
  }
}
