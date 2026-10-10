package dev.bpmcrafters.processengineapi.adapter.c7.remote.testing

import dev.bpmcrafters.processengineapi.testing.config.UseProcessTestInitializer
import dev.bpmcrafters.processengineapi.testing.contract.fixture.UserTaskProcessFixture
import dev.bpmcrafters.processengineapi.testing.contract.suite.UserTaskProcessCompatibilitySuite

@UseProcessTestInitializer(C7RemoteProcessTestInitializer.QUALIFIER)
class C7RemoteUserTaskProcessCompatibilityTest : UserTaskProcessCompatibilitySuite() {
  override fun userTaskProcessFixture() = object : UserTaskProcessFixture {
    override fun classpathResource() = "contract/c7/user-task.bpmn"
    override fun definitionKey() = "contract-user-task"
    override fun userTaskDescriptionKey() = "approval"
    override fun startElementId() = "start"
    override fun userTaskElementId() = "approval"
    override fun endElementId() = "end"
  }
}
