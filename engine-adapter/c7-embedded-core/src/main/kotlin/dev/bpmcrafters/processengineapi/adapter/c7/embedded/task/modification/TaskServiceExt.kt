package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.modification

import org.camunda.bpm.engine.TaskService
import org.camunda.bpm.engine.task.IdentityLinkType

fun TaskService.removeAllCandidateUsers(taskId: String) = this
  .getIdentityLinksForTask(taskId)
  .filter { it.userId != null && it.type == IdentityLinkType.CANDIDATE }
  .forEach { this.deleteCandidateUser(taskId, it.userId) }

fun TaskService.setCandidateUsers(taskId: String, allCandidateUsers: List<String>) {
  val oldCandidates = this
    .getIdentityLinksForTask(taskId)
    .filter { it.userId != null && it.type == IdentityLinkType.CANDIDATE }
    .map { it.userId }
  val candidatesToRemove = oldCandidates.filter { it !in allCandidateUsers }
  val candidatesToAdd = allCandidateUsers.filter { it !in oldCandidates }

  candidatesToRemove.forEach {
    this.deleteCandidateUser(taskId, it)
  }
  candidatesToAdd.forEach {
    this.addCandidateUser(taskId, it)
  }
}

fun TaskService.removeAllCandidateGroups(taskId: String) = this
  .getIdentityLinksForTask(taskId)
  .filter { it.groupId != null && it.type == IdentityLinkType.CANDIDATE }
  .forEach { this.deleteCandidateGroup(taskId, it.groupId) }

fun TaskService.setCandidateGroups(taskId: String, allCandidateGroups: List<String>) {
  val oldCandidates = this
    .getIdentityLinksForTask(taskId)
    .filter { it.groupId != null && it.type == IdentityLinkType.CANDIDATE }
    .map { it.groupId }
  val candidatesToRemove = oldCandidates.filter { it !in allCandidateGroups }
  val candidatesToAdd = allCandidateGroups.filter { it !in oldCandidates }

  candidatesToRemove.forEach {
    this.deleteCandidateGroup(taskId, it)
  }
  candidatesToAdd.forEach {
    this.addCandidateGroup(taskId, it)
  }
}

fun TaskService.removeAllVariablesLocal(taskId: String) {
  val variableKeys = this.getVariablesLocal(taskId).map { it.key }
  this.removeVariablesLocal(taskId, variableKeys)
}
