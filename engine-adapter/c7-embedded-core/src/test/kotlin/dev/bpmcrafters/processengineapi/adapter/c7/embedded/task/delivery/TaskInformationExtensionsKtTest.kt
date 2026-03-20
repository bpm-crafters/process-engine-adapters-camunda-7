package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery

import dev.bpmcrafters.processengineapi.CommonRestrictions
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.impl.persistence.entity.IdentityLinkEntity
import org.camunda.bpm.engine.task.IdentityLink
import org.camunda.community.mockito.delegate.DelegateTaskFake
import org.camunda.community.mockito.task.TaskFake
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.*

class TaskInformationExtensionsKtTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = ["Sat, 12 Aug 1995 13:30:00 GMT+0430"])
  fun `should map Task`(maybeNullDate: Date?) {
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
      .followUpDate(maybeNullDate)
      .dueDate(maybeNullDate)
      .formKey("formKey")
      .lastUpdated(maybeNullDate)
      .build()

    val identityLinks =
      listOf(identityLink(groupId = "group"), identityLink(userId = "user-1"), identityLink(userId = "user-2"))

    val taskInformation = task.toTaskInformation(identityLinks.toSet(), "processDefinitionKey")

    assertThat(taskInformation.taskId).isEqualTo("taskId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_DEFINITION_ID]).isEqualTo("processDefinitionId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_DEFINITION_KEY]).isEqualTo("processDefinitionKey")
    assertThat(taskInformation.meta[CommonRestrictions.ACTIVITY_ID]).isEqualTo("taskDefinitionKey")
    assertThat(taskInformation.meta[CommonRestrictions.TENANT_ID]).isEqualTo("tenantId")
    assertThat(taskInformation.meta["taskName"]).isEqualTo("name")
    assertThat(taskInformation.meta["taskDescription"]).isEqualTo("description")
    assertThat(taskInformation.meta["assignee"]).isEqualTo("assignee")
    assertThat(taskInformation.meta["creationDate"]).isEqualTo(now.toDateString())
    assertThat(taskInformation.meta["formKey"]).isEqualTo("formKey")
    assertThat(taskInformation.meta["candidateUsers"]).isEqualTo("user-1,user-2")
    assertThat(taskInformation.meta["candidateGroups"]).isEqualTo("group")
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
  @ValueSource(strings = ["Sat, 12 Aug 1995 13:30:00 GMT+0430"])
  fun `should map DelegateTask`(maybeNullDate: Date?) {
    val now = Date.from(Instant.now())

    var delegateTask = DelegateTaskFake("taskId")
    delegateTask = delegateTask.withProcessDefinitionId("processDefinitionId")
    delegateTask = delegateTask.withProcessInstanceId("processInstanceId")
    delegateTask = delegateTask.withTenantId("tenantId")
    delegateTask = delegateTask.withTaskDefinitionKey("taskDefinitionKey")
    delegateTask = delegateTask.withName("name")
    delegateTask = delegateTask.withDescription("description")
    delegateTask = delegateTask.withAssignee("assignee")
    delegateTask = delegateTask.withCreateTime(now)
    delegateTask = delegateTask.withFollowUpDate(maybeNullDate)
    delegateTask = delegateTask.withLastUpdated(maybeNullDate)
    delegateTask.dueDate = maybeNullDate
    delegateTask.addGroupIdentityLink("group-1", "CANDIDATE")
    delegateTask.addGroupIdentityLink("group-2", "CANDIDATE")
    delegateTask.addUserIdentityLink("user-1", "CANDIDATE")
    delegateTask.addUserIdentityLink("user-2", "CANDIDATE")

    val taskInformation = delegateTask.toTaskInformation()

    assertThat(taskInformation.taskId).isEqualTo("taskId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_DEFINITION_ID]).isEqualTo("processDefinitionId")
    assertThat(taskInformation.meta[CommonRestrictions.ACTIVITY_ID]).isEqualTo("taskDefinitionKey")
    assertThat(taskInformation.meta[CommonRestrictions.TENANT_ID]).isEqualTo("tenantId")
    assertThat(taskInformation.meta["taskName"]).isEqualTo("name")
    assertThat(taskInformation.meta["taskDescription"]).isEqualTo("description")
    assertThat(taskInformation.meta["assignee"]).isEqualTo("assignee")
    assertThat(taskInformation.meta["creationDate"]).isEqualTo(now.toDateString())
    assertThat(taskInformation.meta["formKey"]).isNull()
    assertThat(taskInformation.meta["candidateUsers"]).isEqualTo("user-1,user-2")
    assertThat(taskInformation.meta["candidateGroups"]).isEqualTo("group-1,group-2")
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

  private fun identityLink(userId: String? = null, groupId: String? = null): IdentityLink {
    val identityLink = IdentityLinkEntity.newIdentityLink()
    identityLink.userId = userId
    identityLink.groupId = groupId
    return identityLink
  }

}
