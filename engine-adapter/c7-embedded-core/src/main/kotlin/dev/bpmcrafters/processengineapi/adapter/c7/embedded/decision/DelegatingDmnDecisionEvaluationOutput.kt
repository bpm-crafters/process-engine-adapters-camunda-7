package dev.bpmcrafters.processengineapi.adapter.c7.embedded.decision

import dev.bpmcrafters.processengineapi.adapter.c7.common.serialization.AdapterDataConverter
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationOutput
import org.camunda.bpm.dmn.engine.DmnDecisionResultEntries

/**
 * Delegating output.
 */
data class DelegatingDmnDecisionEvaluationOutput(
  private val dataConverter: AdapterDataConverter,
  val entries: DmnDecisionResultEntries,
) : DecisionEvaluationOutput {

  override fun <T : Any> asType(type: Class<T>): T? {
    try {
      if (entries.isEmpty()) {
        return null
      } else if (entries.keys.size == 1) {
        return dataConverter.convert(entries.values.first(), type)
      }
      return dataConverter.convert(entries, type)
    } catch (e: Exception) {
      throw IllegalStateException("Can't deserialize into ${type.name} decision output: ${asMap()}", e)
    }
  }

  override fun asMap(): Map<String, Any?> {
    return entries.entryMap
  }
}
