package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.common.task.ExternalTaskCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.c7.common.threading.withThreadContextClassLoader
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.client.exception.NotFoundException
import org.camunda.bpm.client.exception.RestException
import java.util.concurrent.CompletableFuture
import org.camunda.bpm.client.task.ExternalTaskService as ClientExternalTaskService

private val logger = KotlinLogging.logger {}

/**
 * External task completion API implementation using official client-based external task service.
 * @param externalTaskService external task service provided by the official Camunda Platform 7 client
 * @param subscriptionRepository repository for subscriptions.
 */
class OfficialClientServiceTaskCompletionApiImpl(
  private val externalTaskService: ClientExternalTaskService,
  private val subscriptionRepository: SubscriptionRepository,
  private val failureRetrySupplier: FailureRetrySupplier
) : ServiceTaskCompletionApi {

  private val externalTaskCommandExecutor = ExternalTaskCommandExecutor(
    subscriptionRepository,
    failureRetrySupplier::clear,
    ::isMissingExternalTask
  )

  override fun completeTask(cmd: CompleteTaskCmd): CompletableFuture<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-006: completing service task ${cmd.taskId}." }
    return CompletableFuture.supplyAsync {
      val variables = cmd.get()
      externalTaskCommandExecutor.execute(cmd.taskId) {
        externalTaskService
          .complete(
            cmd.taskId,
            variables,
            mapOf()
          )
      }
      failureRetrySupplier.clear(cmd.taskId)
      subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
        withThreadContextClassLoader(termination) {
          termination.accept(
            TaskInformation(cmd.taskId, mapOf()).withReason(TaskInformation.COMPLETE)
          )
        }
        logger.debug { "PROCESS-ENGINE-C7-REMOTE-007: successfully completed service task ${cmd.taskId}." }
      }
      Empty
    }
  }

  override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): CompletableFuture<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-008: throwing error ${cmd.errorCode} in service task ${cmd.taskId}." }
    return CompletableFuture.supplyAsync {
      val variables = cmd.get()
      externalTaskCommandExecutor.execute(cmd.taskId) {
        externalTaskService
          .handleBpmnError(
            cmd.taskId,
            cmd.errorCode,
            cmd.errorMessage,
            variables
          )
      }
      failureRetrySupplier.clear(cmd.taskId)
      subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
        withThreadContextClassLoader(termination) {
          termination.accept(
            TaskInformation(cmd.taskId, mapOf()).withReason(TaskInformation.COMPLETE)
          )
        }
        logger.debug { "PROCESS-ENGINE-C7-REMOTE-009: successfully thrown error in service task ${cmd.taskId}." }
      }
      Empty
    }
  }

  override fun failTask(cmd: FailTaskCmd): CompletableFuture<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-010: failing service task ${cmd.taskId}." }
    return CompletableFuture.supplyAsync {
      val retry = if (cmd.retryCount == null) failureRetrySupplier.apply(cmd.taskId) else null
      val retries = cmd.retryCount ?: checkNotNull(retry).retryCount
      val retryTimeoutInMillis = cmd.retryBackoff?.toMillis()
        ?: (retry?.retryTimeout ?: failureRetrySupplier.retryTimeout(cmd.taskId)) * 1000
      externalTaskCommandExecutor.execute(cmd.taskId) {
        externalTaskService
          .handleFailure(
            cmd.taskId,
            cmd.reason,
            cmd.errorDetails,
            retries,
            retryTimeoutInMillis
          )
      }
      if (retries == 0) failureRetrySupplier.clear(cmd.taskId)
      subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
        withThreadContextClassLoader(termination) {
          termination.accept(
            TaskInformation(cmd.taskId, mapOf()).withReason(TaskInformation.COMPLETE)
          )
        }
        logger.debug { "PROCESS-ENGINE-C7-REMOTE-011: successfully failed service task ${cmd.taskId} handling." }
      }
      Empty
    }
  }

  private fun isMissingExternalTask(failure: Exception, taskId: String): Boolean {
    if (failure !is NotFoundException) return false
    val response = failure.cause as? RestException ?: return false
    return response.type == "RestException" && response.message == "External task with id $taskId does not exist"
  }
}
