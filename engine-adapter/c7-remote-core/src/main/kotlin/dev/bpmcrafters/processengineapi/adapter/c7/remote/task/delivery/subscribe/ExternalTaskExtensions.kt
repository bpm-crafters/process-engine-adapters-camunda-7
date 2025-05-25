package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.subscribe

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.toDateString
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.camunda.bpm.client.task.ExternalTask

fun ExternalTask.toTaskInformation(): TaskInformation = TaskInformation(
  taskId = this.id,
  meta = mapOf(
    CommonRestrictions.ACTIVITY_ID to this.activityId,
    CommonRestrictions.PROCESS_DEFINITION_KEY to this.processDefinitionKey,
    CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
    CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
    CommonRestrictions.PROCESS_DEFINITION_VERSION_TAG to this.processDefinitionVersionTag,
    CommonRestrictions.TENANT_ID to this.tenantId,
    "topicName" to this.topicName,
    "creationDate" to this.createTime.toDateString()
  )
)
