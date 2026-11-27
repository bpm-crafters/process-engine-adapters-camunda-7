package dev.bpmcrafters.processengineapi.adapter.c7.common.task

import java.util.Collections
import java.util.IdentityHashMap

/**
 * The engine confirmed that the addressed external task no longer exists.
 */
class ExternalTaskNotFoundException(
  val taskId: String,
  cause: Throwable
) : RuntimeException("External task $taskId no longer exists.", cause) {

  companion object {
    /**
     * Matches a missing-task outcome for this delivery anywhere in the cause chain.
     **/
    fun matches(failure: Throwable, taskId: String): Boolean {
      val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
      var cause: Throwable? = failure
      while (cause != null && visited.add(cause)) {
        if (cause is ExternalTaskNotFoundException && cause.taskId == taskId) return true
        cause = cause.cause
      }
      return false
    }
  }
}
