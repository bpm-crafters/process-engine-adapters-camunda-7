package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.ExternalServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDeliveryMetrics.DropReason.EXPIRED_WHILE_IN_QUEUE
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDeliveryMetrics.DropReason.NO_MATCHING_SUBSCRIPTIONS
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDeliveryMetrics.FetchAndLockSkipReason.NO_SUBSCRIPTIONS
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDeliveryMetrics.FetchAndLockSkipReason.QUEUE_FULL
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.impl.task.filterBySubscription
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskInformation.Companion.CREATE
import dev.bpmcrafters.processengineapi.task.TaskType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.ExternalTaskService
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryBuilder
import org.camunda.bpm.engine.externaltask.LockedExternalTask
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.Callable
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * Delivers external tasks to subscriptions.
 * This implementation uses internal Java API and pulls tasks for delivery.
 */
class EmbeddedPullServiceTaskDelivery(
  private val externalTaskService: ExternalTaskService,
  private val workerId: String,
  private val subscriptionRepository: SubscriptionRepository,
  private val maxTasks: Int,
  private val lockDurationInSeconds: Long,
  private val retryTimeoutInSeconds: Long,
  private val retries: Int,
  private val executor: ThreadPoolExecutor,
  private val metrics: EmbeddedPullServiceTaskDeliveryMetrics
) : ExternalServiceTaskDelivery, RefreshableDelivery {

  internal val stillLockedTasksGauge = AtomicInteger()

  init {
    metrics.registerExecutorThreadsUsedGauge(executor::getActiveCount)
    metrics.registerExecutorQueueCapacityGauge(executor.queue::remainingCapacity)
    metrics.registerStillLockedTasksGauge(stillLockedTasksGauge)
  }

  /**
   * Delivers all tasks found in the external service to corresponding subscriptions.
   */
  override fun refresh() {
    deliverNewTasks()
    cleanUpTerminatedTasks()
  }

  internal fun deliverNewTasks() {
    val subscriptions = subscriptionRepository.getTaskSubscriptions().filter { s -> s.taskType==TaskType.EXTERNAL }
    if (subscriptions.isEmpty()) {
      logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-035: Pull external tasks disabled because of no active subscriptions" }
      metrics.incrementFetchAndLockTasksSkippedCounter(NO_SUBSCRIPTIONS)
      return
    }

    val tasksToFetch = maxTasks.coerceAtMost(executor.queue.remainingCapacity())
    if (tasksToFetch==0) {
      logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-044: Task executor queue is full, skipping task fetch" }
      metrics.incrementFetchAndLockTasksSkippedCounter(QUEUE_FULL)
      return
    }

    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-030: pulling $tasksToFetch service tasks for subscriptions: $subscriptions" }
    val lockedTasks = externalTaskService
      .fetchAndLock(tasksToFetch, workerId)
      .forSubscriptions(subscriptions)
      .execute()

    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-045: pulled ${lockedTasks.size} service tasks" }
    lockedTasks
      .groupBy { it.topicName }
      .forEach { (topic, tasks) -> metrics.incrementFetchedAndLockedTasksCounter(topic!!, tasks.size) }

    val taskActionHandlerCallables = lockedTasks
      .asSequence()
      .map { lockedTask -> lockedTask to subscriptions.firstOrNull { subscription -> subscription.matches(lockedTask) } }
      .filter { (lockedTask, subscription) ->
        val keep = subscription!=null
        if (!keep) {
          metrics.incrementDroppedTasksCounter(lockedTask.topicName!!, NO_MATCHING_SUBSCRIPTIONS)
        }
        keep
      }
      .map { (lockedTask, activeSubscription) -> createTaskActionHandlerCallable(lockedTask, activeSubscription!!) }
      .toList()

    taskActionHandlerCallables
      .map { executor.submit(it) }
      .forEach { it.get() }

  }

  internal fun createTaskActionHandlerCallable(lockedTask: LockedExternalTask, activeSubscription: TaskSubscriptionHandle): Callable<Unit> =
    Callable {
      // make sure the task has not expired waiting in the queue for the execution
      val start = OffsetDateTime.now().toInstant()
      val lockExpirationInstant = lockedTask.lockExpirationTime.toInstant()
      val timePassedSinceLockAcquisition = Duration.between(lockExpirationInstant.minusSeconds(lockDurationInSeconds), start)
      metrics.recordTaskQueueTime(lockedTask.topicName!!, timePassedSinceLockAcquisition)
      if (start.isBefore(lockExpirationInstant)) {
        try {
          if (subscriptionRepository.getActiveSubscriptionForTask(lockedTask.id) == activeSubscription) {
            // task is already delivered to the current subscription, nothing to do
            logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-041: skipping task ${lockedTask.id} since it is unchanged." }
          } else {
            // create task information and set up the reason
            val taskInformation = lockedTask.toTaskInformation().withReason(CREATE)
            subscriptionRepository.activateSubscriptionForTask(lockedTask.id, activeSubscription)
            val variables = lockedTask.variables.filterBySubscription(activeSubscription)
            logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-031: delivering service task ${lockedTask.id}." }
            activeSubscription.action.accept(taskInformation, variables)
            logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-032: successfully delivered service task ${lockedTask.id}." }
          }
          logger.debug { "PROCESS-ENGINE-C7-REMOTE-032: successfully delivered service task ${lockedTask.id}." }
          metrics.incrementCompletedTasksCounter(lockedTask.topicName!!)
        } catch (e: Exception) {
          val jobRetries: Int = lockedTask.retries?.minus(1) ?: retries
          logger.error { "PROCESS-ENGINE-C7-EMBEDDED-033: failing delivering task ${lockedTask.id}: ${e.message}" }
          metrics.incrementFailedTasksCounter(lockedTask.topicName!!)
          externalTaskService.handleFailure(lockedTask.id, workerId, e.message, jobRetries, retryTimeoutInSeconds * 1000)
          subscriptionRepository.deactivateSubscriptionForTask(taskId = lockedTask.id)
          logger.error { "PROCESS-ENGINE-C7-EMBEDDED-034: successfully failed delivering task ${lockedTask.id}: ${e.message}" }

        } finally {
          metrics.recordTaskExecutionTime(lockedTask.topicName!!, Duration.between(start, OffsetDateTime.now()))
        }
      } else {
        metrics.incrementDroppedTasksCounter(lockedTask.topicName!!, EXPIRED_WHILE_IN_QUEUE)
      }

    }

  internal fun cleanUpTerminatedTasks() {

    // retrieve external tasks locked for configured worker id
    val stillLockedTasks = externalTaskService.createExternalTaskQuery()
      .workerId(workerId)
      .locked()
      .list()

    val stillLockedTaskIds = stillLockedTasks.map { dto -> dto.id!! }.toSet()
    stillLockedTasksGauge.set(stillLockedTaskIds.size)

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

    taskTerminationHandlerCallables
      .map { executor.submit(it) }
      .forEach { it.get() }
  }

  internal fun createTaskTerminationHandlerCallable(taskId: String): Callable<Unit> = Callable {
    // deactivate active subscription and handle termination
    val taskSubscriptionHandle = subscriptionRepository.deactivateSubscriptionForTask(taskId)
    if (taskSubscriptionHandle!=null) {
      taskSubscriptionHandle.termination.accept(
        TaskInformation(
          taskId = taskId,
          meta = emptyMap()
        ).withReason(TaskInformation.DELETE)
      )
      metrics.incrementTerminatedTasksCounter(taskSubscriptionHandle.taskDescriptionKey ?: "?")
    }
  }


  private fun ExternalTaskQueryBuilder.forSubscriptions(subscriptions: List<TaskSubscriptionHandle>): ExternalTaskQueryBuilder {
    subscriptions
      .filter { it.taskDescriptionKey!=null }
      .distinctBy { it.taskDescriptionKey }
      .forEach { subscription ->
        val lockDurationInMilliseconds = getLockDurationForSubscription(subscription)
        this
          .topic(subscription.taskDescriptionKey, lockDurationInMilliseconds) // convert to ms
          .enableCustomObjectDeserialization()
        // FIXME -> consider complex tenant filtering
      }
    return this
  }

  private fun getLockDurationForSubscription(subscription: TaskSubscriptionHandle): Long {
    val customLockDuration = subscription.restrictions["workerLockDurationInMilliseconds"]
    return customLockDuration?.toLong() ?: (lockDurationInSeconds * 1000)
  }

  private fun TaskSubscriptionHandle.matches(task: LockedExternalTask): Boolean {
    return this.taskType==TaskType.EXTERNAL
      && (this.taskDescriptionKey==null || this.taskDescriptionKey==task.topicName)
      && this.restrictions.all {
      when (it.key) {
        CommonRestrictions.EXECUTION_ID -> it.value==task.executionId
        CommonRestrictions.ACTIVITY_ID -> it.value==task.activityId
        CommonRestrictions.BUSINESS_KEY -> it.value==task.businessKey
        CommonRestrictions.TENANT_ID -> it.value==task.tenantId
        CommonRestrictions.PROCESS_INSTANCE_ID -> it.value==task.processInstanceId
        CommonRestrictions.PROCESS_DEFINITION_KEY -> it.value==task.processDefinitionKey
        CommonRestrictions.PROCESS_DEFINITION_ID -> it.value==task.processDefinitionId
        CommonRestrictions.PROCESS_DEFINITION_VERSION_TAG -> it.value==task.processDefinitionVersionTag
        else -> false
      }
    }
  }
}
