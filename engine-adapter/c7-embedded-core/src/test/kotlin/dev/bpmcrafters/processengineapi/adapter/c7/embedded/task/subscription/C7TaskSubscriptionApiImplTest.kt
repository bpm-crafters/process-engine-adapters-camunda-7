package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.subscription

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class C7TaskSubscriptionApiImplTest {

  private val subscriptionRepository = InMemSubscriptionRepository()
  private val taskSubscriptionApi = C7TaskSubscriptionApiImpl(subscriptionRepository)

  @Test
  fun `accepts every restriction the task deliveries evaluate`() {
    val restrictions = mapOf(
      CommonRestrictions.EXECUTION_ID to "execution-1",
      CommonRestrictions.ACTIVITY_ID to "activity-1",
      CommonRestrictions.BUSINESS_KEY to "business-key-1",
      CommonRestrictions.WITHOUT_TENANT_ID to "true",
      CommonRestrictions.PROCESS_INSTANCE_ID to "process-instance-1",
      CommonRestrictions.PROCESS_DEFINITION_ID to "process-definition-1",
      CommonRestrictions.PROCESS_DEFINITION_KEY to "process-definition-key-1",
      CommonRestrictions.PROCESS_DEFINITION_VERSION_TAG to "v1",
      CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS to "25000",
    )

    taskSubscriptionApi.subscribeForTask(subscribeCmd(restrictions)).get()

    assertThat(subscriptionRepository.getTaskSubscriptions()).hasSize(1)
  }

  @Test
  fun `rejects an unsupported restriction`() {
    assertThatThrownBy {
      taskSubscriptionApi.subscribeForTask(subscribeCmd(mapOf(CommonRestrictions.CORRELATION_KEY to "key-1")))
    }.isInstanceOf(IllegalArgumentException::class.java)

    assertThat(subscriptionRepository.getTaskSubscriptions()).isEmpty()
  }

  private fun subscribeCmd(restrictions: Map<String, String>) = SubscribeForTaskCmd(
    restrictions = restrictions,
    taskType = TaskType.EXTERNAL,
    taskDescriptionKey = "topic",
    payloadDescription = null,
    action = { _, _ -> },
    termination = { }
  )
}
