package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import dev.bpmcrafters.processengineapi.task.ChangeDatesModifyTaskCmd.SetFollowUpDateTaskCmd
import dev.bpmcrafters.processengineapi.task.UserTaskModificationApi
import io.toolisticon.testing.jgiven.GIVEN
import io.toolisticon.testing.jgiven.THEN
import io.toolisticon.testing.jgiven.WHEN
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.client.model.TaskDto
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals

@ActiveProfiles("itest")
class C7RemoteTaskApiITest : AbstractC7RemoteApiITestBase() {

  @Autowired
  lateinit var userTaskModificationApi: UserTaskModificationApi

  @Autowired
  lateinit var taskApiClient: TaskApiClient

  @Test
  fun `should get subscribed for user task with pull strategy`() {
    GIVEN
      .`a active user task subscription`(USER_TASK)

    WHEN
      .`start process by definition`(KEY)

    THEN
      .`we should get notified about a new user task with pull strategy`()
  }

  @Test
  fun `should get subscribed for external task with pull strategy`() {
    GIVEN
      .`a active external task subscription`(EXTERNAL_TASK)

    WHEN
      .`start process by definition`(KEY)

    THEN
      .`we should get notified about a new external task`()
  }

  @Test
  fun `should not get subscribed for user task with pull strategy after unsubscribe`() {
    GIVEN
      .`a active user task subscription`(USER_TASK)

    WHEN
      .`unsubscribe user task subscription`()
      .`start process by definition`(KEY)

    THEN
      .`we should not get notified about a new user task with pull strategy`()
  }

  @Test
  fun `should not get subscribed for external task with pull strategy after unsubscribe`() {
    GIVEN
      .`a active external task subscription`(USER_TASK)

    WHEN
      .`unsubscribe external task subscription`()
      .`start process by definition`(KEY)

    THEN
      .`we should not get notified about a new external task`()
  }

  @Test
  fun `should complete a user task`() {
    GIVEN
      .`a active user task subscription`(USER_TASK)
      .`start process by definition`(KEY)

    THEN
      .`we should get notified about a new user task with pull strategy`()

    WHEN
      .`complete the user task`()
  }

  @Test
  fun `should complete a external task`() {
    GIVEN
      .`a active external task subscription`(EXTERNAL_TASK)
      .`start process by definition`(KEY)

    THEN
      .`we should get notified about a new external task`()

    WHEN
      .`complete the external task`()
  }

  @Test
  fun `should preserve existing task attributes when setting follow-up date`() {
    val taskId = UUID.randomUUID().toString()
    val followUpDate = OffsetDateTime.now().plusDays(3)

    taskApiClient.createTask(
      TaskDto()
        .id(taskId)
        .name("Keep task name")
        .description("Keep task description")
        .priority(73)
    )

    userTaskModificationApi.update(
      SetFollowUpDateTaskCmd(taskId = taskId, followUpDate = followUpDate)
    ).get()

    val updatedTask = taskApiClient.getTask(taskId).body!!
    assertThat(updatedTask.followUp!!.toInstant()).isEqualTo(followUpDate.toInstant().truncatedTo(ChronoUnit.MILLIS))
    assertThat(updatedTask.name).isEqualTo("Keep task name")
    assertThat(updatedTask.description).isEqualTo("Keep task description")
    assertEquals(73, updatedTask.priority)
  }

}
