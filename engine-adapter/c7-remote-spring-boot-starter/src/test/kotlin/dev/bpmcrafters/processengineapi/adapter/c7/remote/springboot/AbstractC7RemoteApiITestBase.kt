package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import dev.bpmcrafters.processengineapi.test.JGivenSpringBaseIntegrationTest
import io.toolisticon.testing.jgiven.GIVEN
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

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

    const val USER_TASK = "user-perform-task"
    const val EXTERNAL_TASK = "execute-action-external"

    @JvmStatic
    @Container
    val camundaContainer = Camunda7RunTestContainer("run-7.23.0")

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

  @BeforeEach
  fun setUp() {
    super.processTestHelper = myProcessTestHelper
    deploymentApi
      .deploy(
        DeployBundleCommand(
          listOf(NamedResource.fromClasspath(BPMN))
        )
      ).get()

    GIVEN
      .`process helper`(this.processTestHelper)
  }

  @AfterEach
  fun tearDown() {
    processTestHelper.clearAllSubscriptions()
  }
}
