package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.DropReason
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.FetchAndLockSkipReason
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class DefaultPullServiceTaskDeliveryMetrics(
  private val registry: MeterRegistry,
) : PullServiceTaskDeliveryMetrics {

  companion object {
    const val PREFIX = "bpmcrafters.c7.remote.scheduled.external.tasks"
    const val TOPIC = "topic"
    const val REASON = "reason"
  }

  override fun incrementFetchedAndLockedTasksCounter(topic: String, amount: Int) =
    registry.counter("${PREFIX}.fetched.and.locked", TOPIC, topic).increment(amount.toDouble())

  override fun incrementFetchAndLockTasksSkippedCounter(reason: FetchAndLockSkipReason) =
    registry.counter("${PREFIX}.fetch.and.lock.skipped", REASON, reason.name).increment()

  override fun incrementDroppedTasksCounter(topic: String, reason: DropReason) =
    registry.counter("${PREFIX}.dropped", TOPIC, topic, REASON, reason.name).increment()

  override fun incrementFailedTasksCounter(topic: String) =
    registry.counter("${PREFIX}.failed", TOPIC, topic).increment()

  override fun incrementCompletedTasksCounter(topic: String) =
    registry.counter("${PREFIX}.completed", TOPIC, topic).increment()

  override fun incrementTerminatedTasksCounter(topic: String) =
    registry.counter("${PREFIX}.terminated", TOPIC, topic).increment()

  override fun recordTaskQueueTime(topic: String, duration: Duration) =
    registry.timer("${PREFIX}.queue", TOPIC, topic).record(duration)

  override fun recordTaskExecutionTime(topic: String, duration: Duration) =
    registry.timer("${PREFIX}.execution", TOPIC, topic).record(duration)

  override fun registerExecutorThreadsUsedGauge(valueFn: Supplier<Int>) {
    Gauge.builder("${PREFIX}.executor.threads.used", valueFn).register(registry)
  }

  override fun registerExecutorQueueCapacityGauge(valueFn: Supplier<Int>) {
    Gauge.builder("${PREFIX}.executor.queue.capacity", valueFn).register(registry)
  }

  override fun registerStillLockedTasksGauge(number: AtomicInteger) {
    registry.gauge("${PREFIX}.still.locked", number)
  }
}
