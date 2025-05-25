package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties.UserTaskDeliveryStrategy.REMOTE_SCHEDULED
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemotePullServicesAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.ConditionalOnUserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullUserTaskDelivery
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import java.time.Duration
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

/**
 * Dynamic / imperative scheduling configuration using own task scheduler for user tasks.
 */
@EnableScheduling
@AutoConfiguration
@ConditionalOnUserTaskDeliveryStrategy(
  strategy = REMOTE_SCHEDULED
)
@AutoConfigureAfter(C7RemotePullServicesAutoConfiguration::class)
class PullUserTaskDeliveryAutoConfiguration(
  private val remotePullUserTaskDelivery: PullUserTaskDelivery,
  private val c7RemoteAdapterProperties: C7RemoteAdapterProperties,
  @Qualifier("c7remote-task-scheduler")
  private val c7taskScheduler: TaskScheduler
) : SchedulingConfigurer {

  override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
    taskRegistrar.setScheduler(c7taskScheduler)
    taskRegistrar.addFixedDelayTask(
      {
        logger.trace { "PROCESS-ENGINE-C7-REMOTE-107: Delivering user tasks..." }
        remotePullUserTaskDelivery.refresh()
        logger.trace { "PROCESS-ENGINE-C7-REMOTE-108: Delivered user tasks." }
      },
      Duration.of(c7RemoteAdapterProperties.userTasks.scheduleDeliveryFixedRateInSeconds, ChronoUnit.SECONDS)
    )
  }
}

