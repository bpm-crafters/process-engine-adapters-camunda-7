package dev.bpmcrafters.processengineapi.adapter.c7.embedded.decision

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationOutput
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationResult
import org.camunda.bpm.dmn.engine.DmnDecisionResult

/**
 * Delegating result.
 */
data class DelegatingDmnDecisionResult(
  val dmnDecisionResult: DmnDecisionResult
) : DecisionEvaluationResult {

  private val objectMapper = createObjectMapper()

  override fun asSingle(): DecisionEvaluationOutput {
    return DelegatingDmnDecisionEvaluationOutput(objectMapper, dmnDecisionResult.singleResult)
  }

  override fun asList(): List<DecisionEvaluationOutput> {
    return dmnDecisionResult.map { DelegatingDmnDecisionEvaluationOutput(objectMapper, it) }
  }

  override fun meta(): Map<String, String> = mapOf(
    "single-result" to if (dmnDecisionResult.resultList.size == 1) {
      "true"
    } else {
      "false"
    },
    "result-count" to "${dmnDecisionResult.resultList.size}"
  )

  fun createObjectMapper(): ObjectMapper {
    val mapper = ObjectMapper()

    try {
      val kotlinModuleClass = Class.forName("com.fasterxml.jackson.module.kotlin.KotlinModule")
      val module = kotlinModuleClass.getDeclaredConstructor().newInstance() as Module
      mapper.registerModule(module)
    } catch (_: ClassNotFoundException) {
      // Kotlin module not present — continue without it
    }
    return mapper
  }
}
