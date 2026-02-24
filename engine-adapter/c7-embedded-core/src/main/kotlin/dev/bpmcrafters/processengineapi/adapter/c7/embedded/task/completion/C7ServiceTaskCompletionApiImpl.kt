package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.ExternalTaskService
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

/**
 * Strategy for completing external tasks using Camunda externalTaskService Java API.
 */
class C7ServiceTaskCompletionApiImpl(
  private val workerId: String,
  private val externalTaskService: ExternalTaskService,
  private val subscriptionRepository: SubscriptionRepository,
  private val failureRetrySupplier: FailureRetrySupplier,
  private val commandExecutor: EngineCommandExecutor
) : ServiceTaskCompletionApi {

  override fun completeTask(cmd: CompleteTaskCmd): CompletableFuture<Empty> {

    logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-006: completing service task ${cmd.taskId}." }
    return commandExecutor.execute {
      externalTaskService.complete(
        cmd.taskId,
        workerId,
        cmd.get()
      )
      subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
        termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
        logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-007: successfully completed service task ${cmd.taskId}." }
      }
      Empty
    }
  }

  override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): CompletableFuture<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-008: throwing error ${cmd.errorCode} in service task ${cmd.taskId}." }
    return commandExecutor.execute {
      externalTaskService.handleBpmnError(
        cmd.taskId,
        workerId,
        cmd.errorCode,
        cmd.errorMessage,
        cmd.get()
      )
      subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
        termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
        logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-009: successfully thrown error in service task ${cmd.taskId}." }
      }
      Empty
    }
  }

  override fun failTask(cmd: FailTaskCmd): CompletableFuture<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-010: failing service task ${cmd.taskId}." }
    return commandExecutor.execute {
      val (retries, retryTimeoutInSeconds) = failureRetrySupplier.apply(cmd.taskId)
      externalTaskService.handleFailure(
        cmd.taskId,
        workerId,
        cmd.reason,
        cmd.errorDetails,
        cmd.retryCount ?: retries,
        cmd.retryBackoff?.get(ChronoUnit.SECONDS) ?: retryTimeoutInSeconds
      )
      subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
        termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
        logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-011: successfully failed service task ${cmd.taskId} handling." }
      }
      Empty
    }
  }
}
