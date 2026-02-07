package dev.bpmcrafters.processengineapi.test

import com.tngtech.jgiven.Stage
import com.tngtech.jgiven.annotation.As
import com.tngtech.jgiven.annotation.ExpectedScenarioState
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationResult
import io.toolisticon.testing.jgiven.JGivenKotlinStage
import io.toolisticon.testing.jgiven.step
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import java.util.function.Function
import kotlin.reflect.KClass

@JGivenKotlinStage
class BaseThenStage : Stage<BaseThenStage>() {

  @ExpectedScenarioState
  lateinit var instanceId: String

  @ExpectedScenarioState
  var userTaskId: String? = null

  @ExpectedScenarioState
  var externalTaskId: String? = null

  @ExpectedScenarioState
  lateinit var processTestHelper: ProcessTestHelper

  @ExpectedScenarioState
  lateinit var decisionResult: DecisionEvaluationResult

  @ExpectedScenarioState
  var throwableCaught: Throwable? = null

  @ExpectedScenarioState
  var interpretedDecisionResult: Any? = null

  fun `we should have a running process`() = step {
    val process = processTestHelper.getProcessInformation(instanceId)
    assertThat(process).isNotNull()
  }

  fun `we should get notified about a new user task with pull strategy`() = step {
    processTestHelper.triggerPullingUserTaskDeliveryManually()

    await().untilAsserted { assertThat(userTaskId).isNotEmpty() }
  }

  fun `we should get notified about a new user task with subscribing strategy`() = step {
    await().untilAsserted { assertThat(userTaskId).isNotEmpty() }
  }

  fun `we should not get notified about a new user task with pull strategy`() = step {
    processTestHelper.triggerPullingUserTaskDeliveryManually()

    await().untilAsserted { assertThat(userTaskId).isNull() }
  }

  fun `we should not get notified about a new user task with subscribing strategy`() = step {
    await().untilAsserted { assertThat(userTaskId).isNull() }
  }

  fun `we should get notified about a new external task`() = step {
    processTestHelper.triggerExternalTaskDeliveryManually()

    await().untilAsserted { assertThat(externalTaskId).isNotEmpty() }
  }

  fun `we should not get notified about a new external task`() = step {
    processTestHelper.triggerExternalTaskDeliveryManually()

    await().untilAsserted { assertThat(externalTaskId).isNull() }
  }

  fun `we should have thrown`(clazz: KClass<out Throwable>) = step {
    assertThat(throwableCaught).describedAs { "no exception found, but result was " + this.decisionResult }.isNotNull
    assertThat(throwableCaught).isInstanceOf(clazz.java)
  }

  fun `evaluation result interpreted as `(interpretation: Function<DecisionEvaluationResult, Any?>) = step {
    try {
      this.interpretedDecisionResult = interpretation.apply(this.decisionResult)
    } catch (e: Exception) {
      this.throwableCaught = e
    }
  }

  @As("should fail interpretation with $")
  fun `should interpretation fail`(clazz: KClass<out Throwable>) = `we should have thrown`(clazz)

  fun `interpreted result is`(expectedResult: Any?)  = step {
    assertThat(this.throwableCaught).isNull()
    assertThat(this.interpretedDecisionResult).isEqualTo(expectedResult)
  }


}
