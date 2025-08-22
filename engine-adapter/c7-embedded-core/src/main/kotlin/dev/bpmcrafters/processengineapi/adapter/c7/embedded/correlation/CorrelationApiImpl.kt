package dev.bpmcrafters.processengineapi.adapter.c7.embedded.correlation

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.correlation.CorrelationApiImpl.Restrictions.USE_GLOBAL_CORRELATION_KEY
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.RuntimeService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

/**
 * Implementation of message correlation API using runtime service.
 */
class CorrelationApiImpl(
  private val runtimeService: RuntimeService,
) : CorrelationApi {

  object Restrictions {
    const val USE_GLOBAL_CORRELATION_KEY = "useGlobalCorrelationKey"
  }

  override fun correlateMessage(cmd: CorrelateMessageCmd): Future<Empty> {
    return CompletableFuture.supplyAsync {
      val correlation = cmd.correlation.get()
      val globalCorrelation = cmd.restrictions.containsKey(USE_GLOBAL_CORRELATION_KEY)
        && cmd.restrictions.getValue(USE_GLOBAL_CORRELATION_KEY).toBoolean()
      logger.debug {
        "PROCESS-ENGINE-C7-EMBEDDED-001: Correlating message ${cmd.messageName} using ${
          if (globalCorrelation) {
            "global"
          } else {
            "local"
          }
        } variable ${correlation.correlationVariable} with value ${correlation.correlationKey}"
      }
      runtimeService
        .createMessageCorrelation(cmd.messageName)
        .setVariables(cmd.payloadSupplier.get())
        .applyTenantRestrictions(ensureSupported(cmd.restrictions))
        .let {
          if (globalCorrelation) {
            it.processInstanceVariableEquals(correlation.correlationVariable, correlation.correlationKey)
          } else {
            it.localVariableEquals(correlation.correlationVariable, correlation.correlationKey)
          }
        }
        .correlateWithResult()
      Empty
    }
  }

  override fun getSupportedRestrictions(): Set<String> = setOf(
    CommonRestrictions.TENANT_ID,
    CommonRestrictions.WITHOUT_TENANT_ID,
    USE_GLOBAL_CORRELATION_KEY,
  )

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }

}
