package dev.bpmcrafters.processengineapi.adapter.c7.embedded.testing

import dev.bpmcrafters.processengineapi.testing.api.ProcessTestCapabilities
import org.camunda.bpm.engine.ProcessEngine
import org.camunda.bpm.engine.impl.util.ClockUtil
import java.time.Duration
import java.util.*

internal class C7EmbeddedProcessTestCapabilities(private val engine: ProcessEngine) : ProcessTestCapabilities {
  override fun supportsTimeTravel() = true
  override fun timePasses(duration: Duration) {
    ClockUtil.setCurrentTime(Date.from(ClockUtil.getCurrentTime().toInstant().plus(duration)))
    engine.managementService.createJobQuery().executable().list().forEach { engine.managementService.executeJob(it.id) }
  }
}
