package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * No-op implementation of [EmbeddedPullServiceTaskDeliveryMetrics].
 */
class NoOpPullServiceTaskDeliveryMetrics : EmbeddedPullServiceTaskDeliveryMetrics {

  override fun incrementFetchedAndLockedTasksCounter(topic: String, amount: Int) {}

  override fun incrementFetchAndLockTasksSkippedCounter(reason: EmbeddedPullServiceTaskDeliveryMetrics.FetchAndLockSkipReason) {}

  override fun incrementDroppedTasksCounter(topic: String, reason: EmbeddedPullServiceTaskDeliveryMetrics.DropReason) {}

  override fun incrementFailedTasksCounter(topic: String) {}

  override fun incrementCompletedTasksCounter(topic: String) {}

  override fun incrementTerminatedTasksCounter(topic: String) {}

  override fun recordTaskQueueTime(topic: String, duration: Duration) {}

  override fun recordTaskExecutionTime(topic: String, duration: Duration) {}

  override fun registerExecutorThreadsUsedGauge(valueFn: Supplier<Int>) {}

  override fun registerExecutorQueueCapacityGauge(valueFn: Supplier<Int>) {}

  override fun registerStillLockedTasksGauge(number: AtomicInteger) {}
}
