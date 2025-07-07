package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.modification

import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.client.model.IdentityLinkDto
import org.camunda.community.rest.client.model.PatchVariablesDto
import org.camunda.community.rest.client.model.VariableValueDto
import org.springframework.http.HttpStatusCode

private fun TaskApiClient.getLocalTaskVariables(taskId: String): Map<String, VariableValueDto> {
  val variableResponse = this.getTaskLocalVariables(taskId, false)
  return requireNotNull(variableResponse.body) { "Could not load local task variables for task $taskId, status was ${variableResponse.statusCode}" }
}

private fun TaskApiClient.getAllTaskCandidates(taskId: String): List<IdentityLinkDto> {
  val identityResponse = this.getIdentityLinks(taskId, "candidate")
  return requireNotNull(identityResponse.body) { "Could not load candidates for task $taskId, status code was ${identityResponse.statusCode}" }
}

private fun TaskApiClient.getCandidateGroups(taskId: String) =
  getAllTaskCandidates(taskId).filter { it.groupId != null }

private fun TaskApiClient.getCandidateUsers(taskId: String) =
  getAllTaskCandidates(taskId).filter { it.userId != null }

private fun TaskApiClient.synchronizeIdentityLinks(
  taskId: String,
  toRemove: List<IdentityLinkDto> = listOf(),
  toAdd: List<IdentityLinkDto> = listOf()
) {
  val removed = toRemove.associateWith { this.deleteIdentityLink(taskId, it).statusCode }
  require(removed.all { it.value == HttpStatusCode.valueOf(200) }) {
    "Failed to remove identity links for task $taskId, failed on ${removed.filter { it.value != HttpStatusCode.valueOf(200) }}"
  }

  val added = toAdd.associateWith { this.addIdentityLink(taskId, it).statusCode }
  require(added.all { it.value == HttpStatusCode.valueOf(200) }) {
    "Failed to add identity links for task $taskId, failed on ${added.filter { it.value != HttpStatusCode.valueOf(200) }}"
  }
}

fun TaskApiClient.removeAllCandidateUsers(taskId: String) {
  setCandidateUsers(taskId, listOf())
}

fun TaskApiClient.removeAllCandidateGroups(taskId: String) {
  setCandidateGroups(taskId, listOf())
}

fun TaskApiClient.setCandidateUsers(taskId: String, candidateUsers: List<IdentityLinkDto>) {
  val all = getCandidateUsers(taskId)
  val toRemove = all.filter { it !in candidateUsers }
  val toAdd = candidateUsers.filter { it !in all }
  synchronizeIdentityLinks(taskId = taskId, toRemove = toRemove, toAdd = toAdd)
}

fun TaskApiClient.setCandidateGroups(taskId: String, candidateGroups: List<IdentityLinkDto>) {
  val all = getCandidateGroups(taskId)
  val toRemove = all.filter { it !in candidateGroups }
  val toAdd = candidateGroups.filter { it !in all }
  synchronizeIdentityLinks(taskId = taskId, toRemove = toRemove, toAdd = toAdd)
}

fun TaskApiClient.clearTaskVariablesLocal(taskId: String) {
  val all = getLocalTaskVariables(taskId)
  this.modifyTaskLocalVariables(
    taskId,
    PatchVariablesDto().deletions(all.keys.toList())
  )
}

fun TaskApiClient.removeVariablesLocal(taskId: String, variables: List<String>) {
  this.modifyTaskLocalVariables(
    taskId,
    PatchVariablesDto().deletions(variables)
  )
}
