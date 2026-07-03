package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.subscription.C7TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.TaskService
import org.camunda.bpm.engine.task.Task
import org.camunda.bpm.engine.task.TaskQuery
import org.camunda.community.mockito.QueryMocks
import org.camunda.community.mockito.task.TaskFake
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal class EmbeddedPullUserTaskDeliveryTest {

  private val keys = mutableMapOf("process-definition-id" to "process-definition-key")
  private val versionTags = mutableMapOf<String, String?>()
  private val taskService: TaskService = mock()
  private val subscriptionRepository: SubscriptionRepository = InMemSubscriptionRepository()

  private val taskCount = 100
  private val repeatCount = 100
  private val tasks: List<Task> = (0..<taskCount).map {
    randomTask()
  }


  private val embeddedPullUserTaskDelivery = EmbeddedPullUserTaskDelivery(
    taskService = taskService,
    subscriptionRepository = subscriptionRepository,
    processDefinitionMetaDataResolver = CachingProcessDefinitionMetaDataResolver(
      repositoryService = mock(),
      keys = keys,
      versionTags = versionTags
    ),
    executorService = Executors.newFixedThreadPool(3),
  )

  @BeforeEach
  fun setup() {
    assertThat(tasks).hasSize(taskCount)
    assertThat(tasks.map { it.id }.distinct()).hasSize(taskCount)
    subscriptionRepository.getTaskSubscriptions().forEach { subscriptionRepository.deleteTaskSubscription(it) }

    QueryMocks.mockTaskQuery(taskService).list(
      tasks
    )

  }

  @Test
  fun `deliver tasks directly`() {

    // given
    val deliveredDirectly = ConcurrentHashMap<String, TaskInformation>()
    val terminatedDirectly = ConcurrentHashMap<String, String>()

    subscriptionRepository.createTaskSubscription(
      TaskSubscriptionHandle(
        taskType = TaskType.USER,
        restrictions = mapOf(),
        taskDescriptionKey = null,
        payloadDescription = null,
        action = { taskInformation, _ -> synchronized(deliveredDirectly) { deliveredDirectly[taskInformation.taskId] = taskInformation } },
        termination = { taskInformation -> synchronized(terminatedDirectly) { terminatedDirectly[taskInformation.taskId] = taskInformation.taskId } }
      )
    )

    repeat(repeatCount) {
      embeddedPullUserTaskDelivery.refresh()
    }

    assertThat(terminatedDirectly).hasSize(0)
    assertThat(deliveredDirectly).hasSize(tasks.size)
  }

  @Test
  fun `deliver via user task support`() {
    val userTaskSupport = UserTaskSupport()

    val deliveredViaUserTaskSupport = ConcurrentHashMap<String, TaskInformation>()
    val terminatedViaUserTaskSupport = ConcurrentHashMap<String, String>()


    userTaskSupport.subscribe(taskSubscriptionApi = C7TaskSubscriptionApiImpl(subscriptionRepository), restrictions = mapOf(), payloadDescription = null)

    userTaskSupport.addHandler { taskInformation, _ ->
      synchronized(deliveredViaUserTaskSupport) {
        deliveredViaUserTaskSupport[taskInformation.taskId] = taskInformation
      }
    }
    userTaskSupport.addTerminationHandler { taskInformation ->
      synchronized(terminatedViaUserTaskSupport) {
        terminatedViaUserTaskSupport[taskInformation.taskId] = taskInformation.taskId
      }
    }

    repeat(repeatCount) {
      embeddedPullUserTaskDelivery.refresh()
    }

    assertThat(terminatedViaUserTaskSupport).hasSize(0)
    assertThat(deliveredViaUserTaskSupport).hasSize(tasks.size)
    assertThat(userTaskSupport.getAllTasks()).hasSize(tasks.size)
  }

  @Test
  fun `matches returns false for unknown restriction`() {
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.USER,
      restrictions = mapOf("unknown" to "value"),
      taskDescriptionKey = null,
      payloadDescription = null,
      action = { _, _ -> },
      termination = { }
    )
    val task = randomTask()

    with(embeddedPullUserTaskDelivery) {
      assertThat(subscription.matches(task)).isFalse()
    }
  }

  @Test
  fun `does not fail when task does not match any subscription`() {
    // given
    val deliveredDirectly = ConcurrentHashMap<String, TaskInformation>()

    subscriptionRepository.createTaskSubscription(
      TaskSubscriptionHandle(
        taskType = TaskType.USER,
        restrictions = mapOf(dev.bpmcrafters.processengineapi.CommonRestrictions.PROCESS_DEFINITION_KEY to "process-A"),
        taskDescriptionKey = null,
        payloadDescription = null,
        action = { taskInformation, _ -> synchronized(deliveredDirectly) { deliveredDirectly[taskInformation.taskId] = taskInformation } },
        termination = { }
      )
    )

    // We need at least one task in the system to ensure that it doesn't match the subscription
    assertThat(tasks).isNotEmpty

    // when / then (should NOT throw NPE)
    embeddedPullUserTaskDelivery.refresh()

    // then
    assertThat(deliveredDirectly).isEmpty()
  }

  @Test
  fun `failed downstream handler does not leak tasks into user task support`() {
    val leakingTaskCount = 70
    val taskQuery = mock<TaskQuery>()
    val firstRefreshTasks = tasks.take(leakingTaskCount)
    val userTaskSupport = UserTaskSupport()
    val terminatedTasks = ConcurrentHashMap<String, String>()
    val thrownTasks = AtomicInteger()

    whenever(taskService.createTaskQuery()).thenReturn(taskQuery)
    whenever(taskQuery.initializeFormKeys()).thenReturn(taskQuery)
    whenever(taskQuery.active()).thenReturn(taskQuery)
    whenever(taskQuery.list()).thenReturn(firstRefreshTasks, emptyList())

    userTaskSupport.subscribe(taskSubscriptionApi = C7TaskSubscriptionApiImpl(subscriptionRepository), restrictions = mapOf(), payloadDescription = null)
    userTaskSupport.addHandler { taskInformation, _ ->
      thrownTasks.incrementAndGet()
      throw IllegalStateException("boom for ${taskInformation.taskId}")
    }
    userTaskSupport.addTerminationHandler { taskInformation ->
      terminatedTasks[taskInformation.taskId] = taskInformation.taskId
    }

    embeddedPullUserTaskDelivery.refresh()
    repeat(3) {
      embeddedPullUserTaskDelivery.refresh()
    }

    assertThat(thrownTasks.get()).isEqualTo(leakingTaskCount)
    assertThat(userTaskSupport.getAllTasks()).isEmpty()
    assertThat(terminatedTasks).hasSize(leakingTaskCount)
  }

  private fun randomTask() = TaskFake
    .builder()
    .id(UUID.randomUUID().toString())
    .processDefinitionId("process-definition-id")
    .assignee("kermit")
    .name("Perform user task")
    .createTime(Date.from(Instant.now()))
    .lastUpdated(Date.from(Instant.now().plusSeconds(1)))
    .build()
}
