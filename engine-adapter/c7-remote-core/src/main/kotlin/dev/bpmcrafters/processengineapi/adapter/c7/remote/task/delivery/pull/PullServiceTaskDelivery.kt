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
import dev.bpmcrafters.processengineapi.task.TaskInformation.Companion.CREATE
import dev.bpmcrafters.processengineapi.task.TaskType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.model.*
import org.camunda.community.rest.variables.ValueMapper
import java.time.OffsetDateTime
import java.util.concurrent.Callable
import java.util.concurrent.ThreadPoolExecutor

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
  private val executor: ThreadPoolExecutor,
  private val valueMapper: ValueMapper,
  private val deserializeOnServer: Boolean,
) : ServiceTaskDelivery, RefreshableDelivery {

  /**
   * Delivers all tasks found in the external service to corresponding subscriptions.
   */
  override fun refresh() {
      cleanUpTerminatedTasks()
      deliverNewTasks()
  }

  internal fun deliverNewTasks() {
    val subscriptions = subscriptionRepository.getTaskSubscriptions().filter { s -> s.taskType == TaskType.EXTERNAL }
    if (subscriptions.isEmpty()) {
      logger.trace { "PROCESS-ENGINE-C7-REMOTE-035: Pull external tasks disabled because of no active subscriptions" }
      return
    }

    val tasksToFetch = maxTasks.coerceAtMost(executor.queue.remainingCapacity())
    if (tasksToFetch == 0) {
      logger.trace { "PROCESS-ENGINE-C7-REMOTE-041: Task executor queue is full, skipping task fetch" }
      return
    }

    logger.trace { "PROCESS-ENGINE-C7-REMOTE-030: pulling $tasksToFetch service tasks for subscriptions: $subscriptions" }
    val result = externalTaskApiClient
      .fetchAndLock(
        FetchExternalTasksDto(workerId, tasksToFetch)
          .forSubscriptions(subscriptions)
          .usePriority(true)
          .sorting(mutableListOf(
            FetchExternalTasksDtoSortingInner()
              .sortBy(FetchExternalTasksDtoSortingInner.SortByEnum.CREATE_TIME)
              .sortOrder(FetchExternalTasksDtoSortingInner.SortOrderEnum.ASC)
          ))
      )

    val lockedTasks =
      requireNotNull(result.body) { "Could not subscribe to external tasks: $subscriptions, status code was ${result.statusCode}" }

    logger.trace { "PROCESS-ENGINE-C7-REMOTE-042: pulled ${lockedTasks.size} service tasks" }

    val taskActionHandlerCallables = lockedTasks
      .asSequence()
      .map { lockedTask -> lockedTask to subscriptions.firstOrNull { subscription -> matches(lockedTask, subscription) } }
      .filter { it.second != null }
      .map { (lockedTask, activeSubscription) -> createTaskActionHandlerCallable(lockedTask, activeSubscription!!) }
      .toList()

    taskActionHandlerCallables.forEach { executor.submit(it) }
  }

  internal fun createTaskActionHandlerCallable(lockedTask: LockedExternalTaskDto, activeSubscription: TaskSubscriptionHandle): Callable<Unit> = Callable {
    // make sure the task has not expired waiting in the queue for the execution
    if (OffsetDateTime.now().isBefore(lockedTask.lockExpirationTime)) {
      try {
        subscriptionRepository.activateSubscriptionForTask(lockedTask.id, activeSubscription)
        val variables = valueMapper.mapDtos(lockedTask.variables).filterBySubscription(activeSubscription)
        logger.debug { "PROCESS-ENGINE-C7-REMOTE-031: delivering service task ${lockedTask.id}." }
        val taskInformation = toTaskInformation(lockedTask).withReason(CREATE)
        activeSubscription.action.accept(taskInformation, variables)
        logger.debug { "PROCESS-ENGINE-C7-REMOTE-032: successfully delivered service task ${lockedTask.id}." }
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

  internal fun toTaskInformation(lockedTask: LockedExternalTaskDto) = lockedTask.toTaskInformation(processDefinitionMetaDataResolver)

  internal fun cleanUpTerminatedTasks() {
    // retrieve external tasks locked for configured worker id
    val stillLockedTasksResult = externalTaskApiClient.queryExternalTasks(
      null,
      null,
      ExternalTaskQueryDto()
        .workerId(workerId)
        .locked(true)
        .sorting(listOf(
          ExternalTaskQueryDtoSortingInner()
            .sortBy(ExternalTaskQueryDtoSortingInner.SortByEnum.CREATE_TIME)
            .sortOrder(ExternalTaskQueryDtoSortingInner.SortOrderEnum.ASC))
        )
    )
    val stillLockedTaskIds =
      requireNotNull(stillLockedTasksResult.body) { "Could not fetch still locked tasks for worker: $workerId, status code was ${stillLockedTasksResult.statusCode}" }
        .map { dto -> dto.id }
        .toSet()

    val deliveredTaskIdsMissingInEngine =
      subscriptionRepository.getDeliveredTaskIds(TaskType.EXTERNAL)
        .toMutableSet()
        .apply {
          removeAll(stillLockedTaskIds)
        }
        .toList()
        .let {
          it.subList(0, it.size.coerceAtMost(executor.queue.remainingCapacity())) // make sure the executor can accept our callable
        }

    // now we removed all still existing task ids from the list of already delivered
    // the remaining tasks don't exist in the engine, lets handle them
    val taskTerminationHandlerCallables = deliveredTaskIdsMissingInEngine.map { createTaskTerminationHandlerCallable(it) }

    taskTerminationHandlerCallables.forEach { executor.submit(it) }
  }

  internal fun createTaskTerminationHandlerCallable(taskId: String): Callable<Unit> = Callable {
    // deactivate active subscription and handle termination
    subscriptionRepository.deactivateSubscriptionForTask(taskId)?.termination?.accept(
      TaskInformation(
        taskId = taskId,
        meta = emptyMap()
      ).withReason(TaskInformation.DELETE)
    )
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

  internal fun matches(task: LockedExternalTaskDto, subscription: TaskSubscriptionHandle): Boolean =
    (subscription.taskDescriptionKey == null
      || subscription.taskDescriptionKey == task.topicName)
      && subscription.restrictions.all {
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
