package dev.bpmcrafters.processengineapi.adapter.c7.embedded.testing

import dev.bpmcrafters.processengineapi.testing.config.UseProcessTestInitializer
import dev.bpmcrafters.processengineapi.testing.contract.fixture.*
import dev.bpmcrafters.processengineapi.testing.contract.suite.*

@UseProcessTestInitializer(C7EmbeddedProcessTestInitializer.QUALIFIER)
class C7EmbeddedUserTaskProcessCompatibilityTest : UserTaskProcessCompatibilitySuite() {
  override fun userTaskProcessFixture() = object : UserTaskProcessFixture {
    override fun classpathResource() = "contract/c7/user-task.bpmn"
    override fun definitionKey() = "contract-user-task"
    override fun userTaskDescriptionKey() = "approval"
    override fun startElementId() = "start"
    override fun userTaskElementId() = "approval"
    override fun endElementId() = "end"
  }
}

@UseProcessTestInitializer(C7EmbeddedProcessTestInitializer.QUALIFIER)
class C7EmbeddedUserTaskAssignmentCompatibilityTest : UserTaskAssignmentCompatibilitySuite() {
  override fun userTaskAssignmentFixture() = object : UserTaskAssignmentFixture {
    override fun classpathResource() = "contract/c7/user-task-assignment.bpmn"
    override fun definitionKey() = "contract-user-task-assignment"
    override fun userTaskDescriptionKey() = "approval"
    override fun initialAssignee() = "gonzo"
    override fun assignee() = "kermit"
    override fun assigneeMetadataKey() = "assignee"
  }
}

@UseProcessTestInitializer(C7EmbeddedProcessTestInitializer.QUALIFIER)
class C7EmbeddedExternalTaskCompatibilityTest : ExternalTaskCompatibilitySuite() {
  override fun externalTaskProcessFixture() = object : ExternalTaskProcessFixture {
    override fun classpathResource() = "contract/c7/external-task.bpmn"
    override fun definitionKey() = "contract-external-task"
    override fun externalTaskDescriptionKey() = "contract-worker"
    override fun endElementId() = "end"
  }
}

@UseProcessTestInitializer(C7EmbeddedProcessTestInitializer.QUALIFIER)
class C7EmbeddedMessageAndSignalCompatibilityTest : MessageAndSignalCompatibilitySuite() {
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

@UseProcessTestInitializer(C7EmbeddedProcessTestInitializer.QUALIFIER)
class C7EmbeddedTimerCompatibilityTest : TimerCompatibilitySuite() {
  override fun timerProcessFixture() = object : TimerProcessFixture {
    override fun classpathResource() = "contract/c7/timer.bpmn"
    override fun definitionKey() = "contract-timer"
    override fun timerElementId() = "timerWait"
    override fun userTaskDescriptionKey() = "approval"
  }
}

@UseProcessTestInitializer(C7EmbeddedProcessTestInitializer.QUALIFIER)
class C7EmbeddedAlternateStartCompatibilityTest : AlternateStartCompatibilitySuite() {
  override fun alternateStartFixture() = object : AlternateStartFixture {
    override fun classpathResource() = "contract/c7/alternate-starts.bpmn"
    override fun definitionKey() = "contract-alternate-starts"
    override fun startMessageName() = "contract.start"
    override fun entryElementId() = "approval"
    override fun userTaskDescriptionKey() = "approval"
  }
}
