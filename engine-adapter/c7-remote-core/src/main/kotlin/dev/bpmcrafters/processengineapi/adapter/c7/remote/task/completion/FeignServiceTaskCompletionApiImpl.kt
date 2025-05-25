package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.model.CompleteExternalTaskDto
import org.camunda.community.rest.client.model.ExternalTaskBpmnError
import org.camunda.community.rest.client.model.ExternalTaskFailureDto
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

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

  override fun completeTask(cmd: CompleteTaskCmd): Future<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-006: completing service task ${cmd.taskId}." }
    externalTaskApiClient.completeExternalTaskResource(
      cmd.taskId,
      CompleteExternalTaskDto()
        .apply {
          this.variables = valueMapper.mapValues(cmd.get())
          this.workerId = this@FeignServiceTaskCompletionApiImpl.workerId
        }
    )
    subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
      termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
      logger.debug { "PROCESS-ENGINE-C7-REMOTE-007: successfully completed service task ${cmd.taskId}." }
    }
    return CompletableFuture.completedFuture(Empty)
  }

  override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): Future<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-008: throwing error ${cmd.errorCode} in service task ${cmd.taskId}." }
    externalTaskApiClient.handleExternalTaskBpmnError(
      cmd.taskId,
      ExternalTaskBpmnError().apply {
        this.workerId = this@FeignServiceTaskCompletionApiImpl.workerId
        this.errorCode = cmd.errorCode
        this.errorMessage = cmd.errorMessage
        this.variables = valueMapper.mapValues(cmd.get())
      }
    )
    subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
      termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
      logger.debug { "PROCESS-ENGINE-C7-REMOTE-009: successfully thrown error in service task ${cmd.taskId}." }
    }
    return CompletableFuture.completedFuture(Empty)
  }

  override fun failTask(cmd: FailTaskCmd): Future<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-010: failing service task ${cmd.taskId}." }
    val (retries, retryTimeoutInSeconds) = failureRetrySupplier.apply(cmd.taskId)
    externalTaskApiClient.handleFailure(
      cmd.taskId,
      ExternalTaskFailureDto().apply {
        this.workerId = this@FeignServiceTaskCompletionApiImpl.workerId
        this.retries = retries
        this.retryTimeout = retryTimeoutInSeconds
        this.errorDetails = cmd.errorDetails
        this.errorMessage = cmd.reason
      }
    )
    subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
      termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
      logger.debug { "PROCESS-ENGINE-C7-REMOTE-011: successfully failed service task ${cmd.taskId} handling." }
    }
    return CompletableFuture.completedFuture(Empty)
  }
}
