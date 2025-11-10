package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties.UserTaskDeliveryStrategy.REMOTE_SCHEDULED
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemotePullServicesAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.ConditionalOnUserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullUserTaskDelivery
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit.SECONDS

private val logger = KotlinLogging.logger {}

/**
 * Dynamic / imperative scheduling configuration using own task scheduler for user tasks.
 */
@AutoConfiguration
@ConditionalOnUserTaskDeliveryStrategy(
  strategy = REMOTE_SCHEDULED
)
@AutoConfigureAfter(C7RemotePullServicesAutoConfiguration::class)
class PullUserTaskDeliveryAutoConfiguration(
  private val remotePullUserTaskDelivery: PullUserTaskDelivery
) {

  @Scheduled(
    fixedDelayString = "#{@'dev.bpm-crafters.process-api.adapter.c7remote-dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties'.userTasks.scheduleDeliveryFixedRateInSeconds}",
    timeUnit = SECONDS,
    scheduler = "c7remote-task-scheduler"
  )
  fun refresh() {
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-107: Delivering user tasks..." }
    remotePullUserTaskDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-108: Delivered user tasks." }
  }

}
