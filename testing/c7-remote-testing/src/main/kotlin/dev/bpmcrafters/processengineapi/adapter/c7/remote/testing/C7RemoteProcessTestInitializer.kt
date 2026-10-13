package dev.bpmcrafters.processengineapi.adapter.c7.remote.testing

import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskModificationApi
import dev.bpmcrafters.processengineapi.testing.bootstrap.ProcessTestInitializationRequest
import dev.bpmcrafters.processengineapi.testing.bootstrap.ProcessTestInitializer
import org.camunda.community.rest.client.EnableCamundaFeignClients
import org.camunda.community.rest.client.FeignClientConfiguration
import org.camunda.community.rest.variables.ValueMapperConfiguration
import org.springframework.beans.factory.getBean
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

class C7RemoteProcessTestInitializer : ProcessTestInitializer {

  private var container: GenericContainer<*>? = null

  companion object {
    const val QUALIFIER = "c7remote"
  }

  override fun qualifier() = QUALIFIER

  override fun initialize(request: ProcessTestInitializationRequest): C7RemoteProcessTestContext {
    val engineConfiguration: C7RemoteProcessTestEngine? = request.testClass.getAnnotation(C7RemoteProcessTestEngine::class.java)
    val configuredUrl: String? = engineConfiguration?.url?.takeIf { it.isNotBlank() }

    val url = if (configuredUrl == null) {
      this.container = startContainer(engineConfiguration?.image)
      "http://${container?.host}:${container?.firstMappedPort}/engine-rest"
    } else {
      configuredUrl
    }

    val workerId = "process-test-${request.testClass.simpleName}"
    val application = startApplication(url, workerId, request.configuration)

    return C7RemoteProcessTestContext(
      application = application,
      stopHandler = { this.container?.stop() },
      runtimeApis = C7RemoteProcessTestRuntimeApis(
        startProcessApi = application.getBean<StartProcessApi>(),
        taskSubscriptionApi = application.getBean<TaskSubscriptionApi>(),
        userTaskCompletionApi = application.getBean<UserTaskCompletionApi>(),
        userTaskModificationApi = application.getBean<UserTaskModificationApi>(),
        serviceTaskCompletionApi = application.getBean<ServiceTaskCompletionApi>(),
        correlationApi = application.getBean<CorrelationApi>(),
        signalApi = application.getBean<SignalApi>(),
        deploymentApi = application.getBean<DeploymentApi>(),
      ),
      workerId = workerId,
    )
  }

  private fun startContainer(image: String?): GenericContainer<*> = GenericContainer(image ?: C7RemoteProcessTestEngine.DEFAULT_IMAGE)
    .withCommand("./camunda.sh", "--rest")
    .withEnv("CAMUNDA_BPM_DEFAULT-SERIALIZATION-FORMAT", "application/json")
    .withExposedPorts(8080)
    .waitingFor(Wait.forHttp("/engine-rest/engine/").forPort(8080))
    .apply { start() }

  private fun startApplication(
    url: String,
    workerId: String,
    initializerConfiguration: Map<String, String>,
  ): ConfigurableApplicationContext =
    SpringApplicationBuilder(C7RemoteProcessTestApplication::class.java)
      .web(WebApplicationType.NONE)
      .properties(
        mapOf(
          "feign.client.config.default.url" to url,
          "dev.bpm-crafters.process-api.adapter.c7remote.enabled" to "true",
          "dev.bpm-crafters.process-api.adapter.c7remote.service-tasks.worker-id" to workerId,
          "dev.bpm-crafters.process-api.adapter.c7remote.service-tasks.delivery-strategy" to "REMOTE_SCHEDULED",
          "dev.bpm-crafters.process-api.adapter.c7remote.user-tasks.delivery-strategy" to "REMOTE_SCHEDULED",
          "spring.task.scheduling.enabled" to "false",
        ) + initializerConfiguration
      )
      .run()
}

@SpringBootApplication
@EnableCamundaFeignClients
@ImportAutoConfiguration(FeignClientConfiguration::class, ValueMapperConfiguration::class)
private class C7RemoteProcessTestApplication
