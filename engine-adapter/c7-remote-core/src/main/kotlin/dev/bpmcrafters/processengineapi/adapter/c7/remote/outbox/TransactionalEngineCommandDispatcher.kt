package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.common.threading.withThreadContextClassLoader
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

/**
 * Enqueues inside a transaction, otherwise calls the engine directly.
 * A completed future therefore means handed to the outbox, not acknowledged by the engine.
 */
class TransactionalEngineCommandDispatcher(
  private val outbox: EngineCommandOutbox,
  private val subscriptionRepository: SubscriptionRepository,
  private val transaction: OutboxTransaction,
) {

  fun dispatch(description: String, command: () -> EngineCommand, direct: () -> CompletableFuture<Empty>): CompletableFuture<Empty> {
    if (!transaction.isActive()) {
      logger.warn { "PROCESS-ENGINE-C7-REMOTE-120: no active transaction for $description, calling the engine without outbox." }
      return direct()
    }
    val engineCommand = command()
    outbox.enqueue(engineCommand)
    if (engineCommand is EngineCommand.TaskCommand) {
      transaction.afterCommit { terminate(engineCommand.taskId) }
    }
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-121: enqueued $description." }
    return CompletableFuture.completedFuture(Empty)
  }

  private fun terminate(taskId: String) {
    try {
      subscriptionRepository.deactivateSubscriptionForTask(taskId)?.apply {
        withThreadContextClassLoader(termination) {
          termination.accept(TaskInformation(taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
        }
      }
    } catch (e: Exception) {
      logger.error(e) { "PROCESS-ENGINE-C7-REMOTE-129: termination of task $taskId failed after commit: ${e.message}" }
    }
  }
}
