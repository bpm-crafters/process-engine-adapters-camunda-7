package dev.bpmcrafters.processengineapi.adapter.c7.embedded.decision

import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationOutput
import org.camunda.bpm.dmn.engine.DmnDecisionResultEntries

/**
 * Delegating output.
 */
data class DelegatingDmnDecisionEvaluationOutput(
  val entries: DmnDecisionResultEntries,
) : DecisionEvaluationOutput {

  override fun <T : Any> asType(type: Class<T>): T? {
    return entries.getFirstEntry<T>()
  }

  override fun asMap(): Map<String, Any?>? {
    return entries.entryMap
  }
}

