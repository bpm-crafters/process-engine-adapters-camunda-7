package dev.bpmcrafters.processengineapi.test

import com.tngtech.jgiven.Stage
import com.tngtech.jgiven.annotation.ExpectedScenarioState
import com.tngtech.jgiven.annotation.ProvidedScenarioState
import dev.bpmcrafters.processengineapi.decision.DecisionByRefEvaluationCommand
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationResult
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionAtElementCmd
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd
import dev.bpmcrafters.processengineapi.task.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.toolisticon.testing.jgiven.JGivenKotlinStage
import io.toolisticon.testing.jgiven.step
import org.assertj.core.api.Assertions.assertThat

private val logger = KotlinLogging.logger {}

@JGivenKotlinStage
class BaseGivenWhenStage : Stage<BaseGivenWhenStage>() {

  @ExpectedScenarioState
  lateinit var processTestHelper: ProcessTestHelper

  @ProvidedScenarioState
  lateinit var instanceId: String

  @ProvidedScenarioState
  var userTaskId: String? = null

  @ProvidedScenarioState
  var externalTaskId: String? = null

  @ProvidedScenarioState
  lateinit var taskSubscription: TaskSubscription

  @ProvidedScenarioState
  var decisionResult: DecisionEvaluationResult? = null

  @ProvidedScenarioState
  var throwableCaught: Throwable? = null


  fun `start process by definition`(definitionKey: String) = step {
    instanceId = processTestHelper.getStartProcessApi().startProcess(
      StartProcessByDefinitionCmd(
        definitionKey = definitionKey,
        payloadSupplier = { emptyMap() }
      )
    ).get().instanceId
  }

  fun `start process by definition with payload`(definitionKey: String, singlePayload: Pair<String, Any>) = step {
    instanceId = processTestHelper.getStartProcessApi().startProcess(
      StartProcessByDefinitionCmd(
        definitionKey = definitionKey,
        payloadSupplier = { mapOf(singlePayload) }
      )
    ).get().instanceId
  }

  fun `start process by message`(messageName: String) = step {
    instanceId = processTestHelper.getStartProcessApi().startProcess(
      StartProcessByMessageCmd(
        messageName = messageName,
        payloadSupplier = { emptyMap() }
      )
    ).get().instanceId
  }

  fun `start process by message with payload`(messageName: String, singlePayload: Pair<String, Any>) = step {
    instanceId = processTestHelper.getStartProcessApi().startProcess(
      StartProcessByMessageCmd(
        messageName = messageName,
        payloadSupplier = { mapOf(singlePayload) }
      )
    ).get().instanceId
  }

  fun `start process by definition at element`(definitionKey: String, elementId: String) = step {
    instanceId = processTestHelper.getStartProcessApi().startProcess(
      StartProcessByDefinitionAtElementCmd(
        definitionKey = definitionKey,
        elementId = elementId,
        payloadSupplier = { emptyMap() },
      )
    ).get().instanceId
  }

  fun `a active user task subscription`(taskDescriptionKey: String) = step {
    taskSubscription = subscribeTask(TaskType.USER, taskDescriptionKey) { taskInformation, _ ->
      run {
        logger.info { "Got new task ${taskInformation.taskId}" }
        userTaskId = taskInformation.taskId
      }
    }
  }

  fun `subscribe for tasks`() = step {
    processTestHelper.subscribeForUserTasks()
  }

  fun `process helper`(processTestHelper: ProcessTestHelper) = step {
    this.processTestHelper = processTestHelper
  }


  fun `a active external task subscription`(taskDescriptionKey: String) = step {
    taskSubscription = subscribeTask(TaskType.EXTERNAL, taskDescriptionKey) { taskInformation, _ ->
      externalTaskId = taskInformation.taskId
    }
  }

  fun `complete the user task`() = step {
    assertThat(userTaskId).isNotEmpty()

    processTestHelper.getUserTaskCompletionApi().completeTask(
      CompleteTaskCmd(
        taskId = userTaskId!!,
        payloadSupplier = { emptyMap() }
      )
    ).get()
  }

  fun `complete the external task`() = step {
    assertThat(externalTaskId).isNotEmpty()

    processTestHelper.getServiceTaskCompletionApi().completeTask(
      CompleteTaskCmd(
        taskId = externalTaskId!!,
        payloadSupplier = { emptyMap() }
      )
    ).get()
  }

  fun `unsubscribe user task subscription`() = step { unsubscribeTask() }

  fun `unsubscribe external task subscription`() = step { unsubscribeTask() }

  private fun subscribeTask(taskType: TaskType, taskDescriptionKey: String, taskHandler: TaskHandler) = processTestHelper.getTaskSubscriptionApi().subscribeForTask(
    SubscribeForTaskCmd(
        restrictions = emptyMap(),
        taskType = taskType,
        taskDescriptionKey = taskDescriptionKey,
        action = taskHandler,
        termination = { this.externalTaskId = null } // wait until the termination delivers success
      )
    ).get()

  private fun unsubscribeTask() = processTestHelper.getTaskSubscriptionApi().unsubscribe(
    UnsubscribeFromTaskCmd(
      taskSubscription
    )
  ).get()

  fun `evaluate decision by ref key with payload`(decisionDefinitionId: String, payload: Map<String, Any>) = step {
    processTestHelper.getEvaluateDecisionApi().evaluateDecision(
      DecisionByRefEvaluationCommand(
        decisionRef = decisionDefinitionId,
        payload =  payload,
        restrictions = mapOf(),
      )
    ).handle { res, ex ->
      this.throwableCaught = ex
      this.decisionResult = res
    }.get()
  }


}
