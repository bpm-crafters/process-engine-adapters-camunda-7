package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.RefreshableDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.UserTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.impl.task.filterBySubscription
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.engine.TaskService
import org.camunda.bpm.engine.task.Task
import org.camunda.bpm.engine.task.TaskQuery
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * Delivers user tasks to subscriptions.
 * Uses internal Java API for pulling tasks.
 */
class RemotePullUserTaskDelivery(
  private val taskService: TaskService,
  private val repositoryService: RepositoryService,
  private val subscriptionRepository: SubscriptionRepository,
  private val executorService: ExecutorService
) : UserTaskDelivery, RefreshableDelivery {

  private val cachingProcessDefinitionKeyResolver = CachingProcessDefinitionKeyResolver(repositoryService)

  /**
   * Delivers all tasks found in user task service to corresponding subscriptions.
   */
  override fun refresh() {
    val subscriptions = subscriptionRepository.getTaskSubscriptions().filter { s -> s.taskType == TaskType.USER }
    if (subscriptions.isNotEmpty()) {
      val deliveredTaskIds = subscriptionRepository.getDeliveredTaskIds(TaskType.USER).toMutableList()
      logger.trace { "PROCESS-ENGINE-C7-REMOTE-036: pulling user tasks for subscriptions: $subscriptions" }
      taskService
        .createTaskQuery()
        .forSubscriptions(subscriptions)
        .list()
        .parallelStream()
        .forEach { task ->
          subscriptions
            .firstOrNull { subscription -> subscription.matches(task) }
            ?.let { activeSubscription ->
              executorService.submit {  // in another thread
                try {
                  // create task information and set up the reason
                  val taskInformation = task.toTaskInformation().withReason(
                    if (deliveredTaskIds.contains(task.id) && subscriptionRepository.getActiveSubscriptionForTask(task.id) == activeSubscription) {
                      // remove from already delivered
                      deliveredTaskIds.remove(task.id)
                      // task was already delivered to this subscription
                      if (task.createTime != task.lastUpdated) {
                        TaskInformation.UPDATE
                        // FIXME -> detect assignment change
                      } else {
                        TaskInformation.CREATE
                      }
                    } else {
                      // task is new for this subscription
                      TaskInformation.CREATE
                    }
                  )
                  subscriptionRepository.activateSubscriptionForTask(task.id, activeSubscription)
                  val variables = taskService.getVariables(task.id).filterBySubscription(activeSubscription)
                  logger.debug { "PROCESS-ENGINE-C7-REMOTE-037: delivering user task ${task.id}." }
                  activeSubscription.action.accept(taskInformation, variables)
                } catch (e: Exception) {
                  logger.error { "PROCESS-ENGINE-C7-REMOTE-038: error delivering task ${task.id}: ${e.message}" }
                  subscriptionRepository.deactivateSubscriptionForTask(taskId = task.id)
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
      logger.trace { "PROCESS-ENGINE-C7-REMOTE-039: pull user tasks disabled because of no active subscriptions" }
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun TaskQuery.forSubscriptions(subscriptions: List<TaskSubscriptionHandle>): TaskQuery {
    // FIXME: narrow down, for the moment take all tasks
    return this
      .active()
    // FIXME -> consider complex tenant filtering
  }


  private fun TaskSubscriptionHandle.matches(task: Task): Boolean =
    (this.taskDescriptionKey == null
      || this.taskDescriptionKey == task.taskDefinitionKey
      || this.taskDescriptionKey == task.id)
      && this.restrictions.all {
      when (it.key) {
        CommonRestrictions.EXECUTION_ID -> it.value == task.executionId
        CommonRestrictions.TENANT_ID -> it.value == task.tenantId
        CommonRestrictions.ACTIVITY_ID -> it.value == task.taskDefinitionKey
        CommonRestrictions.PROCESS_INSTANCE_ID -> it.value == task.processInstanceId
        CommonRestrictions.PROCESS_DEFINITION_ID -> it.value == task.processDefinitionId
        CommonRestrictions.PROCESS_DEFINITION_KEY -> it.value == cachingProcessDefinitionKeyResolver.getProcessDefinitionKey(task.processDefinitionId)
        CommonRestrictions.PROCESS_DEFINITION_VERSION_TAG -> it.value == cachingProcessDefinitionKeyResolver.getProcessDefinitionVersionTag(task.processDefinitionId)
        else -> false
      }
    }
}

