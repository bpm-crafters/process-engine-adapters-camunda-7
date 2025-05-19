package dev.bpmcrafters.processengineapi.adapter.c7.remote.correlation

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.MessageApiClient
import org.camunda.community.rest.client.model.CorrelationMessageDto
import org.camunda.community.rest.client.model.VariableValueDto
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

class CorrelationApiImpl(
  private val messageApiClient: MessageApiClient
) : CorrelationApi {

  override fun correlateMessage(cmd: CorrelateMessageCmd): Future<Empty> {
    return CompletableFuture.supplyAsync {
      val correlation = cmd.correlation.get()
      logger.debug { "PROCESS-ENGINE-C7-REMOTE-001: Correlating message ${cmd.messageName} using local variable ${correlation.correlationVariable} with value ${correlation.correlationKey}" }
      messageApiClient.deliverMessage(
        CorrelationMessageDto()
          .messageName(cmd.messageName)
          .localCorrelationKeys(mapOf(correlation.correlationVariable to VariableValueDto().value(correlation.correlationKey)))
          .processVariables(cmd.payloadSupplier.get().mapValues { VariableValueDto().value(it.value) })
          .all(true)
          .resultEnabled(true)
      )
      Empty
    }
  }

  override fun getSupportedRestrictions(): Set<String> = setOf()

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }

}
