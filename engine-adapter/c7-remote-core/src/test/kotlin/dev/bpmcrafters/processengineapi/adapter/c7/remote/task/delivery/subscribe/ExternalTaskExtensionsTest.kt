package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.subscribe

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.toDateString
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.client.task.ExternalTask
import org.camunda.bpm.client.task.impl.ExternalTaskImpl
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

internal class ExternalTaskExtensionsTest {
  @Test
  fun `should map ExternalTask for official client`() {
    val now = Date.from(Instant.now())

    val externalTask: ExternalTask = ExternalTaskImpl().apply {
      processDefinitionId = "processDefinitionId"
      processInstanceId = "processInstanceId"
      tenantId = "tenantId"
      topicName = "topicName"
      id = "taskId"
      activityId = "activityId"
      activityInstanceId = "activityInstanceId"
      createTime = now
    }

    val taskInformation = externalTask.toTaskInformation()

    assertThat(taskInformation.taskId).isEqualTo("taskId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_DEFINITION_ID]).isEqualTo("processDefinitionId")
    assertThat(taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID]).isEqualTo("processInstanceId")
    assertThat(taskInformation.meta[CommonRestrictions.ACTIVITY_ID]).isEqualTo("activityId")
    assertThat(taskInformation.meta[CommonRestrictions.TENANT_ID]).isEqualTo("tenantId")
    assertThat(taskInformation.meta["topicName"]).isEqualTo("topicName")
    assertThat(taskInformation.meta["creationDate"]).isEqualTo(now.toDateString())

  }

}
