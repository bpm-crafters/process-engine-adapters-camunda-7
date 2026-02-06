package dev.bpmcrafters.processengineapi.adapter.c7.remote.decision

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.decision.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.variable.VariableMap
import org.camunda.community.rest.client.api.DecisionDefinitionApiClient
import org.camunda.community.rest.client.model.EvaluateDecisionDto
import org.camunda.community.rest.client.model.VariableValueDto
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

class EvaluateDecisionApiImpl(
  private val decisionDefinitionApiClient: DecisionDefinitionApiClient,
  private val valueMapper: ValueMapper,
) : EvaluateDecisionApi {

  override fun evaluateDecision(command: DecisionEvaluationCommand): CompletableFuture<DecisionEvaluationResult> {
    when (command) {
      is DecisionByRefEvaluationCommand -> {
        logger.debug {
          "PROCESS-ENGINE-C7-REMOTE-061: Evaluating decision by reference ${command.decisionRef}"
        }
        return CompletableFuture.supplyAsync {

          val restrictions = command.restrictionSupplier.get()

          val tenantId = if (restrictions.containsKey(CommonRestrictions.TENANT_ID)) {
            require(!restrictions.containsKey(CommonRestrictions.WITHOUT_TENANT_ID)) {
              "Illegal restriction combination. ${CommonRestrictions.TENANT_ID} " +
                "and ${CommonRestrictions.WITHOUT_TENANT_ID} can't be provided in the same time because they are mutually exclusive."
            }
            restrictions[CommonRestrictions.TENANT_ID]
          } else if (restrictions.containsKey(CommonRestrictions.WITHOUT_TENANT_ID)) {
            require(!restrictions.containsKey(CommonRestrictions.TENANT_ID)) {
              "Illegal restriction combination. ${CommonRestrictions.TENANT_ID} " +
                "and ${CommonRestrictions.WITHOUT_TENANT_ID} can't be provided in the same time because they are mutually exclusive."
            }
            null
          } else {
            null
          }

          val result = if (tenantId != null) {
            decisionDefinitionApiClient
              .evaluateDecisionByKeyAndTenant(
                command.decisionRef,
                tenantId,
                EvaluateDecisionDto().variables(valueMapper.mapValues(command.payloadSupplier.get()))
              )
          } else {
            decisionDefinitionApiClient
              .evaluateDecisionByKey(
                command.decisionRef,
                EvaluateDecisionDto().variables(valueMapper.mapValues(command.payloadSupplier.get()))
              )
          }
          requireNotNull(result.body) { "Could not evaluate decision ${command.decisionRef}, resulting status was ${result.statusCode}" }.toResult()
        }
      }

      else -> throw UnsupportedOperationException("Evaluate Decision command of type ${command::class.qualifiedName} is not implemented yet")
    }

  }

  private fun List<Map<String, VariableValueDto>>.toResult(): DecisionEvaluationResult {
    return if (this.isEmpty()) {
      NoDecisionResult
    } else {
      DelegatingDmnDecisionResult(this.map { valueMapper.mapDtos(it) })
    }
  }

  override fun getSupportedRestrictions(): Set<String> {
    return setOf(
      CommonRestrictions.TENANT_ID,
      CommonRestrictions.WITHOUT_TENANT_ID,
    )
  }

  data class DelegatingDmnDecisionResult(
    val dmnDecisionResult: List<VariableMap>
  ) : DecisionEvaluationResult {
    override fun asSingle(): DecisionEvaluationOutput {
      return VariableMapDmnDecisionEvaluationOutput(dmnDecisionResult.single())
    }

    override fun asList(): List<DecisionEvaluationOutput> {
      return dmnDecisionResult.map { VariableMapDmnDecisionEvaluationOutput(it) }
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


  /**
   * Delegating output.
   */
  data class VariableMapDmnDecisionEvaluationOutput(
    val entries: VariableMap,
  ) : DecisionEvaluationOutput {

    override fun <T : Any> asType(type: Class<T>): T? {
      return entries.get("") as T?
    }

    override fun asMap(): Map<String, Any?>? {
      return entries
    }
  }

  /**
   * No output.
   */
  object NoDecisionResult : DecisionEvaluationResult {
    override fun asSingle(): DecisionEvaluationOutput = throw IllegalStateException("No decision result")

    override fun asList(): List<DecisionEvaluationOutput> = listOf()

    override fun meta(): Map<String, String> = mapOf("single-result" to "false", "result-count" to "0")

  }

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }
}
