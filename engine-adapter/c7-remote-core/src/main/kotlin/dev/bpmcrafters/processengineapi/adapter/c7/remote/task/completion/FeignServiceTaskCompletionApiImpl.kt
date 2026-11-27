package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.common.task.ExternalTaskCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.c7.common.threading.withThreadContextClassLoader
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.*
import feign.FeignException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.model.CompleteExternalTaskDto
import org.camunda.community.rest.client.model.ExternalTaskBpmnError
import org.camunda.community.rest.client.model.ExternalTaskFailureDto
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}
private val errorMapper by lazy { ObjectMapper() }

/**
 * Strategy for completing external tasks using Feign REST ExternalTaskApiClient.
 */
class FeignServiceTaskCompletionApiImpl(
  private val workerId: String,
  private val externalTaskApiClient: ExternalTaskApiClient,
  private val subscriptionRepository: SubscriptionRepository,
  private val failureRetrySupplier: FailureRetrySupplier,
  private val valueMapper: ValueMapper
) : ServiceTaskCompletionApi {

  private val externalTaskCommandExecutor = ExternalTaskCommandExecutor(subscriptionRepository, ::isMissingExternalTask)

  override fun completeTask(cmd: CompleteTaskCmd): CompletableFuture<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-006: completing service task ${cmd.taskId}." }
    return CompletableFuture.supplyAsync {
      val request = CompleteExternalTaskDto()
        .apply {
          this.variables = valueMapper.mapValues(cmd.get())
          this.workerId = this@FeignServiceTaskCompletionApiImpl.workerId
        }
      externalTaskCommandExecutor.execute(cmd.taskId) {
        externalTaskApiClient.completeExternalTaskResource(cmd.taskId, request)
      }
      subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
        withThreadContextClassLoader(termination) {
          termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
        }
        logger.debug { "PROCESS-ENGINE-C7-REMOTE-007: successfully completed service task ${cmd.taskId}." }
      }
      Empty
    }
  }

  override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): CompletableFuture<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-008: throwing error ${cmd.errorCode} in service task ${cmd.taskId}." }
    return CompletableFuture.supplyAsync {
      val request = ExternalTaskBpmnError().apply {
        this.workerId = this@FeignServiceTaskCompletionApiImpl.workerId
        this.errorCode = cmd.errorCode
        this.errorMessage = cmd.errorMessage
        this.variables = valueMapper.mapValues(cmd.get())
      }
      externalTaskCommandExecutor.execute(cmd.taskId) {
        externalTaskApiClient.handleExternalTaskBpmnError(cmd.taskId, request)
      }
      subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
        withThreadContextClassLoader(termination) {
          termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
        }
        logger.debug { "PROCESS-ENGINE-C7-REMOTE-009: successfully thrown error in service task ${cmd.taskId}." }
      }
      Empty
    }
  }

  override fun failTask(cmd: FailTaskCmd): CompletableFuture<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-010: failing service task ${cmd.taskId}." }
    return CompletableFuture.supplyAsync {
      val (retries, retryTimeoutInSeconds) = failureRetrySupplier.apply(cmd.taskId)
      val retryTimeoutInMillis = cmd.retryBackoff?.toMillis() ?: (retryTimeoutInSeconds * 1000)
      externalTaskCommandExecutor.execute(cmd.taskId) {
        externalTaskApiClient.handleFailure(
          cmd.taskId,
          ExternalTaskFailureDto().apply {
            this.workerId = this@FeignServiceTaskCompletionApiImpl.workerId
            this.retries = cmd.retryCount ?: retries
            this.retryTimeout = retryTimeoutInMillis
            this.errorDetails = cmd.errorDetails
            this.errorMessage = cmd.reason
          }
        )
      }
      subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
        withThreadContextClassLoader(termination) {
          termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
        }
        logger.debug { "PROCESS-ENGINE-C7-REMOTE-011: successfully failed service task ${cmd.taskId} handling." }
      }
      Empty
    }
  }

  private fun isMissingExternalTask(failure: Exception, taskId: String): Boolean {
    if (failure !is FeignException.NotFound) return false
    val response = try {
      errorMapper.readTree(failure.contentUTF8())
    } catch (_: JsonProcessingException) {
      return false
    } ?: return false
    return response.path("type").asText() == "RestException" &&
      response.path("message").asText() == "External task with id $taskId does not exist"
  }
}
