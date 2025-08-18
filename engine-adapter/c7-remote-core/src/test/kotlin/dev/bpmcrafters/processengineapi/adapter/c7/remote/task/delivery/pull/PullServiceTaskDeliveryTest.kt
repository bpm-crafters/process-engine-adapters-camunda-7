package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull

import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.DropReason.EXPIRED_WHILE_IN_QUEUE
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.DropReason.NO_MATCHING_SUBSCRIPTIONS
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.FetchAndLockSkipReason.NO_SUBSCRIPTIONS
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.FetchAndLockSkipReason.QUEUE_FULL
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskInformation.Companion.CREATE
import dev.bpmcrafters.processengineapi.task.TaskInformation.Companion.DELETE
import dev.bpmcrafters.processengineapi.task.TaskInformation.Companion.REASON
import dev.bpmcrafters.processengineapi.task.TaskType
import org.camunda.bpm.engine.variable.Variables
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.model.*
import org.camunda.community.rest.variables.ValueMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.lenient
import org.mockito.kotlin.*
import org.springframework.http.ResponseEntity
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID.randomUUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ThreadPoolExecutor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class PullServiceTaskDeliveryTest {

  private val externalTaskApiClient = mock<ExternalTaskApiClient>()

  private val workerId = "best-worker-in-town"

  private val subscriptionRepository = mock<SubscriptionRepository>()

  private val queue = mock<BlockingQueue<Runnable>>().apply {
    lenient().doReturn(3).whenever(this).remainingCapacity()
  }

  private val executor = mock<ThreadPoolExecutor>().apply {
    lenient().doReturn(this@PullServiceTaskDeliveryTest.queue).whenever(this).queue
  }

  private val valueMapper = mock<ValueMapper>()

  private val metrics = mock<PullServiceTaskDeliveryMetrics>()

  private val callableCaptor = argumentCaptor<Callable<Unit>>()

  private val taskInformationCaptor = argumentCaptor<TaskInformation>()

  private val durationCaptor = argumentCaptor<Duration>()

  private val taskDelivery = spy(PullServiceTaskDelivery(
    externalTaskApiClient = externalTaskApiClient,
    processDefinitionMetaDataResolver = mock<ProcessDefinitionMetaDataResolver>(),
    workerId = workerId,
    subscriptionRepository = subscriptionRepository,
    maxTasks = 2,
    lockDurationInSeconds = 10,
    retryTimeoutInSeconds = 10,
    retries = 1,
    executor = executor,
    valueMapper = valueMapper,
    deserializeOnServer = false,
    metrics = metrics,
  ))

  @BeforeEach
  fun setUp() {
    verify(metrics).registerExecutorThreadsUsedGauge(any())
    verify(metrics).registerExecutorQueueCapacityGauge(any())
    verify(executor).queue
    clearInvocations(executor)
    verify(metrics).registerStillLockedTasksGauge(any())
  }

  @Test
  fun `refresh cleans up before it starts something new`() {
    doNothing().whenever(taskDelivery).cleanUpTerminatedTasks()
    doNothing().whenever(taskDelivery).deliverNewTasks()

    taskDelivery.refresh()

    val inOrder = inOrder(taskDelivery)
    inOrder.verify(taskDelivery).cleanUpTerminatedTasks()
    inOrder.verify(taskDelivery).deliverNewTasks()
  }

  /**
   * This test makes sure that the correct parameters are used when querying for still running external tasks.
   */
  @Test
  fun `cleanUpTerminatedTasks queries still running tasks`() {
    doReturn(ResponseEntity.ok(mutableListOf<ExternalTaskDto>()))
      .whenever(externalTaskApiClient)
      .queryExternalTasks(anyOrNull(), anyOrNull(), any())
    doReturn(listOf<String>())
      .whenever(subscriptionRepository)
      .getDeliveredTaskIds(any())

    taskDelivery.cleanUpTerminatedTasks()

    verify(externalTaskApiClient).queryExternalTasks(
      null,
      null,
      ExternalTaskQueryDto()
        .workerId(workerId)
        .locked(true)
        .sorting(listOf(
          ExternalTaskQueryDtoSortingInner()
            .sortBy(ExternalTaskQueryDtoSortingInner.SortByEnum.CREATE_TIME)
            .sortOrder(ExternalTaskQueryDtoSortingInner.SortOrderEnum.ASC))
        )
    )
    verify(subscriptionRepository).getDeliveredTaskIds(TaskType.EXTERNAL)
  }

  @Test
  fun `cleanUpTerminatedTasks does not fail if queryExternalTasks returns an empty list`() {
    doReturn(ResponseEntity.ok(mutableListOf<ExternalTaskDto>()))
      .whenever(externalTaskApiClient)
      .queryExternalTasks(anyOrNull(), anyOrNull(), any())
    doReturn(listOf("1"))
      .whenever(subscriptionRepository)
      .getDeliveredTaskIds(any())

    taskDelivery.cleanUpTerminatedTasks()

    assertEquals(0, taskDelivery.stillLockedTasksGauge.get())
    verify(executor).submit(callableCaptor.capture())
    callableCaptor.singleValue
  }

  @Test
  fun `cleanUpTerminatedTasks does not fail if getDeliveredTaskIds returns an empty list`() {
    val task1 = mockExternalTask("1")
    doReturn(ResponseEntity.ok(mutableListOf(task1)))
      .whenever(externalTaskApiClient)
      .queryExternalTasks(anyOrNull(), anyOrNull(), any())
    doReturn(listOf<String>())
      .whenever(subscriptionRepository)
      .getDeliveredTaskIds(any())

    taskDelivery.cleanUpTerminatedTasks()

    assertEquals(1, taskDelivery.stillLockedTasksGauge.get())
    verify(executor, never()).submit(any())
  }

  @Test
  fun `cleanUpTerminatedTasks cleans up terminated tasks`() {
    val task1 = mockExternalTask("1")
    doReturn(ResponseEntity.ok(mutableListOf(task1)))
      .whenever(externalTaskApiClient)
      .queryExternalTasks(anyOrNull(), anyOrNull(), any())
    doReturn(listOf("1", "2", "3"))
      .whenever(subscriptionRepository)
      .getDeliveredTaskIds(any())
    doReturn(1).whenever(queue).remainingCapacity()
    val task2TerminationHandlerCallable = Callable {}
    doReturn(task2TerminationHandlerCallable).whenever(taskDelivery).createTaskTerminationHandlerCallable("2")

    taskDelivery.cleanUpTerminatedTasks()

    assertEquals(1, taskDelivery.stillLockedTasksGauge.get())
    verify(executor).queue
    verify(queue).remainingCapacity()
    verify(executor).submit(task2TerminationHandlerCallable)
    verifyNoMoreInteractions(executor)
  }

  @Test
  fun `taskTerminationHandlerCallable deactivates subscription for task`() {
    val taskSubscriptionHandle = mockTaskSubscriptionHandle()
    doReturn(taskSubscriptionHandle)
      .whenever(subscriptionRepository)
      .deactivateSubscriptionForTask("1")

    val callable = taskDelivery.createTaskTerminationHandlerCallable("1")
    callable.call()

    verify(taskSubscriptionHandle.termination).accept(taskInformationCaptor.capture())
    val taskInformation = taskInformationCaptor.singleValue
    assertEquals("1", taskInformation.taskId)
    assertEquals(mapOf(REASON to DELETE), taskInformation.meta)
    verify(metrics).incrementTerminatedTasksCounter(taskSubscriptionHandle.taskDescriptionKey!!)
  }

  /**
   * This test makes sure that the correct parameters are used when fetching external tasks.
   */
  @Test
  fun `deliverNewTasks fetches and locks tasks`() {
    val subscription = mockTaskSubscriptionHandle()
    doReturn(listOf(subscription))
      .whenever(subscriptionRepository)
      .getTaskSubscriptions()
    doReturn(ResponseEntity.ok(mutableListOf<ExternalTaskDto>()))
      .whenever(externalTaskApiClient)
      .fetchAndLock(any())

    taskDelivery.deliverNewTasks()

    verify(externalTaskApiClient)
      .fetchAndLock(
        FetchExternalTasksDto(workerId, 2)
          .topics(listOf(
            FetchExternalTaskTopicDto(subscription.taskDescriptionKey, 10_000)
              .deserializeValues(false)
          ))
          .usePriority(true)
          .sorting(mutableListOf(
            FetchExternalTasksDtoSortingInner()
              .sortBy(FetchExternalTasksDtoSortingInner.SortByEnum.CREATE_TIME)
              .sortOrder(FetchExternalTasksDtoSortingInner.SortOrderEnum.ASC)
          ))
      )
  }

  @Test
  fun `deliverNewTasks must not fetch tasks if there are no subscriptions`() {
    doReturn(listOf<TaskSubscriptionHandle>())
      .whenever(subscriptionRepository)
      .getTaskSubscriptions()

    taskDelivery.deliverNewTasks()

    verify(metrics).incrementFetchAndLockTasksSkippedCounter(NO_SUBSCRIPTIONS)
    verifyNoInteractions(externalTaskApiClient)
  }

  @Test
  fun `deliverNewTasks must not fetch tasks if the queue is full`() {
    doReturn(listOf(mockTaskSubscriptionHandle()))
      .whenever(subscriptionRepository)
      .getTaskSubscriptions()
    doReturn(0)
      .whenever(queue)
      .remainingCapacity()

    taskDelivery.deliverNewTasks()

    verify(metrics).incrementFetchAndLockTasksSkippedCounter(QUEUE_FULL)
    verifyNoInteractions(externalTaskApiClient)
  }

  @Test
  fun `deliverNewTasks submits fetched and locked tasks`() {
    val subscription = mockTaskSubscriptionHandle()
    doReturn(listOf(subscription))
      .whenever(subscriptionRepository)
      .getTaskSubscriptions()
    val task = mockLockedExternalTaskDto("1")
    doReturn(ResponseEntity.ok(mutableListOf(task)))
      .whenever(externalTaskApiClient)
      .fetchAndLock(any())
    doReturn(true)
      .whenever(taskDelivery)
      .matches(task, subscription)
    val taskActionHandlerCallable = Callable {}
    doReturn(taskActionHandlerCallable)
      .whenever(taskDelivery)
      .createTaskActionHandlerCallable(task, subscription)

    taskDelivery.deliverNewTasks()

    verify(executor).queue
    verify(metrics).incrementFetchedAndLockedTasksCounter(task.topicName, 1)
    verify(executor).submit(taskActionHandlerCallable)
    verifyNoMoreInteractions(executor)
  }

  @Test
  fun `deliverNewTasks drops fetched and locked tasks if it has no matching subscription`() {
    val subscription = mockTaskSubscriptionHandle()
    doReturn(listOf(subscription))
      .whenever(subscriptionRepository)
      .getTaskSubscriptions()
    val task = mockLockedExternalTaskDto("1")
    doReturn(ResponseEntity.ok(mutableListOf(task)))
      .whenever(externalTaskApiClient)
      .fetchAndLock(any())
    doReturn(false)
      .whenever(taskDelivery)
      .matches(task, subscription)

    taskDelivery.deliverNewTasks()

    verify(executor).queue
    verify(metrics).incrementDroppedTasksCounter(task.topicName, NO_MATCHING_SUBSCRIPTIONS)
    verifyNoMoreInteractions(executor)
  }

  @Test
  fun `taskActionHandlerCallable skips everything if now is passed lockExpirationTime`() {
    val lockedTask = mockLockedExternalTaskDto("1", OffsetDateTime.now().minusMinutes(5))
    val activeSubscription = mockTaskSubscriptionHandle()
    doNothing()
      .whenever(subscriptionRepository)
      .activateSubscriptionForTask("1", activeSubscription)

    val callable = taskDelivery.createTaskActionHandlerCallable(lockedTask, activeSubscription)
    callable.call()

    verify(metrics).incrementDroppedTasksCounter(lockedTask.topicName, EXPIRED_WHILE_IN_QUEUE)
    verifyNoInteractions(subscriptionRepository)
  }

  @Test
  fun `taskActionHandlerCallable activates subscription for task`() {
    val lockedTask = mockLockedExternalTaskDto("1", OffsetDateTime.now().plusSeconds(7))
    val activeSubscription = mockTaskSubscriptionHandle()
    doNothing()
      .whenever(subscriptionRepository)
      .activateSubscriptionForTask("1", activeSubscription)
    val variables = Variables.createVariables()
    lockedTask.variables.entries.forEach {
      variables[it.key] = it.value.value
    }
    doReturn(variables)
      .whenever(valueMapper)
      .mapDtos(lockedTask.variables)
    doReturn(mockTaskInformation("1"))
      .whenever(taskDelivery)
      .toTaskInformation(lockedTask)

    val callable = taskDelivery.createTaskActionHandlerCallable(lockedTask, activeSubscription)
    callable.call()

    verify(metrics).recordTaskQueueTime(eq(lockedTask.topicName), durationCaptor.capture())
    val queueTime = durationCaptor.singleValue.toMillis()
    assertTrue(3_000.rangeTo(4_000).contains(queueTime), "Expected queue time $queueTime to be between 3000 and 4000 ms")
    verify(activeSubscription.action).accept(taskInformationCaptor.capture(), eq(variables))
    val taskInformation = taskInformationCaptor.singleValue
    assertEquals("1", taskInformation.taskId)
    assertEquals(mapOf(REASON to CREATE), taskInformation.meta)
    verify(metrics).incrementCompletedTasksCounter(lockedTask.topicName)
    verify(metrics).recordTaskExecutionTime(eq(lockedTask.topicName), durationCaptor.capture())
    val executionTime = durationCaptor.secondValue.toMillis()
    assertTrue(executionTime >= 0, "Expected execution time to be >= 0")
  }

  @Test
  fun `taskActionHandlerCallable handles exceptions`() {
    val lockedTask = mockLockedExternalTaskDto("1")
    val activeSubscription = mockTaskSubscriptionHandle()

    val exception = RuntimeException("Something went wrong")
    doThrow(exception)
      .whenever(subscriptionRepository)
      .activateSubscriptionForTask("1", activeSubscription)

    val callable = taskDelivery.createTaskActionHandlerCallable(lockedTask, activeSubscription)
    callable.call()

    verifyNoInteractions(valueMapper)
    verify(metrics).incrementFailedTasksCounter(lockedTask.topicName)
    verify(externalTaskApiClient).handleFailure(
      lockedTask.id,
      ExternalTaskFailureDto().apply {
        workerId = this@PullServiceTaskDeliveryTest.workerId
        retries = 0
        retryTimeout = 10_000
        errorDetails = exception.stackTraceToString()
        errorMessage = exception.message
      }
    )
  }

  fun mockExternalTask(id: String): ExternalTaskDto = ExternalTaskDto().id(id)

  fun mockLockedExternalTaskDto(
    id: String,
    lockExpirationTime: OffsetDateTime = OffsetDateTime.now().plusMinutes(5)
  ): LockedExternalTaskDto = LockedExternalTaskDto()
    .id(id)
    .topicName("topical-but-not-tropical")
    .lockExpirationTime(lockExpirationTime)
    .variables(mapOf(
      // ...to have something (most likely) unique for argument matching.
      "variable" to VariableValueDto().value(randomUUID().toString()))
    )

  fun mockTaskSubscriptionHandle(): TaskSubscriptionHandle = TaskSubscriptionHandle(
    TaskType.EXTERNAL,
    setOf("variable"),
    mapOf(),
    "topical-but-not-tropical",
    mock(),
    mock()
  )

  fun mockTaskInformation(taskId: String): TaskInformation = TaskInformation(
    taskId = taskId,
    meta = emptyMap()
  )
}
