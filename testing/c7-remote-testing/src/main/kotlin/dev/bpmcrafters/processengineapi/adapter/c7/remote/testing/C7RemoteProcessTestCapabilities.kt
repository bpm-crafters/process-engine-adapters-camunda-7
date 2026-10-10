package dev.bpmcrafters.processengineapi.adapter.c7.remote.testing

import dev.bpmcrafters.processengineapi.testing.api.ProcessTestCapabilities
import java.time.Duration

internal class C7RemoteProcessTestCapabilities : ProcessTestCapabilities {
  override fun supportsTimeTravel() = false
  override fun timePasses(duration: Duration): Nothing = throw UnsupportedOperationException("Remote Camunda engines do not support test time travel.")
}
