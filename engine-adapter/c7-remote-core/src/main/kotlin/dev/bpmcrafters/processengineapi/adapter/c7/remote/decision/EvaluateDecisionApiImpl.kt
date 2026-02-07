package dev.bpmcrafters.processengineapi.adapter.c7.remote.decision

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.decision.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.DecisionDefinitionApiClient
import org.camunda.community.rest.client.model.EvaluateDecisionDto
import org.camunda.community.rest.client.model.VariableValueDto
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

class EvaluateDecisionApiImpl(
  private val decisionDefinitionApiClient: DecisionDefinitionApiClient,
  private val valueMapper: ValueMapper,
  private val objectMapper: ObjectMapper
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

          var variables = valueMapper.mapValues(command.payloadSupplier.get())
          val result = if (tenantId != null) {
            decisionDefinitionApiClient
              .evaluateDecisionByKeyAndTenant(
                command.decisionRef,
                tenantId,
                EvaluateDecisionDto().variables(variables)
              )
          } else {
            decisionDefinitionApiClient
              .evaluateDecisionByKey(
                command.decisionRef,
                EvaluateDecisionDto().variables(variables)
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
      DelegatingDmnDecisionResult(this.map { valueMapper.mapDtos(it) }, objectMapper)
    }
  }

  override fun getSupportedRestrictions(): Set<String> {
    return setOf(
      CommonRestrictions.TENANT_ID,
      CommonRestrictions.WITHOUT_TENANT_ID,
    )
  }

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }
}
