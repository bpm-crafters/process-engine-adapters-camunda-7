package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.RefreshableDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.ServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.impl.task.filterBySubscription
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.model.ExternalTaskFailureDto
import org.camunda.community.rest.client.model.FetchExternalTaskTopicDto
import org.camunda.community.rest.client.model.FetchExternalTasksDto
import org.camunda.community.rest.client.model.LockedExternalTaskDto
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * Delivers external tasks to subscriptions.
 * This implementation uses internal Java API and pulls tasks for delivery.
 */
class PullServiceTaskDelivery(
  private val externalTaskApiClient: ExternalTaskApiClient,
  private val processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  private val workerId: String,
  private val subscriptionRepository: SubscriptionRepository,
  private val maxTasks: Int,
  private val lockDurationInSeconds: Long,
  private val retryTimeoutInSeconds: Long,
  private val retries: Int,
  private val executorService: ExecutorService,
  private val valueMapper: ValueMapper,
  private val deserializeOnServer: Boolean
) : ServiceTaskDelivery, RefreshableDelivery {

  /**
   * Delivers all tasks found in the external service to corresponding subscriptions.
   */
  override fun refresh() {

    val subscriptions = subscriptionRepository.getTaskSubscriptions().filter { s -> s.taskType == TaskType.EXTERNAL }
    if (subscriptions.isNotEmpty()) {
      val deliveredTaskIds = subscriptionRepository.getDeliveredTaskIds(TaskType.EXTERNAL).toMutableList()
      logger.trace { "PROCESS-ENGINE-C7-REMOTE-030: pulling service tasks for subscriptions: $subscriptions" }
      val result = externalTaskApiClient
        .fetchAndLock(
          FetchExternalTasksDto(workerId, maxTasks)
            .forSubscriptions(subscriptions)
        )
      val lockedExternalTaskDtoList =
        requireNotNull(result.body) { "Could not subscribe to external tasks: $subscriptions, status code was ${result.statusCode}" }

      lockedExternalTaskDtoList
        .parallelStream()
        .map { lockedTask ->
          subscriptions
            .firstOrNull { subscription -> subscription.matches(lockedTask) }
            ?.let { activeSubscription ->
              executorService.submit {  // in another thread
                try {
                  val taskInformation =
                    if (deliveredTaskIds.contains(lockedTask.id) && subscriptionRepository.getActiveSubscriptionForTask(lockedTask.id) == activeSubscription) {
                      null
                    } else {
                      // create task information and set up the reason
                      lockedTask.toTaskInformation(processDefinitionMetaDataResolver).withReason(TaskInformation.CREATE)
                    }
                  if (taskInformation != null) {
                    subscriptionRepository.activateSubscriptionForTask(lockedTask.id, activeSubscription)
                    val variables = valueMapper.mapDtos(lockedTask.variables).filterBySubscription(activeSubscription)
                    logger.debug { "PROCESS-ENGINE-C7-REMOTE-031: delivering service task ${lockedTask.id}." }
                    activeSubscription.action.accept(taskInformation, variables)
                    logger.debug { "PROCESS-ENGINE-C7-REMOTE-032: successfully delivered service task ${lockedTask.id}." }
                  } else {
                    logger.trace { "PROCESS-ENGINE-C7-REMOTE-041: skipping task ${lockedTask.id} since it is unchanged." }
                  }
                  // remove from already delivered
                  deliveredTaskIds.remove(lockedTask.id)
                } catch (e: Exception) {
                  val jobRetries: Int = lockedTask.retries ?: retries
                  logger.error { "PROCESS-ENGINE-C7-REMOTE-033: failing delivering task ${lockedTask.id}: ${e.message}" }
                  externalTaskApiClient.handleFailure(
                    lockedTask.id,
                    ExternalTaskFailureDto().apply {
                      this.workerId = this@PullServiceTaskDelivery.workerId
                      this.retries = jobRetries - 1
                      this.retryTimeout = retryTimeoutInSeconds * 1000 // from seconds to millis
                      this.errorDetails = e.stackTraceToString()
                      this.errorMessage = e.message
                    }
                  )
                  logger.error { "PROCESS-ENGINE-C7-REMOTE-034: successfully failed delivering task ${lockedTask.id}: ${e.message}" }
                }
              }
            }
        }.forEach { taskExecutionFuture -> taskExecutionFuture.get() }
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
        }
      }.forEach { taskTerminationFuture -> taskTerminationFuture.get() }

    } else {
      logger.trace { "PROCESS-ENGINE-C7-REMOTE-035: Pull external tasks disabled because of no active subscriptions" }
    }
  }

  private fun FetchExternalTasksDto.forSubscriptions(subscriptions: List<TaskSubscriptionHandle>): FetchExternalTasksDto {
    subscriptions
      .map { it.taskDescriptionKey to it.restrictions }
      .distinctBy { it.first }
      .forEach { (topic, _) ->
        this.addTopicsItem(
          FetchExternalTaskTopicDto(
            topic,
            lockDurationInSeconds * 1000 // convert to ms
          ).apply {
            this.deserializeValues = this@PullServiceTaskDelivery.deserializeOnServer

            /*
            TODO: decide should we do it here? or is matches functionality enough?

            if (restrictions.containsKey(CommonRestrictions.BUSINESS_KEY)) {
              this.businessKey = restrictions.getValue(CommonRestrictions.BUSINESS_KEY) // support business key
            }
            require( !(restrictions.containsKey(CommonRestrictions.TENANT_ID) && restrictions.containsKey(CommonRestrictions.WITHOUT_TENANT_ID) ))
              { "Illegal restriction. Either set required tenant id or without tenant but not both at the same time."}
            if (restrictions.containsKey(CommonRestrictions.TENANT_ID)) {
              this.tenantIdIn = listOf(restrictions.getValue(CommonRestrictions.TENANT_ID))
              this.withoutTenantId = false
            }
            if (restrictions.containsKey(CommonRestrictions.WITHOUT_TENANT_ID)) {
              this.withoutTenantId = true
            }
            */
          }
        )
      }
    return this
  }

  private fun TaskSubscriptionHandle.matches(task: LockedExternalTaskDto): Boolean =
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
