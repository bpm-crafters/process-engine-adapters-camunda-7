package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.ProcessDefinitionMetaDataResolver
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.model.IdentityLinkDto
import org.camunda.community.rest.client.model.LockedExternalTaskDto
import org.camunda.community.rest.client.model.TaskWithAttachmentAndCommentDto
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.OffsetDateTime


class TaskInformationExtensionsKtTest {

  private val processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver = CachingProcessDefinitionMetaDataResolver(
    mock(ProcessDefinitionApiClient::class.java),
    keys = mutableMapOf("processDefinitionId" to "processDefinitionKey"),
    versionTags = mutableMapOf("processDefinitionId" to "versionTag")
  )

  @Test
  fun `should map TaskWithAttachmentAndCommentDto`() {
    val now = OffsetDateTime.now()
    val task = TaskWithAttachmentAndCommentDto()
      .id("taskId")
      .processDefinitionId("processDefinitionId")
      .processInstanceId("processInstanceId")
      .tenantId("tenantId")
      .taskDefinitionKey("taskDefinitionKey")
      .name("name")
      .description("description")
      .assignee("assignee")
      .created(now)
      .followUp(now)
      .due(now)
      .formKey("formKey")
      .lastUpdated(now)

    val identityLinks =
      listOf(identityLink(groupId = "group"), identityLink(userId = "user-1"), identityLink(userId = "user-2"))

    val taskInformation = task.toTaskInformation(identityLinks.toSet(), processDefinitionMetaDataResolver)

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
    val now = OffsetDateTime.now()

    val lockedTask = LockedExternalTaskDto()
      .processDefinitionId("processDefinitionId")
      .processInstanceId("processInstanceId")
      .tenantId("tenantId")
      .topicName("topicName")
      .id("taskId")
      .activityId("activityId")
      .activityInstanceId("activityInstanceId")
      .createTime(now)

    val taskInformation = lockedTask.toTaskInformation(processDefinitionMetaDataResolver)

    assertThat(taskInformation.taskId).isEqualTo("taskId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_DEFINITION_ID]).isEqualTo("processDefinitionId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID]).isEqualTo("processInstanceId")
    assertThat(taskInformation.meta[CommonRestrictions.ACTIVITY_ID]).isEqualTo("activityId")
    assertThat(taskInformation.meta[CommonRestrictions.TENANT_ID]).isEqualTo("tenantId")
    assertThat(taskInformation.meta["topicName"]).isEqualTo("topicName")
    assertThat(taskInformation.meta["creationDate"]).isEqualTo(now.toDateString())

  }

  private fun identityLink(userId: String? = null, groupId: String? = null): IdentityLinkDto {
    val identityLink = IdentityLinkDto()
    identityLink.userId = userId
    identityLink.groupId = groupId
    return identityLink
  }


}
