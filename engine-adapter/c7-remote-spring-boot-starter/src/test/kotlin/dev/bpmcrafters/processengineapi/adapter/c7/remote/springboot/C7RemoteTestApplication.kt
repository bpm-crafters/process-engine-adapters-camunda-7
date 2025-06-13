package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.tngtech.jgiven.integration.spring.EnableJGiven
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy.REMOTE_SCHEDULED
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy.REMOTE_SUBSCRIBED
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.UserTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.subscribe.SubscribingServiceTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.test.ProcessTestHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.client.spi.DataFormatConfigurator
import org.camunda.bpm.client.variable.impl.format.json.JacksonJsonDataFormat
import org.camunda.community.rest.client.FeignClientConfiguration
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.camunda.community.rest.variables.ValueMapperConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import java.text.SimpleDateFormat

private val logger = KotlinLogging.logger {}

@EnableJGiven
@SpringBootApplication
@ImportAutoConfiguration(
  FeignClientConfiguration::class,
  ValueMapperConfiguration::class,
)
class C7RemoteTestApplication {


  @Bean
  fun objectMapper(): ObjectMapper = JacksonDataFormatConfigurator.configureObjectMapper(ObjectMapper())

  @ConditionalOnServiceTaskDeliveryStrategy(
    strategy = REMOTE_SCHEDULED
  )
  @Bean
  fun remoteScheduledProcessTestHelper(
    processInstanceApiClient: ProcessInstanceApiClient,
    startProcessApi: StartProcessApi,
    taskSubscriptionApi: TaskSubscriptionApi,
    userTaskDelivery: UserTaskDelivery,
    serviceTaskDelivery: PullServiceTaskDelivery,
    userTaskCompletionApi: UserTaskCompletionApi,
    serviceTaskCompletionApi: ServiceTaskCompletionApi,
    subscriptionRepository: SubscriptionRepository,
  ): ProcessTestHelper = C7RemoteProcessTestHelper(
    processInstanceApiClient = processInstanceApiClient,
    startProcessApi = startProcessApi,
    taskSubscriptionApi = taskSubscriptionApi,
    userTaskDelivery = userTaskDelivery,
    serviceTaskDelivery = serviceTaskDelivery,
    userTaskCompletionApi = userTaskCompletionApi,
    serviceTaskCompletionApi = serviceTaskCompletionApi,
    subscriptionRepository = subscriptionRepository
  )

  @ConditionalOnServiceTaskDeliveryStrategy(
    strategy = REMOTE_SUBSCRIBED
  )
  @Bean
  fun remoteSubscribedProcessTestHelper(
    processInstanceApiClient: ProcessInstanceApiClient,
    startProcessApi: StartProcessApi,
    taskSubscriptionApi: TaskSubscriptionApi,
    userTaskDelivery: UserTaskDelivery,
    serviceTaskDelivery: SubscribingServiceTaskDelivery,
    userTaskCompletionApi: UserTaskCompletionApi,
    serviceTaskCompletionApi: ServiceTaskCompletionApi,
    subscriptionRepository: SubscriptionRepository,
  ): ProcessTestHelper = C7RemoteProcessTestHelper(
    processInstanceApiClient = processInstanceApiClient,
    startProcessApi = startProcessApi,
    taskSubscriptionApi = taskSubscriptionApi,
    userTaskDelivery = userTaskDelivery,
    serviceTaskDelivery = serviceTaskDelivery,
    userTaskCompletionApi = userTaskCompletionApi,
    serviceTaskCompletionApi = serviceTaskCompletionApi,
    subscriptionRepository = subscriptionRepository
  )

}

/**
 * Configured SPIN Jackson Mapper.
 * Don't forget to look into **META-INF/services**
 */
class JacksonDataFormatConfigurator : DataFormatConfigurator<JacksonJsonDataFormat> {

  companion object {
    fun configureObjectMapper(objectMapper: ObjectMapper) = objectMapper.apply {
      registerModule(KotlinModule.Builder().build())
      registerModule(Jdk8Module())
      registerModule(JavaTimeModule())
      dateFormat = SimpleDateFormat("yyyy-MM-dd'T'hh:MM:ss.SSSz")
    }
  }

  override fun configure(dataFormat: JacksonJsonDataFormat) {
    configureObjectMapper(dataFormat.objectMapper)
  }

  override fun getDataFormatClass(): Class<JacksonJsonDataFormat> {
    return JacksonJsonDataFormat::class.java
  }
}
