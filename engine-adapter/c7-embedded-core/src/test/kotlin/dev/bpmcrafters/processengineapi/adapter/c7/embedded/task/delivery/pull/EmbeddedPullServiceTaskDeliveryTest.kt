package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.common.task.ExternalTaskNotFoundException
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion.C7ServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import dev.bpmcrafters.processengineapi.test.assertMissingTaskLoggedOnce
import dev.bpmcrafters.processengineapi.test.captureAdapterLogs
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.ExternalTaskService
import org.camunda.bpm.engine.exception.NotFoundException
import org.camunda.bpm.engine.externaltask.LockedExternalTask
import org.camunda.bpm.engine.variable.Variables
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.ThreadPoolExecutor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class EmbeddedPullServiceTaskDeliveryTest {

  private val externalTaskService: ExternalTaskService = mock()
  private val queue: BlockingQueue<Runnable> = mock()
  private val executor: ThreadPoolExecutor = mock()

  init {
    whenever(queue.remainingCapacity()).thenReturn(10)
    whenever(executor.queue).thenReturn(queue)
  }

  private val subscriptionRepository = InMemSubscriptionRepository()

  private val taskDelivery = EmbeddedPullServiceTaskDelivery(
    externalTaskService = externalTaskService,
    subscriptionRepository = subscriptionRepository,
    executor = executor,
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
  fun `matches a task without tenant when requested`() {
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      restrictions = mapOf(CommonRestrictions.WITHOUT_TENANT_ID to "true"),
      taskDescriptionKey = "topic",
      payloadDescription = null,
      action = { _, _ -> },
      termination = { }
    )
    val task: LockedExternalTask = mock()
    whenever(task.topicName).thenReturn("topic")
    whenever(task.tenantId).thenReturn(null)

    with(taskDelivery) {
      assertThat(subscription.matches(task)).isTrue()
    }
  }

  @Test
  fun `failed delivery cleans up local state via termination handler`() {
    val subscriptionRepository = mock<dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository>()
    val delivery = EmbeddedPullServiceTaskDelivery(
      externalTaskService = externalTaskService,
      subscriptionRepository = subscriptionRepository,
      executor = executor,
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

  @Test
  fun `failed delivery cleans up local state even if reporting the failure fails`() {
    val taskIdsPassedToTerminationHandler = mutableListOf<String>()
    val subscription = subscriptionThrowing(RuntimeException("Something went wrong"), taskIdsPassedToTerminationHandler)
    doThrow(IllegalStateException("database unavailable"))
      .whenever(externalTaskService).handleFailure(any(), any(), anyOrNull(), any(), any())

    taskDelivery.createTaskActionHandlerCallable(lockedTask(), subscription).call()

    assertThat(taskIdsPassedToTerminationHandler).containsExactly("1")
    assertThat(subscriptionRepository.getActiveSubscriptionForTask("1")).isNull()
  }

  @Test
  fun `error thrown by the handler is reported as failure`() {
    val taskIdsPassedToTerminationHandler = mutableListOf<String>()
    val subscription = subscriptionThrowing(NoClassDefFoundError("missing class"), taskIdsPassedToTerminationHandler)

    taskDelivery.createTaskActionHandlerCallable(lockedTask(), subscription).call()

    verify(externalTaskService).handleFailure("1", "worker", "missing class", 1, 60_000)
    assertThat(taskIdsPassedToTerminationHandler).containsExactly("1")
  }

  @Test
  fun `missing task wrapped by the handler ends delivery without reporting failure`() {
    val missing = NotFoundException("Cannot find external task with id '1'")
    doThrow(missing).whenever(externalTaskService).complete(eq("1"), eq("worker"), any())
    val completionApi = C7ServiceTaskCompletionApiImpl(
      "worker", externalTaskService, subscriptionRepository, mock(), EngineCommandExecutor { it.run() }
    )
    val terminated = mutableListOf<TaskInformation>()
    lateinit var completionFailure: Throwable
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      restrictions = emptyMap(),
      taskDescriptionKey = "topic",
      payloadDescription = null,
      action = { task, _ ->
        val failure = assertThrows<ExecutionException> {
          completionApi.completeTask(CompleteTaskCmd(task.taskId) { emptyMap() }).get()
        }
        completionFailure = requireNotNull(failure.cause)
        throw RuntimeException(failure)
      },
      termination = { terminated.add(it) }
    )
    subscriptionRepository.createTaskSubscription(subscription)

    val logs = captureAdapterLogs {
      taskDelivery.createTaskActionHandlerCallable(lockedTask(), subscription).call()
    }

    assertAll(
      { assertMissingTaskLoggedOnce(logs, "1") },
      { verify(externalTaskService, never()).handleFailure(eq("1"), any(), anyOrNull(), any(), any()) },
      { assertThat(completionFailure).isInstanceOfSatisfying(ExternalTaskNotFoundException::class.java) {
          assertThat(it.taskId).isEqualTo("1")
        } },
      { assertThat(completionFailure.cause).isSameAs(missing) },
      { assertThat(terminated.map { it.taskId to it.meta[TaskInformation.REASON] })
          .containsExactly("1" to TaskInformation.DELETE) },
      { assertThat(subscriptionRepository.getActiveSubscriptionForTask("1")).isNull() },
      { assertThat(subscriptionRepository.getTaskSubscriptions()).containsExactly(subscription) }
    )
  }

  private fun subscriptionThrowing(throwable: Throwable, taskIdsPassedToTerminationHandler: MutableList<String>) = TaskSubscriptionHandle(
    taskType = TaskType.EXTERNAL,
    restrictions = mapOf(),
    taskDescriptionKey = "topic",
    payloadDescription = null,
    action = { _, _ -> throw throwable },
    termination = { taskIdsPassedToTerminationHandler.add(it.taskId) }
  )

  private fun lockedTask(): LockedExternalTask = mock {
    on { id } doReturn "1"
    on { topicName } doReturn "topic"
    on { lockExpirationTime } doReturn Date(System.currentTimeMillis() + 20_000)
    on { retries } doReturn 2
    on { variables } doReturn Variables.createVariables()
  }
}
