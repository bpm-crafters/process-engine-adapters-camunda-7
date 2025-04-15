package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.camunda.bpm.engine.externaltask.LockedExternalTask
import org.camunda.bpm.engine.task.IdentityLink
import org.camunda.bpm.engine.task.Task
import java.util.*
import org.camunda.bpm.client.task.ExternalTask as RemoteExternalTask


fun RemoteExternalTask.toTaskInformation(): TaskInformation = TaskInformation(
  taskId = this.id,
  meta = mapOf(
    CommonRestrictions.ACTIVITY_ID to this.activityId,
    CommonRestrictions.PROCESS_DEFINITION_KEY to this.processDefinitionKey,
    CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
    CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
    CommonRestrictions.PROCESS_DEFINITION_VERSION_TAG to this.processDefinitionVersionTag,
    CommonRestrictions.TENANT_ID to this.tenantId,
    "topicName" to this.topicName,
    "creationTime" to this.createTime.toDateString()
    // FIXME more
  )
)

fun LockedExternalTask.toTaskInformation(): TaskInformation =
  TaskInformation(
    taskId = this.id,
    meta = mapOf(
      CommonRestrictions.ACTIVITY_ID to this.activityId,
      CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
      CommonRestrictions.PROCESS_DEFINITION_KEY to this.processDefinitionKey,
      CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
      CommonRestrictions.TENANT_ID to this.tenantId,
      "topicName" to this.topicName,
      "creationTime" to "" // creation time is not supported via REST
    )
  )



fun Task.toTaskInformation(candidates: Set<IdentityLink>, processDefinitionKey: String? = null) =
  TaskInformation(
    taskId = this.id,
    meta = mapOf(
      CommonRestrictions.ACTIVITY_ID to this.taskDefinitionKey,
      CommonRestrictions.TENANT_ID to this.tenantId,
      CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
      CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
      "taskName" to this.name,
      "taskDescription" to this.description,
      "assignee" to this.assignee,
      "creationDate" to this.createTime.toDateString(), // FIXME -> to zoned iso 8601
      "followUpDate" to this.followUpDate.toDateString(), // FIXME -> to zoned iso 8601
      "dueDate" to this.dueDate.toDateString(), // FIXME -> to zoned iso 8601
      "formKey" to this.formKey,
      "candidateUsers" to candidates.toUsersString(),
      "candidateGroups" to candidates.toGroupsString(),
      "lastUpdatedDate" to this.lastUpdated.toDateString() // FIXME -> to zoned iso 8601
    ).let {
      if (processDefinitionKey != null) {
        it + (CommonRestrictions.PROCESS_DEFINITION_KEY to processDefinitionKey)
      } else {
        it
      }
    }
  )

/**
 * Converts engine internal representation into a string.
 */
fun Date?.toDateString() = this?.toString() ?: ""
/**
 * Extracts candidates groups as a comma-separated string.
 */
fun Set<IdentityLink>.toGroupsString() = this.mapNotNull { it.groupId }.sorted().joinToString(",")
/**
 * Extracts candidates users as a comma-separated string.
 */
fun Set<IdentityLink>.toUsersString() = this.mapNotNull { it.userId }.sorted().joinToString(",")
