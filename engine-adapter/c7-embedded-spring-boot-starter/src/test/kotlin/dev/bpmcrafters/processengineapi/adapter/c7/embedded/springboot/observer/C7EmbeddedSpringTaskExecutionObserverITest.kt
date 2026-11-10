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
    const val HANDLER_MODE = "handlerMode"
  }

  data class TraceEntry(val event: String, val taskId: String)

  @TestConfiguration(proxyBeanMethods = false)
  class ObserverConfiguration {

    @Bean
    fun trace(): MutableList<TraceEntry> = CopyOnWriteArrayList()

    @Bean
    @Order(1)
    fun tracingObserver(trace: MutableList<TraceEntry>) = object : TaskExecutionObserver {
      override fun onStart(task: TaskInformation): TaskExecutionCleanup {
        trace += TraceEntry("start:${task.meta[CommonRestrictions.ACTIVITY_INSTANCE_ID]}", task.taskId)
        return TaskExecutionCleanup { trace += TraceEntry("close", task.taskId) }
      }

      override fun onOutcome(taskId: String, outcome: TaskExecutionOutcome) {
        trace += TraceEntry("outcome:${outcome::class.simpleName}", taskId)
      }
    }

    @Bean
    @Order(2)
    fun throwingObserver() = object : TaskExecutionObserver {
      override fun onStart(task: TaskInformation): TaskExecutionCleanup = throw IllegalStateException("start failed")
      override fun onOutcome(taskId: String, outcome: TaskExecutionOutcome) = throw IllegalStateException("outcome failed")
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
  lateinit var subscriptionRepository: SubscriptionRepository

  @Autowired
  lateinit var trace: MutableList<TraceEntry>

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
          trace += TraceEntry("handler", taskInformation.taskId)
          when (payload[HANDLER_MODE]) {
            "complete" -> serviceTaskCompletionApi.completeTask(CompleteTaskCmd(taskInformation.taskId) { emptyMap() }).get()
            "fail" -> throw IllegalStateException("handler failed")
          }
        },
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

    awaitCleanup()

    assertThat(
      externalTaskService.createExternalTaskQuery().processInstanceId(processInstanceId).activityId(SERVICE_TASK).count()
    ).isZero()
    val taskId = trace.first().taskId
    val activityInstanceId = trace.first().event.removePrefix("start:")
    assertThat(activityInstanceId).startsWith("$SERVICE_TASK:")
    assertThat(trace).containsExactly(
      TraceEntry("start:$activityInstanceId", taskId),
      TraceEntry("handler", taskId),
      TraceEntry("outcome:Completed", taskId),
      TraceEntry("close", taskId)
    )
  }

  @Test
  fun `observes an exception escaping the handler`() {
    val processInstanceId = startProcess("fail")

    awaitCleanup()

    val failedTask = externalTaskService.createExternalTaskQuery().processInstanceId(processInstanceId).activityId(SERVICE_TASK).singleResult()
    assertThat(failedTask.errorMessage).isEqualTo("handler failed")
    assertThat(trace.map { it.event }).endsWith("handler", "outcome:Thrown", "close")
  }

  private fun awaitCleanup() =
    await().atMost(Duration.ofSeconds(15)).untilAsserted {
      assertThat(trace.map { it.event }).contains("close")
    }

  private fun startProcess(mode: String): String =
    startProcessApi.startProcess(
      StartProcessByDefinitionCmd(definitionKey = KEY, payloadSupplier = { mapOf(HANDLER_MODE to mode) })
    ).get().instanceId
}
