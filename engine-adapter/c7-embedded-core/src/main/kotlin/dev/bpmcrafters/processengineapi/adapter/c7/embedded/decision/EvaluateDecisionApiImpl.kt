package dev.bpmcrafters.processengineapi.adapter.c7.embedded.decision

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.decision.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.dmn.engine.DmnDecisionResult
import org.camunda.bpm.dmn.engine.DmnDecisionResultEntries
import org.camunda.bpm.engine.DecisionService
import org.camunda.bpm.engine.dmn.DecisionsEvaluationBuilder
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

class EvaluateDecisionApiImpl(
  private val decisionService: DecisionService
) : EvaluateDecisionApi {

  override fun evaluateDecision(command: DecisionEvaluationCommand): CompletableFuture<DecisionEvaluationResult> {
    return when (command) {
      is DecisionByRefEvaluationCommand -> {
        logger.debug {
          "PROCESS-ENGINE-C7-EMBEDDED-061: Evaluating decision by reference ${command.decisionRef}"
        }
        CompletableFuture.supplyAsync {
          val result = decisionService
            .evaluateDecisionByKey(command.decisionRef)
            .applyRestrictions(ensureSupported(command.restrictionSupplier.get()))
            .variables(command.payloadSupplier.get())
            .evaluate()
          if (result.size == 0) {
            NoDecisionResult
          } else {
            DelegatingDmnDecisionResult(result)
          }
        }
      }

      else -> throw UnsupportedOperationException("Evaluate Decision command of type ${command::class.qualifiedName} is not implemented yet")
    }
  }

  override fun getSupportedRestrictions(): Set<String> = setOf(
    CommonRestrictions.TENANT_ID,
    CommonRestrictions.WITHOUT_TENANT_ID,
  )

  fun DecisionsEvaluationBuilder.applyRestrictions(restrictions: Map<String, String>): DecisionsEvaluationBuilder = this.apply {
    restrictions
      .forEach { (key, value) ->
        when (key) {
          CommonRestrictions.TENANT_ID -> this.decisionDefinitionTenantId(value).apply {
            require(!restrictions.containsKey(CommonRestrictions.WITHOUT_TENANT_ID)) {
              "Illegal restriction combination. ${CommonRestrictions.TENANT_ID} " +
                "and ${CommonRestrictions.WITHOUT_TENANT_ID} can't be provided in the same time because they are mutually exclusive."
            }
          }

          CommonRestrictions.WITHOUT_TENANT_ID -> this.decisionDefinitionWithoutTenantId().apply {
            require(!restrictions.containsKey(CommonRestrictions.TENANT_ID)) {
              "Illegal restriction combination. ${CommonRestrictions.TENANT_ID} " +
                "and ${CommonRestrictions.WITHOUT_TENANT_ID} can't be provided in the same time because they are mutually exclusive."
            }
          }
        }
      }
  }

  /**
   * Delegating result.
   */
  data class DelegatingDmnDecisionResult(
    val dmnDecisionResult: DmnDecisionResult
  ) : DecisionEvaluationResult {
    override fun asSingle(): DecisionEvaluationOutput {
      return DelegatingDmnDecisionEvaluationOutput(dmnDecisionResult.singleResult)
    }

    override fun asList(): List<DecisionEvaluationOutput> {
      return dmnDecisionResult.map { DelegatingDmnDecisionEvaluationOutput(it) }
    }

    override fun meta(): Map<String, String> = mapOf(
      "single-result" to if (dmnDecisionResult.resultList.size == 1) {
        "true"
      } else {
        "false"
      },
      "result-count" to "${dmnDecisionResult.resultList.size}"
    )
  }

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


