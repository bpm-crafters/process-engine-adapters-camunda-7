package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.ThreadPoolExecutor

private val logger = KotlinLogging.logger {}

/**
 * This class is responsible for the initial pull of service tasks.
 * We are not relying on the pull delivery strategy configured centrally, because for other deliveries we still want to
 * execute an initial pull.
 */
open class C7RemoteInitialPullServiceTasksDeliveryBinding(
  externalTaskApiClient: ExternalTaskApiClient,
  subscriptionRepository: SubscriptionRepository,
  private val c7AdapterProperties: C7RemoteAdapterProperties,
  private val executor: ThreadPoolExecutor,
  valueMapper: ValueMapper,
  processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  metrics: PullServiceTaskDeliveryMetrics
) {
  private val pullDelivery = PullServiceTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    externalTaskApiClient = externalTaskApiClient,
    workerId = c7AdapterProperties.serviceTasks.workerId,
    maxTasks = c7AdapterProperties.serviceTasks.maxTaskCount,
    lockDurationInSeconds = c7AdapterProperties.serviceTasks.lockTimeInSeconds,
    retryTimeoutInSeconds = c7AdapterProperties.serviceTasks.retryTimeoutInSeconds,
    retries = c7AdapterProperties.serviceTasks.retries,
    executor = executor,
    valueMapper = valueMapper,
    deserializeOnServer = c7AdapterProperties.serviceTasks.deserializeOnServer,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
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
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-101: Delivering service tasks..." }
    pullDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-102: Delivered service tasks." }
  }

}
