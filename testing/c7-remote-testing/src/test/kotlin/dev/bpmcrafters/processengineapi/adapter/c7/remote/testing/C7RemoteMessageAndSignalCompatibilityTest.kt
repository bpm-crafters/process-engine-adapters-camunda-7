package dev.bpmcrafters.processengineapi.adapter.c7.remote.testing

import dev.bpmcrafters.processengineapi.testing.config.UseProcessTestInitializer
import dev.bpmcrafters.processengineapi.testing.contract.fixture.MessageAndSignalFixture
import dev.bpmcrafters.processengineapi.testing.contract.suite.MessageAndSignalCompatibilitySuite

@UseProcessTestInitializer(C7RemoteProcessTestInitializer.QUALIFIER)
class C7RemoteMessageAndSignalCompatibilityTest : MessageAndSignalCompatibilitySuite() {
  override fun messageAndSignalFixture() = object : MessageAndSignalFixture {
    override fun classpathResource() = "contract/c7/message-and-signal.bpmn"
    override fun definitionKey() = "contract-message-and-signal"
    override fun messageName() = "contract.continue"
    override fun correlationVariable() = "correlationKey"
    override fun correlationKey() = "customer-42"
    override fun signalName() = "contract.continue.signal"
    override fun messageCatchElementId() = "messageWait"
    override fun signalCatchElementId() = "signalWait"
    override fun endElementId() = "end"
  }
}
