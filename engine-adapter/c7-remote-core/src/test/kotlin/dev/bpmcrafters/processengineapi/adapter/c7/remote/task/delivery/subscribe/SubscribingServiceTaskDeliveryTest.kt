package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.subscribe

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.common.task.ExternalTaskNotFoundException
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.OfficialClientServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskType
import dev.bpmcrafters.processengineapi.test.assertMissingTaskLoggedOnce
import dev.bpmcrafters.processengineapi.test.captureAdapterLogs
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.client.ExternalTaskClient
import org.camunda.bpm.client.exception.NotFoundException
import org.camunda.bpm.client.exception.RestException
import org.camunda.bpm.client.task.ExternalTaskHandler
import org.camunda.bpm.client.task.ExternalTask
import org.camunda.bpm.client.task.ExternalTaskService
import org.camunda.bpm.client.topic.TopicSubscriptionBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ExecutionException
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

  @Test
  fun `missing task from official client completion ends delivery without reporting failure`() {
    val restFailure = RestException("External task with id 1 does not exist", "RestException", 0)
      .apply { httpStatusCode = 404 }
    val missing = NotFoundException("External task with id 1 does not exist", restFailure)
    val externalTaskService = mock<ExternalTaskService>()
    doThrow(missing).whenever(externalTaskService).complete(eq("1"), any(), any())
    val repository = InMemSubscriptionRepository()
    val completionApi = OfficialClientServiceTaskCompletionApiImpl(externalTaskService, repository, mock())
    val terminated = mutableListOf<TaskInformation>()
    lateinit var completionFailure: Throwable
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      restrictions = emptyMap(),
      taskDescriptionKey = "topic",
      payloadDescription = null,
      action = { delivered, _ ->
        val failure = assertThrows<ExecutionException> {
          completionApi.completeTask(CompleteTaskCmd(delivered.taskId) { emptyMap() }).get()
        }
        completionFailure = requireNotNull(failure.cause)
        throw failure
      },
      termination = { terminated.add(it) }
    )
    repository.createTaskSubscription(subscription)
    val builder = mock<TopicSubscriptionBuilder>(defaultAnswer = org.mockito.Answers.RETURNS_SELF)
    val handler = org.mockito.kotlin.argumentCaptor<ExternalTaskHandler>()
    whenever(externalTaskClient.subscribe("topic")).thenReturn(builder)
    whenever(builder.open()).thenReturn(mock())
    val task = mock<ExternalTask> {
      on { id }.thenReturn("1")
      on { topicName }.thenReturn("topic")
      on { retries }.thenReturn(2)
      on { allVariables }.thenReturn(emptyMap())
    }
    SubscribingServiceTaskDelivery(externalTaskClient, repository, 30, 60, 3).subscribe()
    verify(builder).handler(handler.capture())

    val logs = captureAdapterLogs {
      handler.singleValue.execute(task, externalTaskService)
    }

    assertAll(
      { assertMissingTaskLoggedOnce(logs, "1") },
      { verify(externalTaskService, never()).handleFailure(eq("1"), anyOrNull(), anyOrNull(), any(), any()) },
      { assertThat(completionFailure).isInstanceOfSatisfying(ExternalTaskNotFoundException::class.java) {
          assertThat(it.taskId).isEqualTo("1")
        } },
      { assertThat(completionFailure.cause).isSameAs(missing) },
      { assertThat(terminated.map { it.taskId to it.meta[TaskInformation.REASON] })
          .containsExactly("1" to TaskInformation.DELETE) },
      { assertThat(repository.getActiveSubscriptionForTask("1")).isNull() },
      { assertThat(repository.getTaskSubscriptions()).containsExactly(subscription) }
    )
  }
}
