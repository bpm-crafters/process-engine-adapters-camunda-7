package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

interface PullServiceTaskDeliveryMetrics {

  fun incrementFetchedAndLockedTasksCounter(topic: String, amount: Int)

  fun incrementFetchAndLockTasksSkippedCounter(reason: FetchAndLockSkipReason)

  fun incrementDroppedTasksCounter(topic: String, reason: DropReason)

  /**
   * Currently, this counter only records failures caused by itself
   * because technical and BPM errors are handled by [Process Engine Worker](https://github.com/bpm-crafters/process-engine-worker).
   */
  fun incrementFailedTasksCounter(topic: String)

  /**
   * This counter is also incremented when a task fails, because [Process Engine Worker](https://github.com/bpm-crafters/process-engine-worker)
   * handles task failures.
   *
   * @see incrementFailedTasksCounter
   */
  fun incrementCompletedTasksCounter(topic: String)

  fun incrementTerminatedTasksCounter(topic: String)

  fun recordTaskQueueTime(topic: String, duration: Duration)

  fun recordTaskExecutionTime(topic: String, duration: Duration)

  fun registerExecutorThreadsUsedGauge(valueFn: Supplier<Int>)

  fun registerExecutorQueueCapacityGauge(valueFn: Supplier<Int>)

  fun registerStillLockedTasksGauge(number: AtomicInteger)

  enum class FetchAndLockSkipReason {
    NO_SUBSCRIPTIONS,
    QUEUE_FULL,
  }

  enum class DropReason {
    NO_MATCHING_SUBSCRIPTIONS,
    EXPIRED_WHILE_IN_QUEUE,
  }
}
