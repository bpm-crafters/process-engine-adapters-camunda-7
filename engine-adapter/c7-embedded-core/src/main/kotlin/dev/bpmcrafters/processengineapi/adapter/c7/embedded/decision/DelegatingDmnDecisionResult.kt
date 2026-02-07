package dev.bpmcrafters.processengineapi.adapter.c7.embedded.decision

import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationOutput
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationResult
import org.camunda.bpm.dmn.engine.DmnDecisionResult

/**
 * Delegating result.
 */
data class DelegatingDmnDecisionResult(
  val dmnDecisionResult: DmnDecisionResult
) : DecisionEvaluationResult {
  override fun asSingle(): DecisionEvaluationOutput {
    return DelegatingDmnDecisionEvaluationOutput(dmnDecisionResult.singleResult)
  }

  override fun asList(): List<DecisionEvaluationOutput> {
    return dmnDecisionResult.map { DelegatingDmnDecisionEvaluationOutput(it) }
  }

  override fun meta(): Map<String, String> = mapOf(
    "single-result" to if (dmnDecisionResult.resultList.size == 1) {
      "true"
    } else {
      "false"
    },
    "result-count" to "${dmnDecisionResult.resultList.size}"
  )
}
