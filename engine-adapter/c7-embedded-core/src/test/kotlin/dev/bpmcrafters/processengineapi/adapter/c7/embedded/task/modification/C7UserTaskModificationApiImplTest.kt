package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.modification

import dev.bpmcrafters.processengineapi.task.ChangeAssignmentModifyTaskCmd
import dev.bpmcrafters.processengineapi.task.ChangePayloadModifyTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskModification
import dev.bpmcrafters.processengineapi.task.UserTaskModificationApi
import org.camunda.bpm.engine.TaskService
import org.camunda.bpm.engine.impl.persistence.entity.IdentityLinkEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.*

internal class C7UserTaskModificationApiImplTest {
  private val taskService: TaskService = mock()
  private val api: UserTaskModificationApi = C7UserTaskModificationApiImpl(taskService)
  private lateinit var taskId: String

  @BeforeEach
  fun setUp() {
    Mockito.reset(taskService)
    taskId = UUID.randomUUID().toString()
  }

  @Test
  fun `react on wrong assign command`() {
    assertThrows<UnsupportedOperationException> {
      api.update(
        object: ChangeAssignmentModifyTaskCmd(taskId) {

        }
      ).get()
    }
  }

  @Test
  fun `react on wrong payload command`() {
    assertThrows<UnsupportedOperationException> {
      api.update(
        object: ChangePayloadModifyTaskCmd(taskId) {

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
    verify(taskService).setAssignee(taskId, "kermit")
    verify(taskService).setVariablesLocal(taskId, mapOf("key1" to "world"))
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `assign user task`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.AssignTaskCmd(taskId = taskId, assignee = "kermit")
    ).get()
    verify(taskService).setAssignee(taskId, "kermit")
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `unassign user task`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.UnassignTaskCmd(taskId = taskId)
    ).get()
    verify(taskService).setAssignee(taskId, null)
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `add candidate user`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.AddCandidateUserTaskCmd(taskId = taskId, candidateUser = "kermit")
    ).get()
    verify(taskService).addCandidateUser(taskId, "kermit")
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `remove candidate user`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.RemoveCandidateUserTaskCmd(taskId = taskId, candidateUser = "kermit")
    ).get()
    verify(taskService).deleteCandidateUser(taskId, "kermit")
    verifyNoMoreInteractions(taskService)
  }
  @Test
  fun `add candidate group`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.AddCandidateGroupTaskCmd(taskId = taskId, candidateGroup = "muppets")
    ).get()
    verify(taskService).addCandidateGroup(taskId, "muppets")
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `remove candidate group`() {
    api.update(
      ChangeAssignmentModifyTaskCmd.RemoveCandidateGroupTaskCmd(taskId = taskId, candidateGroup = "muppets")
    ).get()
    verify(taskService).deleteCandidateGroup(taskId, "muppets")
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `add candidate users`() {
    whenever(taskService.getIdentityLinksForTask(taskId)).thenReturn(
      listOf(IdentityLinkEntity().apply {
        id = taskId
        type = "candidate"
        userId = "kermit"
      })
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.SetCandidateUsersTaskCmd(taskId = taskId, candidateUsers = listOf("kermit", "piggy"))
    ).get()
    verify(taskService).getIdentityLinksForTask(taskId)
    verify(taskService).addCandidateUser(taskId, "piggy")
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `add candidate groups`() {
    whenever(taskService.getIdentityLinksForTask(taskId)).thenReturn(
      listOf(IdentityLinkEntity().apply {
        id = taskId
        type = "candidate"
        groupId = "muppets"
      })
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.SetCandidateGroupsTaskCmd(taskId = taskId, candidateGroups = listOf("muppets", "avengers"))
    ).get()
    verify(taskService).getIdentityLinksForTask(taskId)
    verify(taskService).addCandidateGroup(taskId, "avengers")
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `clear candidate groups`() {
    whenever(taskService.getIdentityLinksForTask(taskId)).thenReturn(
      listOf(IdentityLinkEntity().apply {
        id = taskId
        type = "candidate"
        groupId = "muppets"
      })
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.ClearCandidateGroupsTaskCmd(taskId = taskId)
    ).get()
    verify(taskService).getIdentityLinksForTask(taskId)
    verify(taskService).deleteCandidateGroup(taskId, "muppets")
    verifyNoMoreInteractions(taskService)
  }
  @Test
  fun `clear candidate users`() {
    whenever(taskService.getIdentityLinksForTask(taskId)).thenReturn(
      listOf(IdentityLinkEntity().apply {
        id = taskId
        type = "candidate"
        userId = "kermit"
      })
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.ClearCandidateUsersTaskCmd(taskId = taskId)
    ).get()
    verify(taskService).getIdentityLinksForTask(taskId)
    verify(taskService).deleteCandidateUser(taskId, "kermit")
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `set candidate groups`() {
    whenever(taskService.getIdentityLinksForTask(taskId)).thenReturn(
      listOf(IdentityLinkEntity().apply {
        id = taskId
        type = "candidate"
        groupId = "muppets"
      })
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.SetCandidateGroupsTaskCmd(taskId = taskId, candidateGroups = listOf("avengers", "turtles"))
    ).get()
    verify(taskService).getIdentityLinksForTask(taskId)
    verify(taskService).deleteCandidateGroup(taskId, "muppets")
    verify(taskService).addCandidateGroup(taskId, "avengers")
    verify(taskService).addCandidateGroup(taskId, "turtles")
    verifyNoMoreInteractions(taskService)
  }
  @Test
  fun `set candidate users`() {
    whenever(taskService.getIdentityLinksForTask(taskId)).thenReturn(
      listOf(IdentityLinkEntity().apply {
        id = taskId
        type = "candidate"
        userId = "kermit"
      })
    )
    api.update(
      ChangeAssignmentModifyTaskCmd.SetCandidateUsersTaskCmd(taskId = taskId, candidateUsers = listOf("piggy", "fozzy"))
    ).get()
    verify(taskService).getIdentityLinksForTask(taskId)
    verify(taskService).deleteCandidateUser(taskId, "kermit")
    verify(taskService).addCandidateUser(taskId, "piggy")
    verify(taskService).addCandidateUser(taskId, "fozzy")
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `delete payload`() {
    api.update(
      ChangePayloadModifyTaskCmd.DeletePayloadTaskCmd(taskId = taskId, payloadKeys = listOf("key1", "key2"))
    ).get()
    verify(taskService).removeVariablesLocal(taskId, listOf("key1", "key2"))
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `clear payload`() {
    whenever(taskService.getVariablesLocal(taskId)).thenReturn(
      mapOf("key1" to 1, "key2" to "hello")
    )
    api.update(
      ChangePayloadModifyTaskCmd.ClearPayloadTaskCmd(taskId = taskId)
    ).get()
    verify(taskService).getVariablesLocal(taskId)
    verify(taskService).removeVariablesLocal(taskId, listOf("key1", "key2"))
    verifyNoMoreInteractions(taskService)
  }

  @Test
  fun `set payload`() {
    api.update(
      ChangePayloadModifyTaskCmd.UpdatePayloadTaskCmd(taskId = taskId, payload = mapOf("key1" to "world"))
    ).get()
    verify(taskService).setVariablesLocal(taskId, mapOf("key1" to "world"))
    verifyNoMoreInteractions(taskService)
  }

}
