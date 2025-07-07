package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.modification

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.task.*
import dev.bpmcrafters.processengineapi.task.ChangeAssignmentModifyTaskCmd.*
import dev.bpmcrafters.processengineapi.task.ChangePayloadModifyTaskCmd.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.client.model.IdentityLinkDto
import org.camunda.community.rest.client.model.PatchVariablesDto
import org.camunda.community.rest.client.model.UserIdDto
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

class UserTaskModificationApiImpl(
  private val taskApiClient: TaskApiClient,
  private val valueMapper: ValueMapper,
) : UserTaskModificationApi {
  override fun update(cmd: ModifyTaskCmd): Future<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-051: modifying user task ${cmd.taskId}." }
    if (cmd is CompositeModifyTaskCmd) {
      cmd.commands.forEach {
        handleCommand(it)
      }
    } else {
      handleCommand(cmd)
    }
    return CompletableFuture.completedFuture(Empty)
  }

  private fun handleCommand(cmd: ModifyTaskCmd) {
    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-052: handling command ${cmd}." }
    when (cmd) {
      is ChangeAssignmentModifyTaskCmd -> changeAssignment(cmd)
      is ChangePayloadModifyTaskCmd -> changePayload(cmd)
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName}.")
    }
  }

  private fun changeAssignment(cmd: ChangeAssignmentModifyTaskCmd) {
    when (cmd) {
      is AssignTaskCmd -> taskApiClient.setAssignee(cmd.taskId, UserIdDto().userId(cmd.assignee))
      is UnassignTaskCmd -> taskApiClient.setAssignee(cmd.taskId, UserIdDto())
      is ClearCandidateUsersTaskCmd -> taskApiClient.removeAllCandidateUsers(cmd.taskId)
      is ClearCandidateGroupsTaskCmd -> taskApiClient.removeAllCandidateGroups(cmd.taskId)
      is SetCandidateUsersTaskCmd -> taskApiClient.setCandidateUsers(cmd.taskId, cmd.toIdentityLinkDtoList())
      is SetCandidateGroupsTaskCmd -> taskApiClient.setCandidateGroups(cmd.taskId, cmd.toIdentityLinkDtoList())
      is AddCandidateUserTaskCmd, is AddCandidateGroupTaskCmd -> taskApiClient.addIdentityLink(cmd.taskId, cmd.toIdentityLinkDto())
      is RemoveCandidateUserTaskCmd, is RemoveCandidateGroupTaskCmd -> taskApiClient.deleteIdentityLink(cmd.taskId, cmd.toIdentityLinkDto())
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName}.")
    }
  }

  private fun changePayload(cmd: ChangePayloadModifyTaskCmd) {
    when (cmd) {
      is UpdatePayloadTaskCmd -> taskApiClient.modifyTaskVariables(
        cmd.taskId,
        PatchVariablesDto().modifications(valueMapper.mapValues(cmd.get()))
      )
      is DeletePayloadTaskCmd -> taskApiClient.removeVariablesLocal(cmd.taskId, cmd.get())
      is ClearPayloadTaskCmd -> taskApiClient.clearTaskVariablesLocal(cmd.taskId)
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName}.")
    }
  }

  private fun ChangeAssignmentModifyTaskCmd.toIdentityLinkDto(): IdentityLinkDto {
    return when (this) {
      is AddCandidateUserTaskCmd -> IdentityLinkDto().apply {
        type = "candidate"
        userId = this@toIdentityLinkDto.candidateUser
      }

      is RemoveCandidateUserTaskCmd -> IdentityLinkDto().apply {
        type = "candidate"
        userId = this@toIdentityLinkDto.candidateUser
      }

      is AddCandidateGroupTaskCmd -> IdentityLinkDto().apply {
        type = "candidate"
        groupId = this@toIdentityLinkDto.candidateGroup
      }

      is RemoveCandidateGroupTaskCmd -> IdentityLinkDto().apply {
        type = "candidate"
        groupId = this@toIdentityLinkDto.candidateGroup
      }

      else -> throw UnsupportedOperationException("Unsupported command ${javaClass.canonicalName}.")
    }
  }

  private fun ChangeAssignmentModifyTaskCmd.toIdentityLinkDtoList(): List<IdentityLinkDto> {
    return when (this) {
      is SetCandidateUsersTaskCmd ->
        this.candidateUsers.map { candidateUser ->
          IdentityLinkDto().apply {
            type = "candidate"
            userId = candidateUser
          }
        }

      is SetCandidateGroupsTaskCmd -> this.candidateGroups.map { candidateGroup ->
        IdentityLinkDto().apply {
          type = "candidate"
          groupId = candidateGroup
        }
      }

      else -> throw UnsupportedOperationException("Unsupported command ${javaClass.canonicalName}.")
    }
  }

}
