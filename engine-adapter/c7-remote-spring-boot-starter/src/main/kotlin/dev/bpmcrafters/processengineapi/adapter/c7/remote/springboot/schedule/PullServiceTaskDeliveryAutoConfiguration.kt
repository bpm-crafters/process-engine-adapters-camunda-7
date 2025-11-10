package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy.REMOTE_SCHEDULED
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemotePullServicesAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.ConditionalOnServiceTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDelivery
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit.SECONDS

private val logger = KotlinLogging.logger {}

/**
 * Dynamic / imperative scheduling configuration using own task scheduler for service tasks.
 */
@AutoConfiguration
@ConditionalOnServiceTaskDeliveryStrategy(
  strategy = REMOTE_SCHEDULED
)
@AutoConfigureAfter(C7RemotePullServicesAutoConfiguration::class)
class PullServiceTaskDeliveryAutoConfiguration(
  private val pullServiceTaskDelivery: PullServiceTaskDelivery
) {

  @Scheduled(
    fixedDelayString = "#{@'dev.bpm-crafters.process-api.adapter.c7remote-dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties'.serviceTasks.scheduleDeliveryFixedRateInSeconds}",
    timeUnit = SECONDS,
    scheduler = "c7remote-task-scheduler"
  )
  fun refresh() {
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-105: Delivering external tasks..." }
    pullServiceTaskDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-106: Delivered external tasks." }
  }

}
