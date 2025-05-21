package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.camunda.bpm.engine.delegate.DelegateTask
import org.camunda.bpm.engine.externaltask.LockedExternalTask
import org.camunda.bpm.engine.impl.persistence.entity.ExternalTaskEntity
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity
import org.camunda.bpm.engine.task.IdentityLink
import org.camunda.bpm.engine.task.Task
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

fun Task.toTaskInformation(candidates: Set<IdentityLink>, processDefinitionKey: String? = null) =
  TaskInformation(
    taskId = this.id,
    meta = mapOf(
      CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
      CommonRestrictions.ACTIVITY_ID to this.taskDefinitionKey,
      CommonRestrictions.TENANT_ID to this.tenantId,
      CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
      "taskName" to this.name,
      "taskDescription" to this.description,
      "assignee" to this.assignee,
      "creationDate" to this.createTime.toDateString(),
      "followUpDate" to this.followUpDate.toDateString(),
      "dueDate" to this.dueDate.toDateString(),
      "formKey" to this.formKey,
      "candidateUsers" to candidates.toUsersString(),
      "candidateGroups" to candidates.toGroupsString(),
      "lastUpdatedDate" to this.lastUpdated.toDateString()
    ).let {
      if (processDefinitionKey != null) {
        it + (CommonRestrictions.PROCESS_DEFINITION_KEY to processDefinitionKey)
      } else {
        it
      }
    }
  )

fun TaskEntity.toTaskInformation() =
  TaskInformation(
    taskId = this.id,
    meta = mapOf(
      CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
      CommonRestrictions.ACTIVITY_ID to this.taskDefinitionKey,
      CommonRestrictions.TENANT_ID to this.tenantId,
      CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
      "taskName" to this.name,
      "taskDescription" to this.description,
      "assignee" to this.assignee,
      "creationDate" to this.createTime.toDateString(),
      "followUpDate" to this.followUpDate.toDateString(),
      "dueDate" to this.dueDate.toDateString(),
      "formKey" to this.formKey,
      "candidateUsers" to this.candidates.toUsersString(),
      "candidateGroups" to this.candidates.toGroupsString(),
      "lastUpdatedDate" to this.lastUpdated.toDateString()
    )
  )

fun DelegateTask.toTaskInformation() =
  TaskInformation(
    taskId = this.id,
    meta = mapOf(
      CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
      CommonRestrictions.ACTIVITY_ID to this.taskDefinitionKey,
      CommonRestrictions.TENANT_ID to this.tenantId,
      CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
      "taskName" to this.name,
      "taskDescription" to this.description,
      "assignee" to this.assignee,
      "creationDate" to this.createTime.toDateString(),
      "followUpDate" to this.followUpDate.toDateString(),
      "dueDate" to this.dueDate.toDateString(),
      "candidateUsers" to this.candidates.toUsersString(),
      "candidateGroups" to this.candidates.toGroupsString(),
      "lastUpdatedDate" to this.lastUpdated.toDateString()
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
      "creationDate" to this.createTime.toDateString(),
    )
  )


fun ExternalTaskEntity.toTaskInformation(): TaskInformation {
  return TaskInformation(
    taskId = this.id,
    meta = mapOf(
      CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
      CommonRestrictions.PROCESS_DEFINITION_KEY to this.processDefinitionKey,
      CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
      CommonRestrictions.TENANT_ID to this.tenantId,
      CommonRestrictions.ACTIVITY_ID to this.activityId,
      "topicName" to this.topicName,
      "creationDate" to this.createTime.toDateString(),
    )
  )
}

/**
 * Converts engine internal representation into a string.
 */
fun Date?.toDateString() = this?.toInstant()?.toIso8601() ?: ""

/**
 * Converts to offset date time in ISO8601 in UTC.
 */
fun Instant.toIso8601() = OffsetDateTime.ofInstant(this, ZoneOffset.UTC).toString()

/**
 * Extracts candidates groups as a comma-separated string.
 */
fun Set<IdentityLink>.toGroupsString() = this.mapNotNull { it.groupId }.sorted().joinToString(",")
/**
 * Extracts candidates users as a comma-separated string.
 */
fun Set<IdentityLink>.toUsersString() = this.mapNotNull { it.userId }.sorted().joinToString(",")
