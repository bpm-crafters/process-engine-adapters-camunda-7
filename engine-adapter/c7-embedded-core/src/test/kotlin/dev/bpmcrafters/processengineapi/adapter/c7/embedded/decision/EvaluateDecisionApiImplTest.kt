package dev.bpmcrafters.processengineapi.adapter.c7.embedded.decision

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.decision.DecisionByRefEvaluationCommand
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.dmn.engine.impl.DmnDecisionResultEntriesImpl
import org.camunda.bpm.dmn.engine.impl.DmnDecisionResultImpl
import org.camunda.bpm.engine.DecisionService
import org.camunda.bpm.engine.dmn.DecisionsEvaluationBuilder
import org.camunda.bpm.engine.variable.Variables
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Answers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutionException

internal class EvaluateDecisionApiImplTest {
  val decisionService: DecisionService = mock()
  val fluentBuilder: DecisionsEvaluationBuilder = mock(DecisionsEvaluationBuilder::class.java, Answers.RETURNS_DEEP_STUBS)
  val testSubject = EvaluateDecisionApiImpl(decisionService, jacksonObjectMapper(), EngineCommandExecutor())

  @Test
  fun `should retrieve empty result`() {
    whenever(decisionService.evaluateDecisionByKey(any())).thenReturn(fluentBuilder)
    whenever(fluentBuilder.evaluate()).thenReturn(DmnDecisionResultImpl(listOf()))

    val result = testSubject.evaluateDecision(
      DecisionByRefEvaluationCommand(
        decisionRef = "ref",
        payload = mapOf("foo" to "bar"),
        restrictions = mapOf(
          CommonRestrictions.TENANT_ID to "1"
        )
      )
    ).get()
    assertThat(result).isInstanceOf(NoDecisionResult::class.java)
  }

  @Test
  fun `should not support with_tenant and without_tenant restrictions at the same time`() {
    whenever(decisionService.evaluateDecisionByKey(any())).thenReturn(fluentBuilder)
    whenever(fluentBuilder.evaluate()).thenReturn(DmnDecisionResultImpl(listOf()))

    val exc = assertThrows<ExecutionException> {
      testSubject.evaluateDecision(
        DecisionByRefEvaluationCommand(
          decisionRef = "ref",
          payload = mapOf("foo" to "bar"),
          restrictions = mapOf(
            CommonRestrictions.TENANT_ID to "1",
            CommonRestrictions.WITHOUT_TENANT_ID to "2",
          )
        )
      ).get()
    }
    assertThat(exc.cause).isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `should return result`() {
    val builder: DecisionsEvaluationBuilder = mock()
    whenever(decisionService.evaluateDecisionByKey(any())).thenReturn(builder)
    whenever(builder.decisionDefinitionWithoutTenantId()).thenReturn(builder)
    whenever(builder.decisionDefinitionTenantId(any())).thenReturn(builder)
    whenever(builder.variables(any())).thenReturn(builder)
    whenever(builder.evaluate()).thenReturn(
      DmnDecisionResultImpl(
        listOf(
      DmnDecisionResultEntriesImpl().apply {
        putValue("key1", Variables.stringValue("bar"))
        putValue("key2", Variables.stringValue("baz"))
      }
    )))
    val result = testSubject.evaluateDecision(
      DecisionByRefEvaluationCommand(
        decisionRef = "ref",
        payload = mapOf("foo" to "bar"),
        restrictions = mapOf(
          CommonRestrictions.TENANT_ID to "1"
        )
      )
    ).get()
    assertThat(result).isInstanceOf(DelegatingDmnDecisionResult::class.java)
    assertThat(result.meta().containsKey("result-count")).isTrue()
    assertThat(result.meta()["result-count"]).isEqualTo("1")
    assertThat(result.meta().containsKey("result-count")).isTrue()
    assertThat(result.meta()["result-count"]).isEqualTo("1")

    val out = result.asSingle().asMap()!!
    assertThat(out["key1"]).isEqualTo("bar")
    assertThat(out["key2"]).isEqualTo("baz")

  }
}
