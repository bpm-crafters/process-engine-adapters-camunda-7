package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.modification

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.bpmcrafters.processengineapi.task.*
import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.client.model.IdentityLinkDto
import org.camunda.community.rest.client.model.PatchVariablesDto
import org.camunda.community.rest.client.model.UserIdDto
import org.camunda.community.rest.client.model.VariableValueDto
import org.camunda.community.rest.variables.SpinValueMapper
import org.camunda.community.rest.variables.ValueMapper
import org.camunda.community.rest.variables.ValueTypeResolverImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.springframework.http.ResponseEntity
import java.util.*

internal class UserTaskModificationApiImplTest {
  private val taskApiClient: TaskApiClient = mock()
  private val valueMapper: ValueMapper = ValueMapper(
    objectMapper = jacksonObjectMapper(),
    valueTypeResolver = ValueTypeResolverImpl(),
    customValueMapper = listOf(SpinValueMapper(ValueTypeResolverImpl()))
  )
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
        object: ModifyTaskCmd {
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
  fun `add candidate users`() {
    whenever(taskApiClient.getIdentityLinks(taskId, "candidate")).thenReturn(
      ResponseEntity.ok(
        listOf(IdentityLinkDto().type("candidate").userId("kermit"))
      )
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.SetCandidateUsersTaskCmd(taskId = taskId, candidateUsers = listOf("kermit", "piggy"))
    ).get()
    verify(taskApiClient).getIdentityLinks(taskId, "candidate")
    verify(taskApiClient).addIdentityLink(taskId, IdentityLinkDto().userId("piggy").type("candidate"))
    verifyNoMoreInteractions(taskApiClient)
  }

  @Test
  fun `add candidate groups`() {
    whenever(taskApiClient.getIdentityLinks(taskId, "candidate")).thenReturn(
      ResponseEntity.ok(
        listOf(IdentityLinkDto().type("candidate").groupId("muppets").type("candidate"))
      )
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.SetCandidateGroupsTaskCmd(taskId = taskId, candidateGroups = listOf("muppets", "avengers"))
    ).get()
    verify(taskApiClient).getIdentityLinks(taskId, "candidate")
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
    verify(taskApiClient).modifyTaskLocalVariables(taskId, PatchVariablesDto()
      .modifications(valueMapper.mapValues(mapOf("key1" to "world"))) )
    verifyNoMoreInteractions(taskApiClient)
  }

}
