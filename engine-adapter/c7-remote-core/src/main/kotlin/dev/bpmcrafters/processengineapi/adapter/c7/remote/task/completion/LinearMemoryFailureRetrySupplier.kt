package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion

import java.util.LinkedHashMap

// Tasks removed between deliveries may never reach the completion API.
private const val MAX_TRACKED_TASKS = 10_000

class LinearMemoryFailureRetrySupplier(
  private val retry: Int,
  private val retryTimeout: Long
) : FailureRetrySupplier {

  private val taskFailures = LinkedHashMap<String, Int>(16, 0.75f, true)

  @Synchronized
  override fun apply(taskId: String): FailureRetrySupplier.FailureRetry {
    val retriesLeft = (taskFailures[taskId] ?: retry).coerceAtLeast(1) - 1
    taskFailures[taskId] = retriesLeft
    if (taskFailures.size > MAX_TRACKED_TASKS) {
      taskFailures.pollFirstEntry()
    }
    return FailureRetrySupplier.FailureRetry(retryCount = retriesLeft, retryTimeout = retryTimeout)
  }

  override fun retryTimeout(taskId: String): Long = retryTimeout

  @Synchronized
  override fun clear(taskId: String) {
    taskFailures.remove(taskId)
  }
}
