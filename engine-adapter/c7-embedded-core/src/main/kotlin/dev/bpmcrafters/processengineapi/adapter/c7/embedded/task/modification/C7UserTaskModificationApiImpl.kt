package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.modification

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.task.*
import dev.bpmcrafters.processengineapi.task.ChangeAssignmentModifyTaskCmd.*
import dev.bpmcrafters.processengineapi.task.ChangePayloadModifyTaskCmd.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.TaskService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

/**
 * Implementation of the user task modification API.
 */
class C7UserTaskModificationApiImpl(
  private val taskService: TaskService,
) : UserTaskModificationApi {
  override fun update(cmd: ModifyTaskCmd): Future<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-051: modifying user task ${cmd.taskId}." }
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
      is AssignTaskCmd -> taskService.setAssignee(cmd.taskId, cmd.assignee)
      is UnassignTaskCmd -> taskService.setAssignee(cmd.taskId, null)
      is ClearCandidateUsersTaskCmd -> taskService.removeAllCandidateUsers(cmd.taskId)
      is ClearCandidateGroupsTaskCmd -> taskService.removeAllCandidateGroups(cmd.taskId)
      is SetCandidateUsersTaskCmd -> taskService.setCandidateUsers(cmd.taskId, cmd.candidateUsers)
      is SetCandidateGroupsTaskCmd -> taskService.setCandidateGroups(cmd.taskId, cmd.candidateGroups)
      is AddCandidateUserTaskCmd -> taskService.addCandidateUser(cmd.taskId, cmd.candidateUser)
      is AddCandidateGroupTaskCmd -> taskService.addCandidateGroup(cmd.taskId, cmd.candidateGroup)
      is RemoveCandidateUserTaskCmd -> taskService.deleteCandidateUser(cmd.taskId, cmd.candidateUser)
      is RemoveCandidateGroupTaskCmd -> taskService.deleteCandidateGroup(cmd.taskId, cmd.candidateGroup)
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName}.")
    }
  }

  private fun changePayload(cmd: ChangePayloadModifyTaskCmd) {
    when (cmd) {
      is UpdatePayloadTaskCmd -> taskService.setVariablesLocal(cmd.taskId, cmd.get())
      is DeletePayloadTaskCmd -> taskService.removeVariablesLocal(cmd.taskId, cmd.get())
      is ClearPayloadTaskCmd -> taskService.removeAllVariablesLocal(cmd.taskId)
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName}.")
    }
  }
}
