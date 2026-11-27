package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import dev.bpmcrafters.processengineapi.CommonRestrictions.BUSINESS_KEY
import dev.bpmcrafters.processengineapi.adapter.c7.common.task.ExternalTaskNotFoundException
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import dev.bpmcrafters.processengineapi.test.JGivenSpringBaseIntegrationTest
import io.toolisticon.testing.jgiven.GIVEN
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.SECONDS

@SpringBootTest(
  classes = [C7RemoteTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DirtiesContext
@Testcontainers
abstract class AbstractC7RemoteApiITestBase : JGivenSpringBaseIntegrationTest() {

  companion object {
    const val KEY = "simple-process"
    const val START_MESSAGE = "startMessage"
    const val BPMN = "bpmn/$KEY.bpmn"
    const val DMN = "decision/main_decision.dmn"
    const val USER_TASK = "user-perform-task"
    const val EXTERNAL_TASK = "execute-action-external"

    @JvmStatic
    @Container
    val camundaContainer = Camunda7RunTestContainer("run-7.24.0")

    @JvmStatic
    @DynamicPropertySource
    fun configure(registry: DynamicPropertyRegistry) {
      registry.add("camunda.bpm.client.base-url") { "http://localhost:${camundaContainer.firstMappedPort}/engine-rest/" }
      registry.add("feign.client.config.default.url") { "http://localhost:${camundaContainer.firstMappedPort}/engine-rest/" }
    }

  }

  @Autowired
  lateinit var deploymentApi: DeploymentApi

  @Autowired
  lateinit var myProcessTestHelper: C7RemoteProcessTestHelper

  @Autowired
  lateinit var processInstanceApiClient: ProcessInstanceApiClient

  @BeforeEach
  fun setUp() {
    super.processTestHelper = myProcessTestHelper
    deploymentApi
      .deploy(
        DeployBundleCommand(
          listOf(
            NamedResource.fromClasspath(BPMN),
            NamedResource.fromClasspath(DMN),
          )
        )
      ).get()

    GIVEN
      .`process helper`(this.processTestHelper)
  }

  @AfterEach
  fun tearDown() {
    processTestHelper.clearAllSubscriptions()
  }

  protected fun completeDeletedExternalTask(): ExternalTaskNotFoundException {
    val restrictions = mapOf(BUSINESS_KEY to UUID.randomUUID().toString())
    val delivered = CompletableFuture<TaskInformation>()
    val terminated = CompletableFuture<TaskInformation>()
    myProcessTestHelper.getTaskSubscriptionApi().subscribeForTask(
      SubscribeForTaskCmd(
        restrictions = restrictions,
        taskType = TaskType.EXTERNAL,
        taskDescriptionKey = EXTERNAL_TASK,
        action = { task, _ -> delivered.complete(task) },
        termination = { task -> terminated.complete(task) }
      )
    ).get(10, SECONDS)
    val process = myProcessTestHelper.getStartProcessApi()
      .startProcess(StartProcessByDefinitionCmd(KEY, restrictions))
      .get(10, SECONDS)
    myProcessTestHelper.triggerExternalTaskDeliveryManually()
    val taskId = delivered.get(10, SECONDS).taskId

    val deletion = processInstanceApiClient.deleteProcessInstance(process.instanceId, false, false, false, true)
    assertThat(deletion.statusCode.value()).isEqualTo(204)

    val failure = assertThrows<ExecutionException> {
      myProcessTestHelper.getServiceTaskCompletionApi()
        .completeTask(CompleteTaskCmd(taskId) { emptyMap() })
        .get(10, SECONDS)
    }
    val missing = assertInstanceOf(ExternalTaskNotFoundException::class.java, failure.cause)
    assertThat(missing.taskId).isEqualTo(taskId)
    val termination = terminated.get(10, SECONDS)
    assertThat(termination.taskId).isEqualTo(taskId)
    assertThat(termination.meta[TaskInformation.REASON]).isEqualTo(TaskInformation.DELETE)
    return missing
  }
}
