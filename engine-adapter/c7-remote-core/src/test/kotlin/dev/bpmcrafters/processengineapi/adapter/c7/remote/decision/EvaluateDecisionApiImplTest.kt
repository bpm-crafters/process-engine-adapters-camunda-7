package dev.bpmcrafters.processengineapi.adapter.c7.remote.decision

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.TestFixtures
import dev.bpmcrafters.processengineapi.decision.DecisionByRefEvaluationCommand
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.DecisionDefinitionApiClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import java.util.concurrent.ExecutionException

internal class EvaluateDecisionApiImplTest {
  val valueMapper = TestFixtures.valueMapper()
  val decisionClient: DecisionDefinitionApiClient = mock()
  val testSubject = EvaluateDecisionApiImpl(decisionClient, valueMapper)

  @BeforeEach
  fun setUp() {
    reset(decisionClient)
  }

  @Test
  fun `should retrieve empty result`() {
    whenever(decisionClient.evaluateDecisionByKey(any(), any())).thenReturn(ResponseEntity.ok(listOf()))
    whenever(decisionClient.evaluateDecisionByKeyAndTenant(any(), any(), any())).thenReturn(ResponseEntity.ok(listOf()))

    val result = testSubject.evaluateDecision(
      DecisionByRefEvaluationCommand(
        decisionRef = "ref",
        payload = mapOf("foo" to "bar"),
        restrictions = mapOf(
          CommonRestrictions.TENANT_ID to "1"
        )
      )
    ).get()
    assertThat(result).isInstanceOf(EvaluateDecisionApiImpl.NoDecisionResult::class.java)
  }

  @Test
  fun `should not support with_tenant and without_tenant restrictions at the same time`() {
    whenever(decisionClient.evaluateDecisionByKey(any(), any())).thenReturn(ResponseEntity.ok(listOf()))

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
  fun `should return result for a tenant`() {

    whenever(decisionClient.evaluateDecisionByKeyAndTenant(any(), any(), any())).thenReturn(
      ResponseEntity.ok(
        listOf(
          valueMapper.mapValues(
            mapOf("key1" to "bar", "key2" to "baz")
          )
        )
      )
    )

    val result = testSubject.evaluateDecision(
      DecisionByRefEvaluationCommand(
        decisionRef = "ref",
        payload = mapOf("foo" to "bar"),
        restrictions = mapOf(
          CommonRestrictions.TENANT_ID to "1"
        )
      )
    ).get()
    assertThat(result).isInstanceOf(EvaluateDecisionApiImpl.DelegatingDmnDecisionResult::class.java)
    assertThat(result.meta().containsKey("result-count")).isTrue()
    assertThat(result.meta()["result-count"]).isEqualTo("1")
    assertThat(result.meta().containsKey("result-count")).isTrue()
    assertThat(result.meta()["result-count"]).isEqualTo("1")

    val out = result.asSingle().asMap()!!
    assertThat(out["key1"]).isEqualTo("bar")
    assertThat(out["key2"]).isEqualTo("baz")

  }
}
