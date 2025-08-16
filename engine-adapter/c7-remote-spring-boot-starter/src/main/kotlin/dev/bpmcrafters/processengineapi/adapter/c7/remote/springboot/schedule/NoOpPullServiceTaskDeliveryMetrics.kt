package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.DropReason
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.FetchAndLockSkipReason
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

@Component
@ConditionalOnMissingBean(MeterRegistry::class)
class NoOpPullServiceTaskDeliveryMetrics : PullServiceTaskDeliveryMetrics {

  override fun incrementFetchedAndLockedTasksCounter(topic: String, amount: Int) {}

  override fun incrementFetchAndLockTasksSkippedCounter(reason: FetchAndLockSkipReason) {}

  override fun incrementDroppedTasksCounter(topic: String, reason: DropReason) {}

  override fun incrementFailedTasksCounter(topic: String) {}

  override fun incrementCompletedTasksCounter(topic: String) {}

  override fun incrementTerminatedTasksCounter(topic: String) {}

  override fun recordTaskQueueTime(topic: String, duration: Duration) {}

  override fun recordTaskExecutionTime(topic: String, duration: Duration) {}

  override fun registerExecutorThreadsUsedGauge(valueFn: Supplier<Int>) {}

  override fun registerExecutorQueueCapacityGauge(valueFn: Supplier<Int>) {}

  override fun registerStillLockedTasksGauge(number: AtomicInteger) {}
}
