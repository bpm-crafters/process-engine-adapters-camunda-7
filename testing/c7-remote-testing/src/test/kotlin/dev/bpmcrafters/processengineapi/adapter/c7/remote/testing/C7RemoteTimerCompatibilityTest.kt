package dev.bpmcrafters.processengineapi.adapter.c7.remote.testing

import dev.bpmcrafters.processengineapi.testing.config.UseProcessTestInitializer
import dev.bpmcrafters.processengineapi.testing.contract.fixture.*
import dev.bpmcrafters.processengineapi.testing.contract.suite.*
import org.junit.jupiter.api.Disabled

@Disabled("Camunda 7 REST does not expose controllable engine time.")
@UseProcessTestInitializer(C7RemoteProcessTestInitializer.QUALIFIER)
class C7RemoteTimerCompatibilityTest : TimerCompatibilitySuite() {
  override fun timerProcessFixture() = object : TimerProcessFixture {
    override fun classpathResource() = "contract/c7/timer.bpmn"
    override fun definitionKey() = "contract-timer"
    override fun timerElementId() = "timerWait"
    override fun userTaskDescriptionKey() = "approval"
  }
}
