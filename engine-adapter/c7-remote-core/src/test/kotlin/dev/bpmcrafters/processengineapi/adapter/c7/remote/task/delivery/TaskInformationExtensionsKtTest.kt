package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.model.IdentityLinkDto
import org.camunda.community.rest.client.model.LockedExternalTaskDto
import org.camunda.community.rest.client.model.TaskWithAttachmentAndCommentDto
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import java.time.OffsetDateTime


class TaskInformationExtensionsKtTest {

  private val processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver = CachingProcessDefinitionMetaDataResolver(
    mock(ProcessDefinitionApiClient::class.java),
    keys = mutableMapOf("processDefinitionId" to "processDefinitionKey"),
    versionTags = mutableMapOf("processDefinitionId" to "versionTag")
  )

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = ["2007-12-03T10:15:30+01:00"])
  fun `should map TaskWithAttachmentAndCommentDto`(maybeNullDate: OffsetDateTime?) {
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
      .followUp(maybeNullDate)
      .due(maybeNullDate)
      .formKey("formKey")
      .lastUpdated(maybeNullDate)

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
    assertThat(taskInformation.meta["followUpDate"]).isEqualTo(maybeNullDate.toDateString())
    assertThat(taskInformation.meta["dueDate"]).isEqualTo(maybeNullDate.toDateString())
    assertThat(taskInformation.meta["formKey"]).isEqualTo("formKey")
    assertThat(taskInformation.meta["candidateUsers"]).isEqualTo("user-1,user-2")
    assertThat(taskInformation.meta["candidateGroups"]).isEqualTo("group")
    assertThat(taskInformation.meta["lastUpdatedDate"]).isEqualTo(maybeNullDate.toDateString())
    if (maybeNullDate == null) {
      assertThat(taskInformation.meta).doesNotContainKey("followUpDate")
      assertThat(taskInformation.meta).doesNotContainKey("dueDate")
      assertThat(taskInformation.meta).doesNotContainKey("lastUpdatedDate")
    } else {
      assertThat(taskInformation.meta["followUpDate"]).isEqualTo(maybeNullDate.toDateString())
      assertThat(taskInformation.meta["dueDate"]).isEqualTo(maybeNullDate.toDateString())
      assertThat(taskInformation.meta["lastUpdatedDate"]).isEqualTo(maybeNullDate.toDateString())
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = [3])
  fun `should map TaskWithAttachmentAndCommentDto`(maybeNullRetryCount: Int?) {
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
      .retries(maybeNullRetryCount)

    val taskInformation = lockedTask.toTaskInformation(processDefinitionMetaDataResolver)

    assertThat(taskInformation.taskId).isEqualTo("taskId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_DEFINITION_ID]).isEqualTo("processDefinitionId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID]).isEqualTo("processInstanceId")
    assertThat(taskInformation.meta[CommonRestrictions.ACTIVITY_ID]).isEqualTo("activityId")
    assertThat(taskInformation.meta[CommonRestrictions.TENANT_ID]).isEqualTo("tenantId")
    assertThat(taskInformation.meta["topicName"]).isEqualTo("topicName")
    assertThat(taskInformation.meta["creationDate"]).isEqualTo(now.toDateString())
    if (maybeNullRetryCount == null) {
      assertThat(taskInformation.meta).doesNotContainKey(TaskInformation.RETRIES)
    } else {
      assertThat(taskInformation.meta[TaskInformation.RETRIES]).isEqualTo(maybeNullRetryCount.toString())
    }
  }

  private fun identityLink(userId: String? = null, groupId: String? = null): IdentityLinkDto {
    val identityLink = IdentityLinkDto()
    identityLink.userId = userId
    identityLink.groupId = groupId
    return identityLink
  }

}
