package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.initial.C7EmbeddedInitialPullUserTasksDeliveryBinding.Companion.ORDER
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullUserTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.springboot.lifecycle.ProcessApplicationRunningEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.TaskService
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * This class is responsible for the initial pull of user tasks.
 * We are not relying on the pull delivery strategy configured centrally, because for other deliveries we still want to
 * execute an initial pull (e.g. for event-based delivery)
 */
@Order(ORDER)
open class C7EmbeddedInitialPullUserTasksDeliveryBinding(
    subscriptionRepository: SubscriptionRepository,
    taskService: TaskService,
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    private val executorService: ExecutorService,
    private val c7EmbeddedAdapterProperties: C7EmbeddedAdapterProperties
) {

  companion object {
    const val ORDER = Ordered.HIGHEST_PRECEDENCE + 2000
  }

  private val pullDelivery = EmbeddedPullUserTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    taskService = taskService,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    executorService = executorService
  )

  @EventListener
  @Order(ORDER)
  open fun pullUserTasks(event: ProcessApplicationRunningEvent) {
    if (c7EmbeddedAdapterProperties.userTasks.executeInitialPullAsync) {
      executorService.execute { deliverUserTasks() }
    } else {
      deliverUserTasks()
    }
  }

  private fun deliverUserTasks() {
    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-103: Delivering user tasks..." }
    pullDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-104: Delivered user tasks." }
  }
}
