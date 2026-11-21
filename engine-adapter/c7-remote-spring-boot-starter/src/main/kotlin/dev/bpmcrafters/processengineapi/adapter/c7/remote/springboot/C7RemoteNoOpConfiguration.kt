package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.NoOpServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional

private val logger = KotlinLogging.logger {}

@AutoConfiguration
@AutoConfigureAfter(C7RemoteAdapterAutoConfiguration::class)
@Conditional(C7RemoteAdapterEnabledCondition::class)
class C7RemoteNoOpConfiguration {

  @ConditionalOnServiceTaskDeliveryStrategy(
    strategy = C7RemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy.DISABLED
  )
  @Bean
  fun noOpServiceTaskCompletionApi(): ServiceTaskCompletionApi {
    logger.info { "PROCESS-ENGINE-C7-REMOTE-211: Configured a NO-OP ServiceTaskCompletion API, no service task completion is possible." }
    return NoOpServiceTaskCompletionApiImpl()
  }
}
