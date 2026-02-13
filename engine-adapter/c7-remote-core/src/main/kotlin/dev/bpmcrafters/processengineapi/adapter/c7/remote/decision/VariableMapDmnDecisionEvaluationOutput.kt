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
    try {
      if (entries.keys.size == 1) {
        if (entries.values.first() == null) {
          return null
        }
        return objectMapper.convertValue(entries.values.first(), type)
      }
      return objectMapper.convertValue(entries, type)
    } catch (e: Exception) {
      throw IllegalStateException("Can't deserialize into ${type.name} decision output: ${asMap()}", e)
    }
  }

  override fun asMap(): Map<String, Any?> {
    return entries
  }
}
