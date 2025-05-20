package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery

import dev.bpmcrafters.processengineapi.CommonRestrictions
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.client.task.impl.ExternalTaskImpl
import org.camunda.bpm.engine.impl.persistence.entity.IdentityLinkEntity
import org.camunda.bpm.engine.task.IdentityLink
import org.camunda.community.mockito.task.LockedExternalTaskFake
import org.camunda.community.mockito.task.TaskFake
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*


class TaskInformationExtensionsKtTest {

  @Test
  fun `should map Task`() {
    val now = Date.from(Instant.now())
    val task = TaskFake.builder()
      .id("taskId")
      .processDefinitionId("processDefinitionId")
      .processInstanceId("processInstanceId")
      .tenantId("tenantId")
      .taskDefinitionKey("taskDefinitionKey")
      .name("name")
      .description("description")
      .assignee("assignee")
      .createTime(now)
      .followUpDate(now)
      .dueDate(now)
      .formKey("formKey")
      .lastUpdated(now)
      .build()

    val identityLinks =
      listOf(identityLink(groupId = "group"), identityLink(userId = "user-1"), identityLink(userId = "user-2"))

    val taskInformation = task.toTaskInformation(identityLinks.toSet(), "processDefinitionKey")

    assertThat(taskInformation.taskId).isEqualTo("taskId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_DEFINITION_ID]).isEqualTo("processDefinitionId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_DEFINITION_KEY]).isEqualTo("processDefinitionKey")
    assertThat(taskInformation.meta[CommonRestrictions.TENANT_ID]).isEqualTo("tenantId")
    assertThat(taskInformation.meta["taskName"]).isEqualTo("name")
    assertThat(taskInformation.meta["taskDescription"]).isEqualTo("description")
    assertThat(taskInformation.meta["assignee"]).isEqualTo("assignee")
    assertThat(taskInformation.meta["creationDate"]).isEqualTo(now.toDateString())
    assertThat(taskInformation.meta["followUpDate"]).isEqualTo(now.toDateString())
    assertThat(taskInformation.meta["dueDate"]).isEqualTo(now.toDateString())
    assertThat(taskInformation.meta["formKey"]).isEqualTo("formKey")
    assertThat(taskInformation.meta["candidateUsers"]).isEqualTo("user-1,user-2")
    assertThat(taskInformation.meta["candidateGroups"]).isEqualTo("group")
    assertThat(taskInformation.meta["lastUpdatedDate"]).isEqualTo(now.toDateString())
  }

  @Test
  fun `should map LockedExternalTask`() {
    val now = Date.from(Instant.now())

    var lockedTaskBuilder = LockedExternalTaskFake.builder()
    lockedTaskBuilder = lockedTaskBuilder.processDefinitionId("processDefinitionId")
    lockedTaskBuilder = lockedTaskBuilder.processInstanceId("processInstanceId")
    lockedTaskBuilder = lockedTaskBuilder.tenantId("tenantId")
    lockedTaskBuilder = lockedTaskBuilder.topicName("topicName")
    lockedTaskBuilder = lockedTaskBuilder.id("taskId")
    lockedTaskBuilder = lockedTaskBuilder.activityId("activityId")
    lockedTaskBuilder = lockedTaskBuilder.activityInstanceId("activityInstanceId")
    lockedTaskBuilder = lockedTaskBuilder.createTime(now)

    val lockedTask = lockedTaskBuilder.build()

    val taskInformation = lockedTask.toTaskInformation()

    assertThat(taskInformation.taskId).isEqualTo("taskId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_DEFINITION_ID]).isEqualTo("processDefinitionId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID]).isEqualTo("processInstanceId")
    assertThat(taskInformation.meta[CommonRestrictions.ACTIVITY_ID]).isEqualTo("activityId")
    assertThat(taskInformation.meta[CommonRestrictions.TENANT_ID]).isEqualTo("tenantId")
    assertThat(taskInformation.meta["topicName"]).isEqualTo("topicName")
    assertThat(taskInformation.meta["creationDate"]).isEqualTo(now.toDateString())

  }

  @Test
  fun `should map RemoteExternalTask`() {
    val now = Date.from(Instant.now())

    val externalTask = ExternalTaskImpl()
    externalTask.processDefinitionId = "processDefinitionId"
    externalTask.processInstanceId = "processInstanceId"
    externalTask.tenantId = "tenantId"
    externalTask.topicName = "topicName"
    externalTask.id = "taskId"
    externalTask.activityId = "activityId"
    externalTask.activityInstanceId = "activityInstanceId"
    externalTask.createTime = now

    val taskInformation = externalTask.toTaskInformation()

    assertThat(taskInformation.taskId).isEqualTo("taskId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_DEFINITION_ID]).isEqualTo("processDefinitionId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID]).isEqualTo("processInstanceId")
    assertThat(taskInformation.meta[CommonRestrictions.ACTIVITY_ID]).isEqualTo("activityId")
    assertThat(taskInformation.meta[CommonRestrictions.TENANT_ID]).isEqualTo("tenantId")
    assertThat(taskInformation.meta["topicName"]).isEqualTo("topicName")
    assertThat(taskInformation.meta["creationDate"]).isEqualTo(now.toDateString())

  }

  private fun identityLink(userId: String? = null, groupId: String? = null): IdentityLink {
    val identityLink = IdentityLinkEntity.newIdentityLink()
    identityLink.userId = userId
    identityLink.groupId = groupId
    return identityLink
  }


}
