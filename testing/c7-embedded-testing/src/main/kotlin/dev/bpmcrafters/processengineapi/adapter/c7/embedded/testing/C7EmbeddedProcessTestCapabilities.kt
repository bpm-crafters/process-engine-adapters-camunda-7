package dev.bpmcrafters.processengineapi.adapter.c7.embedded.testing

import dev.bpmcrafters.processengineapi.testing.api.ProcessTestCapabilities
import org.camunda.bpm.engine.ProcessEngine
import org.camunda.bpm.engine.impl.util.ClockUtil
import java.time.Duration
import java.util.*

internal class C7EmbeddedProcessTestCapabilities(private val engine: ProcessEngine) : ProcessTestCapabilities {
  private var currentTime = Date()
  override fun supportsTimeTravel() = true
  override fun timePasses(duration: Duration) {
    currentTime = Date.from(currentTime.toInstant().plus(duration)); ClockUtil.setCurrentTime(currentTime); engine.managementService.createJobQuery()
      .executable().list().forEach { engine.managementService.executeJob(it.id) }
  }
}
