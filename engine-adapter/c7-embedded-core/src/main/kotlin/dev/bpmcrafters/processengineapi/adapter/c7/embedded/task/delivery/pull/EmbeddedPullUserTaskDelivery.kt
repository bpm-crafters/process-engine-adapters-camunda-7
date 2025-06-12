package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.*
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.impl.task.filterBySubscription
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.engine.TaskService
import org.camunda.bpm.engine.task.IdentityLink
import org.camunda.bpm.engine.task.Task
import org.camunda.bpm.engine.task.TaskQuery
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * Delivers user tasks to subscriptions.
 * Uses internal Java API for pulling tasks.
 */
class EmbeddedPullUserTaskDelivery(
  private val taskService: TaskService,
  private val subscriptionRepository: SubscriptionRepository,
  private val processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  private val executorService: ExecutorService
) : UserTaskDelivery, RefreshableDelivery {

  private val deliveredTasks: ConcurrentHashMap<String, TaskInformation> = ConcurrentHashMap()

  /**
   * Delivers all tasks found in user task service to corresponding subscriptions.
   */
  override fun refresh() {
    val subscriptions = subscriptionRepository.getTaskSubscriptions().filter { s -> s.taskType == TaskType.USER }
    if (subscriptions.isNotEmpty()) {
      val deliveredTaskIds = subscriptionRepository.getDeliveredTaskIds(TaskType.USER).toMutableList()
      // clean up task information items which are not in the list of delivered task ids. This is because,
      // if a task has been completed via API and the list of delivered tasks is reduced, the `deliveredTasks`
      // variable has not been updated yet.
      synchronized(deliveredTasks) {
        (deliveredTasks.keys().asSequence().filterNot { deliveredTaskIds.contains(it) }).forEach(deliveredTasks::remove)
      }

      logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-036: pulling user tasks for subscriptions: $subscriptions" }
      taskService
        .createTaskQuery()
        .initializeFormKeys()
        .forSubscriptions(subscriptions)
        .list()
        .parallelStream()
        .map { task ->
          subscriptions
            .firstOrNull { subscription -> subscription.matches(task) }
            ?.let { activeSubscription: TaskSubscriptionHandle ->
              executorService.submit {  // in another thread
                try {
                  val processDefinitionKey = processDefinitionMetaDataResolver.getProcessDefinitionKey(task.processDefinitionId)
                  val candidates = taskService.getIdentityLinksForTask(task.id).toSet()
                  // create task information and set up the reason
                  val taskInformation =
                    if (deliveredTaskIds.contains(task.id)
                      && subscriptionRepository.getActiveSubscriptionForTask(task.id) == activeSubscription
                    ) {
                      // task was already delivered to this subscription
                      if (task.hasChanged()) {
                        if (task.hasChangedAssignees(candidates)) {
                          task.toTaskInformation(candidates, processDefinitionKey).withReason(TaskInformation.ASSIGN)
                        } else {
                          task.toTaskInformation(candidates, processDefinitionKey).withReason(TaskInformation.UPDATE)
                        }
                      } else {
                        // no change on the task
                        null
                      }

                    } else {
                      // task is new for this subscription
                      task.toTaskInformation(candidates, processDefinitionKey).withReason(TaskInformation.CREATE)
                    }
                  if (taskInformation != null) {
                    subscriptionRepository.activateSubscriptionForTask(task.id, activeSubscription)
                    synchronized(deliveredTasks) {
                      deliveredTasks[task.id] = taskInformation
                    }
                    val variables = taskService.getVariables(task.id).filterBySubscription(activeSubscription)
                    logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-037: delivering user task ${task.id}." }
                    activeSubscription.action.accept(taskInformation, variables)
                  } else {
                    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-040: skipping task ${task.id} since it is unchanged." }
                  }
                  // successfully handled the task, remove from already delivered
                  // since we do it from another thread, this must terminate before
                  // we can access the `deliveredTaskIds` for
                  synchronized(deliveredTaskIds) {
                    if (deliveredTaskIds.contains(task.id)) { // if the task was already there, remove it from unprocessed list
                      val successful = deliveredTaskIds.remove(task.id)
                      if (!successful) {
                        logger.error { "PROCESS-ENGINE-C7-EMBEDDED-038: error processing task ${task.id}, could not mark updated task as processed." }
                      }
                    }
                  }

                } catch (e: Exception) {
                  logger.error { "PROCESS-ENGINE-C7-EMBEDDED-038: error delivering task ${task.id}: ${e.message}" }
                  subscriptionRepository.deactivateSubscriptionForTask(taskId = task.id)
                }
              }
            }
        }.forEach { taskExecutionFuture ->
          taskExecutionFuture.get() // finish all before submitting the de-activation
        }

      // now we removed all still existing task ids from the list of already delivered
      // the remaining tasks doesn't exist in the engine, lets handle them
      deliveredTaskIds
        .parallelStream()
        .map { taskId ->
          executorService.submit { // also async
            // deactivate active subscription and handle termination
            logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-042: deactivating $taskId, task is gone." }
            subscriptionRepository.deactivateSubscriptionForTask(taskId)
              ?.termination
              ?.accept(
                TaskInformation(taskId = taskId, meta = emptyMap()).withReason(TaskInformation.DELETE)
              )
            synchronized(deliveredTasks) {
              deliveredTasks.remove(taskId)
            }
          }
        }.forEach { terminationExecutionFuture ->
          terminationExecutionFuture.get() // finish this thread too
        }
    } else {
      logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-039: pull user tasks disabled because of no active subscriptions" }
    }
  }

  /**
   * Checks if a task has changed.
   */
  private fun Task.hasChanged(): Boolean {
    val taskInformation = deliveredTasks[this.id]
    return !(
      taskInformation != null
        && taskInformation.meta["lastUpdatedDate"] == this.lastUpdated.toDateString()
        && taskInformation.meta["creationDate"] == this.createTime.toDateString()
      )
  }

  /**
   * Checks if a task assignees, candidate users and candidate users have changed.
   */
  private fun Task.hasChangedAssignees(candidates: Set<IdentityLink>): Boolean {
    val taskInformation = deliveredTasks[this.id]
    return !(taskInformation != null
      && taskInformation.meta["assignee"] == this.assignee
      && taskInformation.meta["candidateUsers"] == candidates.toUsersString()
      && taskInformation.meta["candidateGroups"] == candidates.toGroupsString()
      )
  }

  private fun TaskQuery.forSubscriptions(@Suppress("UNUSED_PARAMETER") subscriptions: List<TaskSubscriptionHandle>): TaskQuery {
    // TODO: narrow down, for the moment take all tasks matching tenants
    return this
      .active()
    // FIXME -> consider complex tent filtering
  }


  private fun TaskSubscriptionHandle.matches(task: Task): Boolean =
    (this.taskDescriptionKey == null
      || this.taskDescriptionKey == task.taskDefinitionKey
      || this.taskDescriptionKey == task.id
      ) && this.restrictions.all {
      when (it.key) {
        CommonRestrictions.EXECUTION_ID -> it.value == task.executionId
        CommonRestrictions.TENANT_ID -> it.value == task.tenantId
        CommonRestrictions.ACTIVITY_ID -> it.value == task.taskDefinitionKey
        CommonRestrictions.PROCESS_INSTANCE_ID -> it.value == task.processInstanceId
        CommonRestrictions.PROCESS_DEFINITION_ID -> it.value == task.processDefinitionId
        CommonRestrictions.PROCESS_DEFINITION_KEY -> it.value == processDefinitionMetaDataResolver.getProcessDefinitionKey(task.processDefinitionId)
        CommonRestrictions.PROCESS_DEFINITION_VERSION_TAG -> it.value == processDefinitionMetaDataResolver.getProcessDefinitionVersionTag(task.processDefinitionId)
        else -> false
      }
    }
}

