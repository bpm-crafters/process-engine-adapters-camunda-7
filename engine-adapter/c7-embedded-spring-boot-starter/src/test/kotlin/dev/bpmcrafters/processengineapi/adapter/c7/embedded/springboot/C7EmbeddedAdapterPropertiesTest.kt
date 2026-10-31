package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties.ExternalServiceTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties.ServiceTasks
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties.UserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties.UserTasks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class C7EmbeddedAdapterPropertiesTest {

  @Test
  fun `falls back to default seconds when no duration is set`() {
    assertThat(userTasks().scheduleDeliveryIntervalInMilliseconds).isEqualTo(5_000)
    assertThat(serviceTasks().scheduleDeliveryIntervalInMilliseconds).isEqualTo(13_000)
  }

  @Test
  fun `uses configured seconds when no duration is set`() {
    val tasks = userTasks().copy(scheduleDeliveryFixedRateInSeconds = 2)

    assertThat(tasks.scheduleDeliveryIntervalInMilliseconds).isEqualTo(2_000)
  }

  @Test
  fun `duration takes precedence over seconds`() {
    val rate = Duration.ofMillis(200)

    assertThat(userTasks().copy(scheduleDeliveryFixedRate = rate).scheduleDeliveryIntervalInMilliseconds).isEqualTo(200)
    assertThat(serviceTasks().copy(scheduleDeliveryFixedRate = rate).scheduleDeliveryIntervalInMilliseconds).isEqualTo(200)
  }

  private fun userTasks() = UserTasks(deliveryStrategy = UserTaskDeliveryStrategy.EMBEDDED_SCHEDULED)

  private fun serviceTasks() = ServiceTasks(
    workerId = "worker",
    deliveryStrategy = ExternalServiceTaskDeliveryStrategy.EMBEDDED_SCHEDULED,
  )
}
