package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.camunda.bpm.client.task.ExternalTask
import org.camunda.community.rest.client.model.IdentityLinkDto
import org.camunda.community.rest.client.model.LockedExternalTaskDto
import org.camunda.community.rest.client.model.TaskWithAttachmentAndCommentDto
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

fun LockedExternalTaskDto.toTaskInformation(): TaskInformation =
  TaskInformation(
    taskId = this.id,
    meta = mapOf(
      CommonRestrictions.ACTIVITY_ID to this.activityId,
      CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
      CommonRestrictions.PROCESS_DEFINITION_KEY to this.processDefinitionKey,
      CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
      CommonRestrictions.TENANT_ID to this.tenantId,
      "topicName" to this.topicName,
      "creationDate" to this.createTime.toDateString()
    )
  )

fun TaskWithAttachmentAndCommentDto.toTaskInformation(candidates: Set<IdentityLinkDto>, processDefinitionKey: String? = null) =
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
      "creationDate" to this.created.toDateString(),
      "followUpDate" to this.followUp.toDateString(),
      "dueDate" to this.due.toDateString(),
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

/**
 * Converts engine internal representation into a string.
 */
fun Date?.toDateString() = this?.toInstant()?.toIso8601() ?: ""

/**
 * Converts offset date time to string representation in ISO8601 in UTC.
 */
fun OffsetDateTime?.toDateString() = this?.atZoneSameInstant(ZoneOffset.UTC).toString()

/**
 * Converts to offset date time in ISO8601 in UTC.
 */
fun Instant.toIso8601() = OffsetDateTime.ofInstant(this, ZoneOffset.UTC).toString()

/**
 * Extracts candidates groups as a comma-separated string.
 */
fun Set<IdentityLinkDto>.toGroupsString() = this.mapNotNull { it.groupId }.sorted().joinToString(",")

/**
 * Extracts candidates users as a comma-separated string.
 */
fun Set<IdentityLinkDto>.toUsersString() = this.mapNotNull { it.userId }.sorted().joinToString(",")


fun <T : Any> Map<String, T>.filterBySubscription(subscription: TaskSubscriptionHandle): Map<String, T> =
  if (subscription.payloadDescription != null) {
    if (subscription.payloadDescription!!.isEmpty()) {
      mapOf()
    } else {
      this.filterKeys { key -> subscription.payloadDescription!!.contains(key) }
    }
  } else {
    this
  }
