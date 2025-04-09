package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.RefreshableDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.ServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.impl.task.filterBySubscription
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.ExternalTaskService
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryBuilder
import org.camunda.bpm.engine.externaltask.LockedExternalTask
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * Delivers external tasks to subscriptions.
 * This implementation uses internal Java API and pulls tasks for delivery.
 */
class RemotePullServiceTaskDelivery(
    private val externalTaskService: ExternalTaskService,
    private val workerId: String,
    private val subscriptionRepository: SubscriptionRepository,
    private val maxTasks: Int,
    private val lockDurationInSeconds: Long,
    private val retryTimeoutInSeconds: Long,
    private val retries: Int,
    private val executorService: ExecutorService
) : ServiceTaskDelivery, RefreshableDelivery {

  /**
   * Delivers all tasks found in the external service to corresponding subscriptions.
   */
  override fun refresh() {

    val subscriptions = subscriptionRepository.getTaskSubscriptions().filter { s -> s.taskType == TaskType.EXTERNAL }
    if (subscriptions.isNotEmpty()) {
      val deliveredTaskIds = subscriptionRepository.getDeliveredTaskIds(TaskType.EXTERNAL).toMutableList()
      logger.trace { "PROCESS-ENGINE-C7-REMOTE-030: pulling service tasks for subscriptions: $subscriptions" }
      externalTaskService
        .fetchAndLock(maxTasks, workerId)
        .forSubscriptions(subscriptions)
        .execute()
        .parallelStream()
        .forEach { lockedTask ->
          subscriptions
            .firstOrNull { subscription -> subscription.matches(lockedTask) }
            ?.let { activeSubscription ->
              executorService.submit {  // in another thread
                try {
                  if (deliveredTaskIds.contains(lockedTask.id) && subscriptionRepository.getActiveSubscriptionForTask(lockedTask.id) == activeSubscription) {
                    // remove from already delivered
                    deliveredTaskIds.remove(lockedTask.id)
                  }
                  // create task information and set up the reason
                  val taskInformation = lockedTask.toTaskInformation().withReason(TaskInformation.CREATE)
                  subscriptionRepository.activateSubscriptionForTask(lockedTask.id, activeSubscription)
                  val variables = lockedTask.variables.filterBySubscription(activeSubscription)
                  logger.debug { "PROCESS-ENGINE-C7-REMOTE-031: delivering service task ${lockedTask.id}." }
                  activeSubscription.action.accept(taskInformation, variables)
                  logger.debug { "PROCESS-ENGINE-C7-REMOTE-032: successfully delivered service task ${lockedTask.id}." }
                } catch (e: Exception) {
                  val jobRetries: Int = lockedTask.retries ?: retries
                  logger.error { "PROCESS-ENGINE-C7-REMOTE-033: failing delivering task ${lockedTask.id}: ${e.message}" }
                  externalTaskService.handleFailure(lockedTask.id, workerId, e.message, jobRetries - 1, retryTimeoutInSeconds * 1000)
                  logger.error { "PROCESS-ENGINE-C7-REMOTE-034: successfully failed delivering task ${lockedTask.id}: ${e.message}" }
                }
              }.get()
            }
        }
      // now we removed all still existing task ids from the list of already delivered
      // the remaining tasks doesn't exist in the engine, lets handle them
      deliveredTaskIds.forEach { taskId ->
        executorService.submit {
          // deactivate active subscription and handle termination
          subscriptionRepository.deactivateSubscriptionForTask(taskId)?.termination?.accept(
            TaskInformation(
              taskId = taskId,
              meta = emptyMap()
            ).withReason(TaskInformation.DELETE)
          )
        }
      }

    } else {
      logger.trace { "PROCESS-ENGINE-C7-REMOTE-035: Pull external tasks disabled because of no active subscriptions" }
    }
  }

  private fun ExternalTaskQueryBuilder.forSubscriptions(subscriptions: List<TaskSubscriptionHandle>): ExternalTaskQueryBuilder {
    subscriptions
      .mapNotNull { it.taskDescriptionKey }
      .distinct()
      .forEach { topic ->
        this.topic(topic, lockDurationInSeconds * 1000) // convert to ms
          .enableCustomObjectDeserialization()
          // FIXME -> consider complex tenant filtering
      }
    return this
  }

  private fun TaskSubscriptionHandle.matches(task: LockedExternalTask): Boolean =
    (this.taskDescriptionKey == null
      || this.taskDescriptionKey == task.topicName)
      && this.restrictions.all {
      when (it.key) {
        CommonRestrictions.EXECUTION_ID -> it.value == task.executionId
        CommonRestrictions.ACTIVITY_ID -> it.value == task.activityId
        CommonRestrictions.BUSINESS_KEY -> it.value == task.businessKey
        CommonRestrictions.TENANT_ID -> it.value == task.tenantId
        CommonRestrictions.PROCESS_INSTANCE_ID -> it.value == task.processInstanceId
        CommonRestrictions.PROCESS_DEFINITION_KEY -> it.value == task.processDefinitionKey
        CommonRestrictions.PROCESS_DEFINITION_ID -> it.value == task.processDefinitionId
        CommonRestrictions.PROCESS_DEFINITION_VERSION_TAG -> it.value == task.processDefinitionVersionTag
        else -> false
      }
    }

}
