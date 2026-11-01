package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import java.io.Serializable
import java.time.Duration

/**
 * Engine call stored for delivery after the business transaction committed.
 */
sealed interface EngineCommand : Serializable {

  sealed interface TaskCommand : EngineCommand {
    val taskId: String
  }

  data class CompleteServiceTask(
    override val taskId: String,
    val payload: Map<String, Any?>,
  ) : TaskCommand {
    private companion object {
      private const val serialVersionUID = 1L
    }
  }

  data class CompleteServiceTaskByError(
    override val taskId: String,
    val errorCode: String,
    val errorMessage: String?,
    val payload: Map<String, Any?>,
  ) : TaskCommand {
    private companion object {
      private const val serialVersionUID = 1L
    }
  }

  /**
   * Holds the retry values resolved at enqueue, as repeated delivery must not count down the retries again.
   */
  data class FailServiceTask(
    override val taskId: String,
    val reason: String,
    val errorDetails: String?,
    val retryCount: Int,
    val retryBackoff: Duration,
  ) : TaskCommand {
    private companion object {
      private const val serialVersionUID = 1L
    }
  }

  data class CompleteUserTask(
    override val taskId: String,
    val payload: Map<String, Any?>,
  ) : TaskCommand {
    private companion object {
      private const val serialVersionUID = 1L
    }
  }

  data class CompleteUserTaskByError(
    override val taskId: String,
    val errorCode: String,
    val errorMessage: String?,
    val payload: Map<String, Any?>,
  ) : TaskCommand {
    private companion object {
      private const val serialVersionUID = 1L
    }
  }

  data class CorrelateMessage(
    val messageName: String,
    val correlationKey: String,
    val correlationVariable: String,
    val restrictions: Map<String, String>,
    val payload: Map<String, Any?>,
  ) : EngineCommand {
    private companion object {
      private const val serialVersionUID = 1L
    }
  }

  data class SendSignal(
    val signalName: String,
    val restrictions: Map<String, String>,
    val payload: Map<String, Any?>,
  ) : EngineCommand {
    private companion object {
      private const val serialVersionUID = 1L
    }
  }
}
