package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion

import java.util.function.Function

@FunctionalInterface
interface FailureRetrySupplier : Function<String, FailureRetrySupplier.FailureRetry> {

  /**
   * Returns the default timeout in seconds when the caller supplies the retry count.
   * Stateful suppliers can override this to avoid consuming a retry.
   */
  fun retryTimeout(taskId: String): Long = apply(taskId).retryTimeout

  /**
   * Releases any retry state for a task that has ended.
   */
  fun clear(taskId: String) {}

  data class FailureRetry(
    val retryCount: Int,
    val retryTimeout: Long
  )
}
