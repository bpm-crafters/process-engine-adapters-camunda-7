package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * This interface is used by `PullServiceTaskDelivery` to report all its metrics to.
 * Depending on your environment and needs, you can report those information to a metrics store of your choice.
 *
 * Note that the `dev.bpm-crafters.process-engine-adapters:process-engine-adapter-camunda-platform-c7-remote-spring-boot-starter`
 * artifact already has 2 implementations:
 * * A default implementation that uses Micrometer
 * * and a no-op implementation that does nothing and is used if no other bean of this type exists, e.g., if Micrometer is not available.
 */
interface PullServiceTaskDeliveryMetrics {

  /**
   * Increments the counter for fetched-and-locked tasks by the specified amount.
   *
   * @param topic The topic to increment the counter for.
   * @param amount The amount to increment the counter by.
   */
  fun incrementFetchedAndLockedTasksCounter(topic: String, amount: Int)

  /**
   * Increments the counter for fetch-and-lock skips by 1.
   *
   * @param reason The reason why the fetch-and-lock has been skipped.
   */
  fun incrementFetchAndLockTasksSkippedCounter(reason: FetchAndLockSkipReason)

  /**
   * Increments the counter for dropped tasks by 1.
   *
   * @param topic The topic of the task that has been dropped.
   * @param reason The reason why the task has been dropped.
   */
  fun incrementDroppedTasksCounter(topic: String, reason: DropReason)

  /**
   * Increments the counter for failed tasks by 1.
   *
   * Currently, this counter only records failures caused by `PullServiceTaskDelivery`
   * because technical and BPM errors are handled by [Process Engine Worker](https://github.com/bpm-crafters/process-engine-worker).
   *
   * @param topic The topic of the task that has failed.
   *
   * @see incrementCompletedTasksCounter
   */
  fun incrementFailedTasksCounter(topic: String)

  /**
   * Increments the counter for completed tasks by 1.
   *
   * Currently, this counter is also incremented when a task fails outside of `PullServiceTaskDelivery`
   * because [Process Engine Worker](https://github.com/bpm-crafters/process-engine-worker) handles task failures.
   *
   * @param topic The topic of the task that has completed.
   *
   * @see incrementFailedTasksCounter
   */
  fun incrementCompletedTasksCounter(topic: String)

  /**
   * Increments the counter for terminated tasks by 1.
   *
   * @param topic The topic of the task that has been terminated.
   */
  fun incrementTerminatedTasksCounter(topic: String)

  /**
   * Records a task queue time.
   *
   * @param topic The topic of the task to record the queue time for.
   * @param duration Indicates how long the task has been in the queue.
   */
  fun recordTaskQueueTime(topic: String, duration: Duration)

  /**
   * Records a task execution time.
   *
   * @param topic The topic of the task to record the execution time for.
   * @param duration Indicates how long it took to execute the task.
   */
  fun recordTaskExecutionTime(topic: String, duration: Duration)

  /**
   * Registers the executor threads used gauge. This method is called during boot and only once.
   *
   * @param valueFn The function that returns the number of executor threads that are currently in use.
   */
  fun registerExecutorThreadsUsedGauge(valueFn: Supplier<Int>)

  /**
   * Registers the executor queue capacity gauge. This method is called during boot and only once.
   *
   * @param valueFn The function that returns the number of free slots in the executor queue.
   */
  fun registerExecutorQueueCapacityGauge(valueFn: Supplier<Int>)

  /**
   * Registers the still locked tasks gauge. This method is called during boot and only once.
   *
   * @param number The number of still locked tasks. This number is constantly being updated.
   */
  fun registerStillLockedTasksGauge(number: AtomicInteger)

  /**
   * The fetch-and-lock skip reason indicates why a fetch-and-lock has been skipped.
   */
  enum class FetchAndLockSkipReason {

    /**
     * The `PullServiceTaskDelivery` has no (external task) subscriptions.
     */
    NO_SUBSCRIPTIONS,

    /**
     * The executor queue is full. In other words, the remaining queue capacity has dropped to 0.
     */
    QUEUE_FULL,
  }

  /**
   * The drop reason indicates why a task has been dropped before it had the chance to be executed.
   */
  enum class DropReason {

    /**
     * The `PullServiceTaskDelivery` did not find a matching (external task) subscription.
     */
    NO_MATCHING_SUBSCRIPTIONS,

    /**
     * The task has expired while being stuck in the executor queue. In other words, its lock has already expired.
     * This can indicate multiple things:
     * * The executor queue is too big.
     * * The number of executor threads is too small.
     * * Long running tasks have clogged up the executor and/or queue.
     * * The lock time is too short.
     */
    EXPIRED_WHILE_IN_QUEUE,
  }
}
