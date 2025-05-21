package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.client.model.CompleteTaskDto
import org.camunda.community.rest.client.model.TaskBpmnErrorDto
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

/**
 * Strategy for completing user tasks using Camunda taskService Java API.
 */
class UserTaskCompletionApiImpl(
  private val taskApiClient: TaskApiClient,
  private val subscriptionRepository: SubscriptionRepository,
  private val valueMapper: ValueMapper
) : UserTaskCompletionApi {

  override fun completeTask(cmd: CompleteTaskCmd): Future<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-011: completing user task ${cmd.taskId}." }
    taskApiClient.complete(
      cmd.taskId,
      CompleteTaskDto().apply {
        this.variables = valueMapper.mapValues(cmd.get())
      }
    )
    subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
      termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
      logger.debug { "PROCESS-ENGINE-C7-REMOTE-012: successfully completed user task ${cmd.taskId}." }
    }
    return CompletableFuture.completedFuture(Empty)
  }

  override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): Future<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-013: throwing error on user task ${cmd.taskId}." }
    taskApiClient.handleBpmnError(
      cmd.taskId,
      TaskBpmnErrorDto().apply {
        this.errorCode = cmd.errorCode
        this.errorMessage = cmd.errorMessage
        this.variables = valueMapper.mapValues(cmd.get())
      }
    )
    subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
      termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
      logger.debug { "PROCESS-ENGINE-C7-REMOTE-014: successfully thrown error on user task ${cmd.taskId}." }
    }
    return CompletableFuture.completedFuture(Empty)
  }
}
