package dev.bpmcrafters.processengineapi.adapter.c7.remote.testing

import dev.bpmcrafters.processengineapi.testing.config.UseProcessTestInitializer
import dev.bpmcrafters.processengineapi.testing.contract.fixture.UserTaskAssignmentFixture
import dev.bpmcrafters.processengineapi.testing.contract.suite.UserTaskAssignmentCompatibilitySuite

@UseProcessTestInitializer(C7RemoteProcessTestInitializer.QUALIFIER)
class C7RemoteUserTaskAssignmentCompatibilityTest : UserTaskAssignmentCompatibilitySuite() {
  override fun userTaskAssignmentFixture() = object : UserTaskAssignmentFixture {
    override fun classpathResource() = "contract/c7/user-task-assignment.bpmn"
    override fun definitionKey() = "contract-user-task-assignment"
    override fun userTaskDescriptionKey() = "approval"
    override fun initialAssignee() = "gonzo"
    override fun assignee() = "kermit"
    override fun assigneeMetadataKey() = "assignee"
  }
}
