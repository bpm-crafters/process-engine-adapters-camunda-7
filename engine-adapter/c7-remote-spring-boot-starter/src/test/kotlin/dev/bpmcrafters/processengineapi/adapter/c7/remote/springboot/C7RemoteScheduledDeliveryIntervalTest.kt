package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import dev.bpmcrafters.processengineapi.springboot.lifecycle.ProcessApplicationRunningEvent
import dev.bpmcrafters.processengineapi.springboot.lifecycle.ProcessApplicationStoppingEvent
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.TaskScheduler
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture

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

  @MockitoBean(name = "c7remote-task-scheduler")
  lateinit var taskScheduler: TaskScheduler

  @Test
  fun `starts and stops scheduled pulls through lifecycle events`() {
    val scheduledServiceTaskDelivery = mock(ScheduledFuture::class.java)
    clearInvocations(taskScheduler)
    `when`(
      taskScheduler.scheduleWithFixedDelay(
        any(Runnable::class.java),
        any(Instant::class.java),
        eq(Duration.ofMillis(200))
      )
    ).thenReturn(scheduledServiceTaskDelivery)

    context.publishEvent(ProcessApplicationRunningEvent())

    verify(taskScheduler).scheduleWithFixedDelay(any(), any(), eq(Duration.ofMillis(200)))
    verify(taskScheduler).scheduleWithFixedDelay(any(), any(), eq(Duration.ofMillis(300)))

    context.publishEvent(ProcessApplicationStoppingEvent())

    verify(scheduledServiceTaskDelivery).cancel(false)
  }
}
