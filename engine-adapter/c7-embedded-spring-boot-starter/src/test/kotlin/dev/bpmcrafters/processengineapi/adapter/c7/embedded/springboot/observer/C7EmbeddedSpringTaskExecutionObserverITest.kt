package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.observer

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedTestApplication
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskExecutionObserver
import dev.bpmcrafters.processengineapi.task.TaskExecutionOutcome
import dev.bpmcrafters.processengineapi.task.TaskExecutionCleanup
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.TaskType
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.camunda.bpm.engine.ExternalTaskService
import org.camunda.bpm.engine.RepositoryService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.Order
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
  classes = [C7EmbeddedTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(C7EmbeddedSpringTaskExecutionObserverITest.ObserverConfiguration::class)
@ActiveProfiles("itest")
@DirtiesContext
class C7EmbeddedSpringTaskExecutionObserverITest {

  companion object {
    const val KEY = "simple-process"
    const val BPMN = "bpmn/$KEY.bpmn"
    const val EXTERNAL_TASK = "execute-action-external"
    const val SERVICE_TASK = "service-do-action1"
    const val USER_TASK = "user-perform-task"
    const val HANDLER_MODE = "handlerMode"
  }

  data class TraceEntry(val event: String, val taskId: String, val taskType: TaskType)

  /**
   * Records observed events per task. Tasks of earlier tests may still be delivered, so tests read the events of their own process instance.
   */
  class Trace {
    private val entries = CopyOnWriteArrayList<TraceEntry>()
    private val processInstanceIdByTaskId = ConcurrentHashMap<String, String>()

    fun recordStart(task: TaskInformation, taskType: TaskType, event: String) {
      task.meta[CommonRestrictions.PROCESS_INSTANCE_ID]?.let { processInstanceIdByTaskId[task.taskId] = it }
      record(event, task.taskId, taskType)
    }

    fun record(event: String, taskId: String, taskType: TaskType) {
      entries += TraceEntry(event, taskId, taskType)
    }

    fun of(processInstanceId: String, taskType: TaskType): List<TraceEntry> =
      entries.filter { it.taskType == taskType && processInstanceIdByTaskId[it.taskId] == processInstanceId }

    fun clear() {
      entries.clear()
      processInstanceIdByTaskId.clear()
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  class ObserverConfiguration {

    @Bean
    fun trace() = Trace()

    @Bean
    @Order(1)
    fun tracingObserver(trace: Trace) = object : TaskExecutionObserver {
      override fun onStart(task: TaskInformation, taskType: TaskType): TaskExecutionCleanup {
        val detail = when (taskType) {
          TaskType.EXTERNAL -> task.meta[CommonRestrictions.ACTIVITY_INSTANCE_ID]
          TaskType.USER -> task.meta[TaskInformation.REASON]
        }
        trace.recordStart(task, taskType, "start:$detail")
        return TaskExecutionCleanup { trace.record("close", task.taskId, taskType) }
      }

      override fun onOutcome(taskId: String, taskType: TaskType, outcome: TaskExecutionOutcome) {
        trace.record("outcome:${outcome::class.simpleName}", taskId, taskType)
      }

      override fun onTermination(task: TaskInformation, taskType: TaskType) {
        trace.record("termination:${task.meta[TaskInformation.REASON]}", task.taskId, taskType)
      }
    }

    @Bean
    @Order(2)
    fun throwingObserver() = object : TaskExecutionObserver {
      override fun onStart(task: TaskInformation, taskType: TaskType): TaskExecutionCleanup = throw IllegalStateException("start failed")
      override fun onOutcome(taskId: String, taskType: TaskType, outcome: TaskExecutionOutcome) = throw IllegalStateException("outcome failed")
      override fun onTermination(task: TaskInformation, taskType: TaskType) = throw IllegalStateException("termination failed")
    }
  }

  @Autowired
  lateinit var repositoryService: RepositoryService

  @Autowired
  lateinit var externalTaskService: ExternalTaskService

  @Autowired
  lateinit var startProcessApi: StartProcessApi

  @Autowired
  lateinit var taskSubscriptionApi: TaskSubscriptionApi

  @Autowired
  lateinit var serviceTaskCompletionApi: ServiceTaskCompletionApi

  @Autowired
  lateinit var userTaskCompletionApi: UserTaskCompletionApi

  @Autowired
  lateinit var subscriptionRepository: SubscriptionRepository

  @Autowired
  lateinit var trace: Trace

  @BeforeEach
  fun setUp() {
    repositoryService.createDeployment().name("Simple Process").addClasspathResource(BPMN).deploy()
    taskSubscriptionApi.subscribeForTask(
      SubscribeForTaskCmd(
        restrictions = CommonRestrictions.builder().build(),
        taskType = TaskType.EXTERNAL,
        taskDescriptionKey = EXTERNAL_TASK,
        payloadDescription = null,
        action = { taskInformation, payload ->
          trace.record("handler", taskInformation.taskId, TaskType.EXTERNAL)
          when (payload[HANDLER_MODE]) {
            "complete" -> serviceTaskCompletionApi.completeTask(CompleteTaskCmd(taskInformation.taskId) { emptyMap() }).get()
            "fail" -> throw IllegalStateException("handler failed")
          }
        },
        termination = { }
      )
    ).get()
    taskSubscriptionApi.subscribeForTask(
      SubscribeForTaskCmd(
        restrictions = CommonRestrictions.builder().build(),
        taskType = TaskType.USER,
        taskDescriptionKey = USER_TASK,
        payloadDescription = null,
        action = { taskInformation, _ -> trace.record("handler", taskInformation.taskId, TaskType.USER) },
        termination = { }
      )
    ).get()
  }

  @AfterEach
  fun tearDown() {
    (subscriptionRepository as InMemSubscriptionRepository).deleteAllTaskSubscriptions()
    trace.clear()
  }

  @Test
  fun `observes a completed external task and ignores a throwing observer`() {
    val processInstanceId = startProcess("complete")

    awaitTrace(processInstanceId, TaskType.EXTERNAL, "close")

    assertThat(
      externalTaskService.createExternalTaskQuery().processInstanceId(processInstanceId).activityId(SERVICE_TASK).count()
    ).isZero()
    val externalTrace = trace.of(processInstanceId, TaskType.EXTERNAL)
    val taskId = externalTrace.first().taskId
    val activityInstanceId = externalTrace.first().event.removePrefix("start:")
    assertThat(activityInstanceId).startsWith("$SERVICE_TASK:")
    assertThat(externalTrace).containsExactly(
      TraceEntry("start:$activityInstanceId", taskId, TaskType.EXTERNAL),
      TraceEntry("handler", taskId, TaskType.EXTERNAL),
      TraceEntry("outcome:Completed", taskId, TaskType.EXTERNAL),
      TraceEntry("termination:${TaskInformation.COMPLETE}", taskId, TaskType.EXTERNAL),
      TraceEntry("close", taskId, TaskType.EXTERNAL)
    )
  }

  @Test
  fun `observes an exception escaping the handler`() {
    val processInstanceId = startProcess("fail")

    awaitTrace(processInstanceId, TaskType.EXTERNAL, "close")

    val failedTask = externalTaskService.createExternalTaskQuery().processInstanceId(processInstanceId).activityId(SERVICE_TASK).singleResult()
    assertThat(failedTask.errorMessage).isEqualTo("handler failed")
    assertThat(trace.of(processInstanceId, TaskType.EXTERNAL).map { it.event }).containsSubsequence("handler", "outcome:Thrown", "close")
  }

  @Test
  fun `observes a delivered and completed user task`() {
    val processInstanceId = startProcess("none")
    awaitTrace(processInstanceId, TaskType.USER, "close")
    val taskId = trace.of(processInstanceId, TaskType.USER).first().taskId

    userTaskCompletionApi.completeTask(CompleteTaskCmd(taskId) { emptyMap() }).get()

    awaitTrace(processInstanceId, TaskType.USER, "termination:${TaskInformation.COMPLETE}")
    assertThat(trace.of(processInstanceId, TaskType.USER).map { it.event }).containsSubsequence(
      "start:${TaskInformation.CREATE}",
      "handler",
      "close",
      "outcome:Completed",
      "termination:${TaskInformation.COMPLETE}"
    )
  }

  private fun awaitTrace(processInstanceId: String, taskType: TaskType, event: String) =
    await().atMost(Duration.ofSeconds(15)).untilAsserted {
      assertThat(trace.of(processInstanceId, taskType).map { it.event }).contains(event)
    }

  private fun startProcess(mode: String): String =
    startProcessApi.startProcess(
      StartProcessByDefinitionCmd(definitionKey = KEY, payloadSupplier = { mapOf(HANDLER_MODE to mode) })
    ).get().instanceId
}
