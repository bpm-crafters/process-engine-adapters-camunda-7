package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.EngineCommand.*
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.Correlation
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SendSignalCmd
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import dev.bpmcrafters.processengineapi.task.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

private val logger = KotlinLogging.logger {}

private const val UNAUTHORIZED = 401
private const val FORBIDDEN = 403
private const val NOT_FOUND = 404
private val UNAVAILABLE = setOf(502, 503, 504)

/**
 * Sends a stored command to the engine and tells how to proceed with it. Never throws for engine failures.
 */
class EngineCommandDelivery(
  private val serviceTaskCompletionApi: ServiceTaskCompletionApi?,
  private val userTaskCompletionApi: UserTaskCompletionApi?,
  private val correlationApi: CorrelationApi,
  private val signalApi: SignalApi,
  private val httpStatusOf: (Exception) -> Int?,
) {

  enum class Result {
    DELIVERED,

    /** Engine is not reachable, try again without limit. */
    UNAVAILABLE,

    /** Engine answered with an error, give up after a limited number of attempts. */
    REJECTED,
  }

  class Outcome(val result: Result, val failure: Exception? = null)

  fun deliver(command: EngineCommand): Outcome =
    try {
      send(command)
      Outcome(Result.DELIVERED)
    } catch (e: Exception) {
      val failure = unwrap(e)
      Outcome(classify(command, httpStatusOf(failure), failure), failure)
    }

  private fun send(command: EngineCommand) {
    when (command) {
      is CompleteServiceTask -> requireServiceTaskApi().completeTask(CompleteTaskCmd(command.taskId) { command.payload })
      is CompleteServiceTaskByError -> requireServiceTaskApi().completeTaskByError(
        CompleteTaskByErrorCmd(command.taskId, command.errorCode, command.errorMessage) { command.payload }
      )
      is FailServiceTask -> requireServiceTaskApi().failTask(
        FailTaskCmd(command.taskId, command.reason, command.errorDetails, command.retryCount, command.retryBackoff)
      )
      is CompleteUserTask -> requireUserTaskApi().completeTask(CompleteTaskCmd(command.taskId) { command.payload })
      is CompleteUserTaskByError -> requireUserTaskApi().completeTaskByError(
        CompleteTaskByErrorCmd(command.taskId, command.errorCode, command.errorMessage) { command.payload }
      )
      is CorrelateMessage -> correlationApi.correlateMessage(
        CorrelateMessageCmd(
          command.messageName,
          { command.payload },
          { Correlation.withKey(command.correlationKey).withVariable(command.correlationVariable) },
          command.restrictions,
        )
      )
      is SendSignal -> signalApi.sendSignal(SendSignalCmd(command.signalName, { command.payload }, command.restrictions))
    }.get()
  }

  private fun requireServiceTaskApi() = requireNotNull(serviceTaskCompletionApi) { "No service task completion API available." }

  private fun requireUserTaskApi() = requireNotNull(userTaskCompletionApi) { "No user task completion API available." }

  private fun classify(command: EngineCommand, status: Int?, failure: Exception): Result = when {
    status == NOT_FOUND && command is TaskCommand -> Result.DELIVERED
    status == UNAUTHORIZED || status == FORBIDDEN -> {
      logger.error { "PROCESS-ENGINE-C7-REMOTE-126: engine denied access: ${failure.message}" }
      Result.UNAVAILABLE
    }
    status == NOT_FOUND || status in UNAVAILABLE || isConnectionFailure(status, failure) -> Result.UNAVAILABLE
    else -> Result.REJECTED
  }

  private fun isConnectionFailure(status: Int?, failure: Exception) =
    status == null && generateSequence<Throwable>(failure) { it.cause }.any { it is IOException }

  private fun unwrap(failure: Exception): Exception {
    val cause = (failure as? ExecutionException ?: failure as? CompletionException)?.cause
    return if (cause is Exception) unwrap(cause) else failure
  }
}
