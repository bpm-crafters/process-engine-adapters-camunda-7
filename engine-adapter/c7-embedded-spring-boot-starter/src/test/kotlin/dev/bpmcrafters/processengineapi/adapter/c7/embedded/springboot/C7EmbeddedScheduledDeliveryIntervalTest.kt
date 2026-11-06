package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.schedule.C7EmbeddedServiceTaskPullStrategyAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.schedule.C7EmbeddedUserTaskPullStrategyAutoConfiguration
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
    "dev.bpm-crafters.process-api.adapter.c7embedded.service-tasks.schedule-delivery-fixed-rate = 200ms",
    "dev.bpm-crafters.process-api.adapter.c7embedded.user-tasks.schedule-delivery-fixed-rate = 300ms"
  ]
)
@ActiveProfiles("itest")
@DirtiesContext
class C7EmbeddedScheduledDeliveryIntervalTest {

  @Autowired
  lateinit var context: ApplicationContext

  @Test
  fun `schedules pulls with the configured sub-second delay`() {
    assertThat(scheduledDelayOf(C7EmbeddedServiceTaskPullStrategyAutoConfiguration::class.java)).isEqualTo(Duration.ofMillis(200))
    assertThat(scheduledDelayOf(C7EmbeddedUserTaskPullStrategyAutoConfiguration::class.java)).isEqualTo(Duration.ofMillis(300))
  }

  private fun scheduledDelayOf(configurationType: Class<*>): Duration =
    context.getBeansOfType(ScheduledTaskHolder::class.java).values
      .flatMap { it.scheduledTasks }
      .map { it.task }
      .filterIsInstance<FixedDelayTask>()
      .single { it.toString().startsWith(configurationType.name) }
      .intervalDuration
}
