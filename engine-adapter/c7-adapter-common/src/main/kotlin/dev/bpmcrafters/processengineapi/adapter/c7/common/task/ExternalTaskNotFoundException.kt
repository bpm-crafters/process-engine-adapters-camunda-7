package dev.bpmcrafters.processengineapi.adapter.c7.common.task

import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

/**
 * The engine confirmed that the addressed external task no longer exists.
 */
class ExternalTaskNotFoundException(
  val taskId: String,
  cause: Throwable
) : RuntimeException("External task $taskId no longer exists.", cause) {

  companion object {
    /** Matches a missing-task outcome for this delivery, including future wrappers. */
    fun matches(failure: Throwable, taskId: String): Boolean {
      var cause: Throwable? = failure
      while (cause is CompletionException || cause is ExecutionException) {
        cause = cause.cause
      }
      return cause is ExternalTaskNotFoundException && cause.taskId == taskId
    }
  }
}
