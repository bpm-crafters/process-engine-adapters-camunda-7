package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.modification

import dev.bpmcrafters.processengineapi.adapter.c7.remote.TestFixtures
import dev.bpmcrafters.processengineapi.task.*
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.client.model.CamundaFormRef
import org.camunda.community.rest.client.model.IdentityLinkDto
import org.camunda.community.rest.client.model.PatchVariablesDto
import org.camunda.community.rest.client.model.TaskDto
import org.camunda.community.rest.client.model.TaskWithAttachmentAndCommentDto
import org.camunda.community.rest.client.model.UserIdDto
import org.camunda.community.rest.client.model.VariableValueDto
import org.camunda.community.rest.variables.ValueMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.springframework.http.ResponseEntity
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.assertEquals

internal class UserTaskModificationApiImplTest {
  private val taskApiClient: TaskApiClient = mock()
  private val valueMapper: ValueMapper = TestFixtures.valueMapper()
  private val api: UserTaskModificationApi = UserTaskModificationApiImpl(taskApiClient, valueMapper)
  private lateinit var taskId: String

  @BeforeEach
  fun setUp() {
    Mockito.reset(taskApiClient)
    whenever(taskApiClient.addIdentityLink(any(), any())).thenReturn(ResponseEntity.ok().build())
    whenever(taskApiClient.deleteIdentityLink(any(), any())).thenReturn(ResponseEntity.ok().build())
    taskId = UUID.randomUUID().toString()
  }

  @Test
  fun `react on wrong assign command`() {
    assertThrows<UnsupportedOperationException> {
      api.update(
        object : ChangeAssignmentModifyTaskCmd(taskId) {

        }
      ).get()
    }
  }

  @Test
  fun `react on wrong modify command`() {
    assertThrows<UnsupportedOperationException> {
      api.update(
        object : ModifyTaskCmd {
          override val taskId: String
            get() = this@UserTaskModificationApiImplTest.taskId
        }
      ).get()
    }
  }

  @Test
  fun `react on wrong payload command`() {
    assertThrows<UnsupportedOperationException> {
      api.update(
        object : ChangePayloadModifyTaskCmd(taskId) {

        }
      ).get()
    }
  }


