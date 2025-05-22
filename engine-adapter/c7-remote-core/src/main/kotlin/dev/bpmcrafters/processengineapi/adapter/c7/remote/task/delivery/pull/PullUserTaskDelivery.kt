package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.*
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.client.model.IdentityLinkDto
import org.camunda.community.rest.client.model.TaskQueryDto
import org.camunda.community.rest.client.model.TaskWithAttachmentAndCommentDto
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * Delivers user tasks to subscriptions.
 * Uses internal Java API for pulling tasks.
 */
class PullUserTaskDelivery(
  processDefinitionApiClient: ProcessDefinitionApiClient,
  private val taskApiClient: TaskApiClient,
  private val subscriptionRepository: SubscriptionRepository,
  private val executorService: ExecutorService,
  private val valueMapper: ValueMapper,
  private val deserializeOnServer: Boolean
) : UserTaskDelivery, RefreshableDelivery {

  private val cachingProcessDefinitionKeyResolver = CachingProcessDefinitionKeyResolver(processDefinitionApiClient)
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
      (deliveredTasks.keys().asSequence().filterNot { deliveredTaskIds.contains(it) }).forEach(deliveredTasks::remove)

      logger.trace { "PROCESS-ENGINE-C7-REMOTE-036: pulling user tasks for subscriptions: $subscriptions" }
      val result = taskApiClient
        .queryTasks(0, Integer.MAX_VALUE, TaskQueryDto().forSubscriptions(subscriptions))

      val taskDtoList = requireNotNull(result.body) { "Could not fetch user tasks for subscriptions: $subscriptions, status code was ${result.statusCode}" }

      taskDtoList
        .parallelStream()
        .map { task ->
          subscriptions
            .firstOrNull { subscription -> subscription.matches(task) }
            ?.let { activeSubscription ->
              executorService.submit {  // in another thread
                try {
                  val processDefinitionKey = cachingProcessDefinitionKeyResolver.getProcessDefinitionKey(task.processDefinitionId)
                  val identityResult = taskApiClient.getIdentityLinks(task.id, null)
                  val candidates =
                    requireNotNull(identityResult.body) { "Could not retrieve identity links for task ${task.id}, status was ${identityResult.statusCode}" }.toSet()

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
                    deliveredTasks[task.id] = taskInformation
                    val variableResult = taskApiClient.getTaskVariables(task.id, deserializeOnServer) // FIXME?
                    val variables =
                      requireNotNull(variableResult.body) { "Could not retrieve variables for task ${task.id}, status was ${variableResult.statusCode}" }
                        .filterBySubscription(activeSubscription)
                        .let { dtoList -> valueMapper.mapDtos(variables = dtoList, deserializeValues = true) }
                    logger.debug { "PROCESS-ENGINE-C7-REMOTE-037: delivering user task ${task.id}." }
                    activeSubscription.action.accept(taskInformation, variables)
                  } else {
                    logger.trace { "PROCESS-ENGINE-C7-REMOTE-040: skipping task ${task.id} since it is unchanged." }
                  }
                  // successfully handled the task, remove from already delivered
                  // since we do it from another thread, this must terminate before
                  // we can access the `deliveredTaskIds` for
                  deliveredTaskIds.remove(task.id)

                } catch (e: Exception) {
                  logger.error { "PROCESS-ENGINE-C7-REMOTE-038: error delivering task ${task.id}: ${e.message}" }
                  subscriptionRepository.deactivateSubscriptionForTask(taskId = task.id)
                }
              }
            }
        }.forEach { taskExecutionFuture ->
          taskExecutionFuture.get()
        }

      // now we removed all still existing task ids from the list of already delivered
      // the remaining tasks doesn't exist in the engine, lets handle them
      deliveredTaskIds.parallelStream().map { taskId ->
        executorService.submit {
          // deactivate active subscription and handle termination
          subscriptionRepository.deactivateSubscriptionForTask(taskId)?.termination?.accept(
            TaskInformation(
              taskId = taskId,
              meta = emptyMap()
            ).withReason(TaskInformation.DELETE)
          )
          // deactivate active subscription and handle termination
          logger.trace { "PROCESS-ENGINE-C7-REMOTE-042: deactivating $taskId, task is gone." }
          deliveredTasks.remove(taskId)
        }
      }.forEach { taskTerminationFuture -> taskTerminationFuture.get() }
    } else {
      logger.trace { "PROCESS-ENGINE-C7-REMOTE-039: pull user tasks disabled because of no active subscriptions" }
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun TaskQueryDto.forSubscriptions(subscriptions: List<TaskSubscriptionHandle>): TaskQueryDto {
    return this.apply {
      this.active = true
    }
    // TODO -> consider complex tenant filtering
  }

  /**
   * Checks if a task has changed.
   */
  private fun TaskWithAttachmentAndCommentDto.hasChanged(): Boolean {
    val taskInformation = deliveredTasks[this.id]
    return !(
      taskInformation != null
        && taskInformation.meta["lastUpdatedDate"] == this.lastUpdated.toDateString()
        && taskInformation.meta["creationDate"] == this.created.toDateString()
      )
  }

  /**
   * Checks if a task assignees, candidate users and candidate users have changed.
   */
  private fun TaskWithAttachmentAndCommentDto.hasChangedAssignees(candidates: Set<IdentityLinkDto>): Boolean {
    val taskInformation = deliveredTasks[this.id]
    return !(taskInformation != null
      && taskInformation.meta["assignee"] == this.assignee
      && taskInformation.meta["candidateUsers"] == candidates.toUsersString()
      && taskInformation.meta["candidateGroups"] == candidates.toGroupsString()
      )
  }


  private fun TaskSubscriptionHandle.matches(task: TaskWithAttachmentAndCommentDto): Boolean =
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

