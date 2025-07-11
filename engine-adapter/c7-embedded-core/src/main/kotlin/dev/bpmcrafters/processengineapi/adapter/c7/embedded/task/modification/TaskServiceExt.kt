package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.modification

import org.camunda.bpm.engine.TaskService
import org.camunda.bpm.engine.task.IdentityLinkType

fun TaskService.getAllCandidates(taskId: String) = this
  .getIdentityLinksForTask(taskId)
  .filter { it.type == IdentityLinkType.CANDIDATE }

fun TaskService.getCandidateUsers(taskId: String) = getAllCandidates(taskId)
  .filter { it.userId != null }
  .map { it.userId }

fun TaskService.getCandidateGroups(taskId: String) = getAllCandidates(taskId)
  .filter { it.groupId != null }
  .map { it.groupId }

fun TaskService.removeAllCandidateUsers(taskId: String) = this
  .setCandidateUsers(taskId, listOf())

fun TaskService.setCandidateUsers(taskId: String, allCandidateUsers: List<String>) {
  val oldCandidates = this.getCandidateUsers(taskId)
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
  .setCandidateGroups(taskId, listOf())

fun TaskService.setCandidateGroups(taskId: String, allCandidateGroups: List<String>) {
  val oldCandidates = this.getCandidateGroups(taskId)
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