  @Test
  fun `update assignee and payload`() {
    api.update(
      TaskModification.taskModification(taskId) {
        assign("kermit")
        updatePayload(mapOf("key1" to "world"))
      }
    )
    verify(taskApiClient).setAssignee(taskId, UserIdDto().userId("kermit"))
    verify(taskApiClient).modifyTaskLocalVariables(taskId, PatchVariablesDto().modifications(valueMapper.mapValues(mapOf("key1" to "world"))))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `assign user task`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.AssignTaskCmd(taskId = taskId, assignee = "kermit")
    ).get()
    verify(taskApiClient).setAssignee(taskId, UserIdDto().userId("kermit"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `unassign user task`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.UnassignTaskCmd(taskId = taskId)
    ).get()
    verify(taskApiClient).setAssignee(taskId, UserIdDto())
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `add candidate user`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.AddCandidateUserTaskCmd(taskId = taskId, candidateUser = "kermit")
    ).get()
    verify(taskApiClient).addIdentityLink(taskId, IdentityLinkDto().userId("kermit").type("candidate"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `remove candidate user`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.RemoveCandidateUserTaskCmd(taskId = taskId, candidateUser = "kermit")
    ).get()
    verify(taskApiClient).deleteIdentityLink(taskId, IdentityLinkDto().userId("kermit").type("candidate"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `add candidate group`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.AddCandidateGroupTaskCmd(taskId = taskId, candidateGroup = "muppets")
    ).get()
    verify(taskApiClient).addIdentityLink(taskId, IdentityLinkDto().groupId("muppets").type("candidate"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `remove candidate group`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.RemoveCandidateGroupTaskCmd(taskId = taskId, candidateGroup = "muppets")
    ).get()
    verify(taskApiClient).deleteIdentityLink(taskId, IdentityLinkDto().groupId("muppets").type("candidate"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `set candidate users without previous`() {
    whenever(taskApiClient.getIdentityLinks(taskId, "candidate")).thenReturn(
      ResponseEntity.ok(
        listOf()
      )
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.SetCandidateUsersTaskCmd(taskId = taskId, candidateUsers = listOf("kermit", "piggy"))
    ).get()
    verify(taskApiClient).getIdentityLinks(taskId, "candidate")
    verify(taskApiClient).addIdentityLink(taskId, IdentityLinkDto().userId("piggy").type("candidate"))
    verify(taskApiClient).addIdentityLink(taskId, IdentityLinkDto().userId("kermit").type("candidate"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `set candidate groups without previous`() {
    whenever(taskApiClient.getIdentityLinks(taskId, "candidate")).thenReturn(
      ResponseEntity.ok(
        listOf()
      )
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.SetCandidateGroupsTaskCmd(taskId = taskId, candidateGroups = listOf("muppets", "avengers"))
    ).get()
    verify(taskApiClient).getIdentityLinks(taskId, "candidate")
    verify(taskApiClient).addIdentityLink(taskId, IdentityLinkDto().groupId("muppets").type("candidate"))
    verify(taskApiClient).addIdentityLink(taskId, IdentityLinkDto().groupId("avengers").type("candidate"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `clear candidate groups`() {
    whenever(taskApiClient.getIdentityLinks(taskId, "candidate")).thenReturn(
      ResponseEntity.ok(
        listOf(IdentityLinkDto().type("candidate").groupId("muppets"))
      )
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.ClearCandidateGroupsTaskCmd(taskId = taskId)
    ).get()
    verify(taskApiClient).getIdentityLinks(taskId, "candidate")
    verify(taskApiClient).deleteIdentityLink(taskId, IdentityLinkDto().groupId("muppets").type("candidate"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `clear candidate users`() {
    whenever(taskApiClient.getIdentityLinks(taskId, "candidate")).thenReturn(
      ResponseEntity.ok(
        listOf(IdentityLinkDto().type("candidate").userId("kermit"))
      )
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.ClearCandidateUsersTaskCmd(taskId = taskId)
    ).get()
    verify(taskApiClient).getIdentityLinks(taskId, "candidate")
    verify(taskApiClient).deleteIdentityLink(taskId, IdentityLinkDto().userId("kermit").type("candidate"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `set candidate groups`() {
    whenever(taskApiClient.getIdentityLinks(taskId, "candidate")).thenReturn(
      ResponseEntity.ok(
        listOf(IdentityLinkDto().type("candidate").groupId("muppets").type("candidate"))
      )
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.SetCandidateGroupsTaskCmd(taskId = taskId, candidateGroups = listOf("avengers", "turtles"))
    ).get()
    verify(taskApiClient).getIdentityLinks(taskId, "candidate")
    verify(taskApiClient).deleteIdentityLink(taskId, IdentityLinkDto().groupId("muppets").type("candidate"))
    verify(taskApiClient).addIdentityLink(taskId, IdentityLinkDto().groupId("avengers").type("candidate"))
    verify(taskApiClient).addIdentityLink(taskId, IdentityLinkDto().groupId("turtles").type("candidate"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `set candidate users`() {
    whenever(taskApiClient.getIdentityLinks(taskId, "candidate")).thenReturn(
      ResponseEntity.ok(
        listOf(IdentityLinkDto().type("candidate").userId("kermit"))
      )
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.SetCandidateUsersTaskCmd(taskId = taskId, candidateUsers = listOf("piggy", "fozzy"))
    ).get()
    verify(taskApiClient).getIdentityLinks(taskId, "candidate")
    verify(taskApiClient).deleteIdentityLink(taskId, IdentityLinkDto().userId("kermit").type("candidate"))
    verify(taskApiClient).addIdentityLink(taskId, IdentityLinkDto().userId("piggy").type("candidate"))
    verify(taskApiClient).addIdentityLink(taskId, IdentityLinkDto().userId("fozzy").type("candidate"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `delete payload`() {
    api.update(
      ChangePayloadModifyTaskCmd.DeletePayloadTaskCmd(taskId = taskId, payloadKeys = listOf("key1", "key2"))
    ).get()
    verify(taskApiClient).modifyTaskLocalVariables(taskId, PatchVariablesDto().deletions(listOf("key1", "key2")))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `clear payload`() {
    whenever(taskApiClient.getTaskLocalVariables(taskId, false)).thenReturn(
      ResponseEntity.ok(
        mapOf("key1" to VariableValueDto().value(1), "key2" to VariableValueDto().value("hello"))
      )
    )
    api.update(
      ChangePayloadModifyTaskCmd.ClearPayloadTaskCmd(taskId = taskId)
    ).get()
    verify(taskApiClient).getTaskLocalVariables(taskId, false)
    verify(taskApiClient).modifyTaskLocalVariables(taskId, PatchVariablesDto().deletions(listOf("key1", "key2")))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `set payload`() {
    api.update(
      ChangePayloadModifyTaskCmd.UpdatePayloadTaskCmd(taskId = taskId, payload = mapOf("key1" to "world"))
    ).get()
    verify(taskApiClient).modifyTaskLocalVariables(
      taskId, PatchVariablesDto()
        .modifications(valueMapper.mapValues(mapOf("key1" to "world")))
    )
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `set due date`() {
    val dueDate = OffsetDateTime.now().plusDays(7)
    val update = updateDate(ChangeDatesModifyTaskCmd.SetDueDateTaskCmd(taskId = taskId, dueDate = dueDate))

    assertThat(update.due).isEqualTo(dueDate)
  }

  @Test
  fun `clear due date`() {
    val update = updateDate(ChangeDatesModifyTaskCmd.ClearDueDateTaskCmd(taskId = taskId))

    assertThat(update.due).isNull()
  }

  @Test
  fun `set follow-up date`() {
    val followUpDate = OffsetDateTime.now().plusDays(3)
    val update = updateDate(ChangeDatesModifyTaskCmd.SetFollowUpDateTaskCmd(taskId = taskId, followUpDate = followUpDate))

    assertThat(update.followUp).isEqualTo(followUpDate)
  }

  @Test
  fun `clear follow-up date`() {
    val update = updateDate(ChangeDatesModifyTaskCmd.ClearFollowUpDateTaskCmd(taskId = taskId))

    assertThat(update.followUp).isNull()
  }

  @Test
  fun `fail date update when task is not found`() {
    whenever(taskApiClient.getTask(taskId)).thenReturn(ResponseEntity.ok(null))

    val exception = assertThrows<IllegalArgumentException> {
      api.update(ChangeDatesModifyTaskCmd.ClearFollowUpDateTaskCmd(taskId = taskId))
    }

    assertThat(exception).hasMessage("Task with id $taskId not found.")
    verify(taskApiClient).getTask(taskId)
    verifyNoMoreInteractions(taskApiClient)
  }

  private fun updateDate(command: ChangeDatesModifyTaskCmd): TaskDto {
    val currentTask = taskWithAllFields()
    whenever(taskApiClient.getTask(taskId)).thenReturn(ResponseEntity.ok(currentTask))

    api.update(command).get()

    val taskUpdateCaptor = argumentCaptor<TaskDto>()
    verify(taskApiClient).getTask(taskId)
    verify(taskApiClient).updateTask(eq(taskId), taskUpdateCaptor.capture())
    assertTaskFieldsAreCopied(currentTask, taskUpdateCaptor.firstValue)
    verifyNoMoreInteractions(taskApiClient)
    return taskUpdateCaptor.firstValue
  }

  private fun taskWithAllFields() = TaskWithAttachmentAndCommentDto()
    .id("task-id")
    .name("task name")
    .assignee("kermit")
    .owner("gonzo")
    .created(OffsetDateTime.parse("2026-01-02T03:04:05Z"))
    .lastUpdated(OffsetDateTime.parse("2026-01-03T03:04:05Z"))
    .due(OffsetDateTime.parse("2026-01-04T03:04:05Z"))
    .followUp(OffsetDateTime.parse("2026-01-05T03:04:05Z"))
    .delegationState(TaskWithAttachmentAndCommentDto.DelegationStateEnum.PENDING)
    .description("task description")
    .executionId("execution-id")
    .parentTaskId("parent-task-id")
    .priority(73)
    .processDefinitionId("process-definition-id")
    .processInstanceId("process-instance-id")
    .caseExecutionId("case-execution-id")
    .caseDefinitionId("case-definition-id")
    .caseInstanceId("case-instance-id")
    .taskDefinitionKey("task-definition-key")
    .suspended(false)
    .formKey("form-key")
    .camundaFormRef(CamundaFormRef().key("form-ref-key").binding("version").version(2))
    .tenantId("tenant-id")
    .taskState("task-state")

  private fun assertTaskFieldsAreCopied(currentTask: TaskWithAttachmentAndCommentDto, taskUpdate: TaskDto) {
    assertThat(taskUpdate.id).isEqualTo(currentTask.id)
    assertThat(taskUpdate.name).isEqualTo(currentTask.name)
    assertThat(taskUpdate.assignee).isEqualTo(currentTask.assignee)
    assertThat(taskUpdate.owner).isEqualTo(currentTask.owner)
    assertThat(taskUpdate.created).isEqualTo(currentTask.created)
    assertThat(taskUpdate.lastUpdated).isEqualTo(currentTask.lastUpdated)
    assertThat(taskUpdate.delegationState?.value).isEqualTo(currentTask.delegationState?.value)
    assertThat(taskUpdate.description).isEqualTo(currentTask.description)
    assertThat(taskUpdate.executionId).isEqualTo(currentTask.executionId)
    assertThat(taskUpdate.parentTaskId).isEqualTo(currentTask.parentTaskId)
    assertEquals(currentTask.priority, taskUpdate.priority)
    assertThat(taskUpdate.processDefinitionId).isEqualTo(currentTask.processDefinitionId)
    assertThat(taskUpdate.processInstanceId).isEqualTo(currentTask.processInstanceId)
    assertThat(taskUpdate.caseExecutionId).isEqualTo(currentTask.caseExecutionId)
    assertThat(taskUpdate.caseDefinitionId).isEqualTo(currentTask.caseDefinitionId)
    assertThat(taskUpdate.caseInstanceId).isEqualTo(currentTask.caseInstanceId)
    assertThat(taskUpdate.taskDefinitionKey).isEqualTo(currentTask.taskDefinitionKey)
    assertEquals(currentTask.suspended, taskUpdate.suspended)
    assertThat(taskUpdate.formKey).isEqualTo(currentTask.formKey)
    assertThat(taskUpdate.camundaFormRef).isEqualTo(currentTask.camundaFormRef)
    assertThat(taskUpdate.tenantId).isEqualTo(currentTask.tenantId)
    assertThat(taskUpdate.taskState).isEqualTo(currentTask.taskState)
  }

}
