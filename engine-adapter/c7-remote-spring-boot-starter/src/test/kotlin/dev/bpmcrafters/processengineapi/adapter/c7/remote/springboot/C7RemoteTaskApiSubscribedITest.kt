package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import io.toolisticon.testing.jgiven.GIVEN
import io.toolisticon.testing.jgiven.THEN
import io.toolisticon.testing.jgiven.WHEN
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.client.exception.NotFoundException
import org.camunda.bpm.client.exception.RestException
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles

/**
 * Special test for external task delivery strategy `remote_subscribed`.
 */
@ActiveProfiles("itest-subscribed")
class C7RemoteTaskApiSubscribedITest : AbstractC7RemoteApiITestBase() {

  @Test
  fun `should get subscribed for external task with subscribed strategy`() {
    GIVEN
      .`a active external task subscription`(EXTERNAL_TASK)

    WHEN
      .`start process by definition`(KEY)

    THEN
      .`we should get notified about a new external task`()
  }


  @Test
  fun `should not get subscribed for external task with subscribe strategy after unsubscribe`() {
    GIVEN
      .`a active external task subscription`(USER_TASK)

    WHEN
      .`unsubscribe external task subscription`()
      .`start process by definition`(KEY)

    THEN
      .`we should not get notified about a new external task`()
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
  fun `official client classifies Camunda's missing external task response after process deletion`() {
    val missing = completeDeletedExternalTask()

    val cause = assertInstanceOf(NotFoundException::class.java, missing.cause)
    val response = assertInstanceOf(RestException::class.java, cause.cause)
    assertThat(response.httpStatusCode).isEqualTo(404)
    assertThat(response.type).isEqualTo("RestException")
    assertThat(response.message).isEqualTo("External task with id ${missing.taskId} does not exist")
  }

}
