package dev.bpmcrafters.processengineapi.adapter.c7.remote.decision

import dev.bpmcrafters.processengineapi.adapter.c7.common.serialization.AdapterDataConverter
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationOutput
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationResult
import org.camunda.bpm.engine.variable.VariableMap

data class DelegatingDmnDecisionResult(
  private val dmnDecisionResult: List<VariableMap>,
  private val dataConverter: AdapterDataConverter
) : DecisionEvaluationResult {
  override fun asSingle(): DecisionEvaluationOutput {
    return VariableMapDmnDecisionEvaluationOutput(dmnDecisionResult.single(), dataConverter)
  }

  override fun asList(): List<DecisionEvaluationOutput> {
    return dmnDecisionResult.filter { it.isNotEmpty() }.map { VariableMapDmnDecisionEvaluationOutput(it, dataConverter) }
  }

  override fun meta(): Map<String, String> = mapOf(
    "single-result" to if (dmnDecisionResult.size == 1) {
      "true"
    } else {
      "false"
    },
    "result-count" to "${dmnDecisionResult.size}"
  )
}
