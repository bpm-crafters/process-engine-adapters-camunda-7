package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy.REMOTE_SCHEDULED
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemotePullServicesAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.ConditionalOnServiceTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.springboot.lifecycle.ProcessApplicationRunningEvent
import dev.bpmcrafters.processengineapi.springboot.lifecycle.ProcessApplicationStoppingEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.TaskScheduler
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture

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
  private val pullServiceTaskDelivery: PullServiceTaskDelivery,
  private val c7RemoteAdapterProperties: C7RemoteAdapterProperties,
  @Qualifier("c7remote-task-scheduler")
  private val taskScheduler: TaskScheduler
) {
  private var scheduledDelivery: ScheduledFuture<*>? = null

  @EventListener
  @Order(Ordered.LOWEST_PRECEDENCE)
  @Synchronized
  fun start(event: ProcessApplicationRunningEvent) {
    if (scheduledDelivery != null) {
      return
    }

    val deliveryInterval = Duration.ofMillis(c7RemoteAdapterProperties.serviceTasks.scheduleDeliveryIntervalInMilliseconds)
    scheduledDelivery = taskScheduler.scheduleWithFixedDelay(
      { refresh() },
      Instant.now().plus(deliveryInterval),
      deliveryInterval
    )
  }

  @EventListener
  @Synchronized
  fun stop(event: ProcessApplicationStoppingEvent) {
    scheduledDelivery?.cancel(false)
    scheduledDelivery = null
  }

  private fun refresh() {
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-105: Delivering external tasks..." }
    pullServiceTaskDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-106: Delivered external tasks." }
  }

}
