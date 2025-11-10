package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties.UserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.ConditionalOnUserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullUserTaskDelivery
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import java.util.concurrent.TimeUnit.SECONDS

private val logger = KotlinLogging.logger {}

/**
 * Dynamic / imperative scheduling configuration using own task scheduler for user tasks.
 */
@Configuration
@ConditionalOnUserTaskDeliveryStrategy(
  strategies = [ UserTaskDeliveryStrategy.EMBEDDED_SCHEDULED ]
)
@AutoConfigureAfter(C7EmbeddedSchedulingAutoConfiguration::class)
class C7EmbeddedUserTaskPullStrategyAutoConfiguration(
  private val embeddedPullUserTaskDelivery: EmbeddedPullUserTaskDelivery
) {

  @Scheduled(
    fixedDelayString = "#{@'dev.bpm-crafters.process-api.adapter.c7embedded-dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties'.userTasks.scheduleDeliveryFixedRateInSeconds}",
    timeUnit = SECONDS,
    scheduler = "c7embedded-task-scheduler"
  )
  fun refresh() {
    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-107: Delivering user tasks..." }
    embeddedPullUserTaskDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-108: Delivered user tasks." }
  }

}
