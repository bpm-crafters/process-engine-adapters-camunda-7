package dev.bpmcrafters.processengineapi.adapter.c7.embedded.decision

import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationOutput
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationResult

/**
 * No output.
 */
object NoDecisionResult : DecisionEvaluationResult {
  override fun asSingle(): DecisionEvaluationOutput = throw IllegalStateException("No decision result")

  override fun asList(): List<DecisionEvaluationOutput> = listOf()

  override fun meta(): Map<String, String> = mapOf("single-result" to "false", "result-count" to "0")

}
