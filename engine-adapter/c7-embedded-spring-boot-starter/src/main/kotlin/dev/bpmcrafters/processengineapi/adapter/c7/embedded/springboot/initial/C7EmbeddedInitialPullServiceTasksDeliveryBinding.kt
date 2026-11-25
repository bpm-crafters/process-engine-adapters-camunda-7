package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.ExternalTaskService
import java.util.concurrent.ThreadPoolExecutor

private val logger = KotlinLogging.logger {}

/**
 * This class is responsible for the initial pull of service tasks.
 * We are not relying on the pull delivery strategy configured centrally, because for other deliveries we still want to
 * execute an initial pull (e.g. for event-based delivery)
 */
open class C7EmbeddedInitialPullServiceTasksDeliveryBinding(
  externalTaskService: ExternalTaskService,
  subscriptionRepository: SubscriptionRepository,
  private val c7AdapterProperties: C7EmbeddedAdapterProperties,
  private val executor: ThreadPoolExecutor,
  metrics: EmbeddedPullServiceTaskDeliveryMetrics
) {

  private val pullDelivery = EmbeddedPullServiceTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    externalTaskService = externalTaskService,
    workerId = c7AdapterProperties.serviceTasks.workerId,
    maxTasks = c7AdapterProperties.serviceTasks.maxTaskCount,
    lockDurationInSeconds = c7AdapterProperties.serviceTasks.lockTimeInSeconds,
    retryTimeoutInSeconds = c7AdapterProperties.serviceTasks.retryTimeoutInSeconds,
    retries = c7AdapterProperties.serviceTasks.retries,
    executor = executor,
    metrics = metrics
  )

  open fun pullServiceTasks() {
    if (c7AdapterProperties.serviceTasks.executeInitialPullAsync) {
      executor.execute { deliverServiceTasks() }
    } else {
      deliverServiceTasks()
    }
  }

  private fun deliverServiceTasks() {
    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-101: Delivering service tasks..." }
    pullDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-102: Delivered service tasks." }
  }

}
