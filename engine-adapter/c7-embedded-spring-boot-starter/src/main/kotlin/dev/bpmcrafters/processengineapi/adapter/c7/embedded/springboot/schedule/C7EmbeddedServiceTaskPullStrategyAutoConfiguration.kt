package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties.ExternalServiceTaskDeliveryStrategy.EMBEDDED_SCHEDULED
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.ConditionalOnServiceTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDelivery
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit.SECONDS

private val logger = KotlinLogging.logger {}

/**
 * Dynamic / imperative scheduling configuration using own task scheduler for service tasks.
 */
@Configuration
@ConditionalOnServiceTaskDeliveryStrategy(
  strategy = EMBEDDED_SCHEDULED
)
@AutoConfigureAfter(C7EmbeddedSchedulingAutoConfiguration::class)
class C7EmbeddedServiceTaskPullStrategyAutoConfiguration(
  private val embeddedPullServiceTaskDelivery: EmbeddedPullServiceTaskDelivery
) {

  @Scheduled(
    fixedDelayString = "#{@'dev.bpm-crafters.process-api.adapter.c7embedded-dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties'.serviceTasks.scheduleDeliveryFixedRateInSeconds}",
    timeUnit = SECONDS,
    scheduler = "c7embedded-task-scheduler"
  )
  fun refresh() {
    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-105: Delivering external tasks..." }
    embeddedPullServiceTaskDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-106: Delivered external tasks." }
  }

}
