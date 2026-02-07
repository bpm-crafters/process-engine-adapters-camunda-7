package dev.bpmcrafters.processengineapi.adapter.c7.remote.decision

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationOutput
import org.camunda.bpm.engine.variable.VariableMap

/**
 * Delegating output.
 */
data class VariableMapDmnDecisionEvaluationOutput(
  val entries: VariableMap,
  val objectMapper: ObjectMapper
) : DecisionEvaluationOutput {

  override fun <T : Any> asType(type: Class<T>): T? {
    return objectMapper.convertValue(entries, type)
  }

  override fun asMap(): Map<String, Any?>? {
    return entries
  }
}
