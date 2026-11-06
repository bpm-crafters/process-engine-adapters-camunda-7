package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.schedule.PullServiceTaskDeliveryAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.schedule.PullUserTaskDeliveryAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.config.FixedDelayTask
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Duration

@SpringBootTest(
  properties = [
    "dev.bpm-crafters.process-api.adapter.c7remote.service-tasks.schedule-delivery-fixed-rate = 200ms",
    "dev.bpm-crafters.process-api.adapter.c7remote.user-tasks.schedule-delivery-fixed-rate = 300ms"
  ]
)
@ActiveProfiles("itest")
@DirtiesContext
class C7RemoteScheduledDeliveryIntervalTest {

  @Autowired
  lateinit var context: ApplicationContext

  @Test
  fun `schedules pulls with the configured sub-second delay`() {
    assertThat(scheduledDelayOf(PullServiceTaskDeliveryAutoConfiguration::class.java)).isEqualTo(Duration.ofMillis(200))
    assertThat(scheduledDelayOf(PullUserTaskDeliveryAutoConfiguration::class.java)).isEqualTo(Duration.ofMillis(300))
  }

  private fun scheduledDelayOf(configurationType: Class<*>): Duration =
    context.getBeansOfType(ScheduledTaskHolder::class.java).values
      .flatMap { it.scheduledTasks }
      .map { it.task }
      .filterIsInstance<FixedDelayTask>()
      .single { it.toString().startsWith(configurationType.name) }
      .intervalDuration
}
