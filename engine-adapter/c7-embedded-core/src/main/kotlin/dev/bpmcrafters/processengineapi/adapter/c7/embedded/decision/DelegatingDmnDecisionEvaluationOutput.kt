package dev.bpmcrafters.processengineapi.adapter.c7.embedded.decision

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationOutput
import org.camunda.bpm.dmn.engine.DmnDecisionResultEntries

/**
 * Delegating output.
 */
data class DelegatingDmnDecisionEvaluationOutput(
  private val objectMapper: ObjectMapper,
  val entries: DmnDecisionResultEntries,
) : DecisionEvaluationOutput {

  override fun <T : Any> asType(type: Class<T>): T? {
    try {
      if (entries.isEmpty()) {
        return null
      } else if (entries.keys.size == 1) {
        return objectMapper.convertValue(entries.values.first(), type)
      }
      return objectMapper.convertValue(entries, type)
    } catch (e: Exception) {
      throw IllegalStateException("Can't deserialize into ${type.name} decision output: ${asMap()}", e)
    }
  }

  override fun asMap(): Map<String, Any?> {
    return entries.entryMap
  }
}

