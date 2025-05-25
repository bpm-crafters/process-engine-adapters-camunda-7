package dev.bpmcrafters.processengineapi.adapter.c7.remote.correlation

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.MessageApiClient
import org.camunda.community.rest.client.model.CorrelationMessageDto
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

class CorrelationApiImpl(
  private val messageApiClient: MessageApiClient,
  private val valueMapper: ValueMapper,
) : CorrelationApi {

  override fun correlateMessage(cmd: CorrelateMessageCmd): Future<Empty> {
    return CompletableFuture.supplyAsync {
      val correlation = cmd.correlation.get()
      logger.debug { "PROCESS-ENGINE-C7-REMOTE-001: Correlating message ${cmd.messageName} using local variable ${correlation.correlationVariable} with value ${correlation.correlationKey}" }
      val payload = cmd.payloadSupplier.get()
      val correlationValue = requireNotNull(payload[correlation.correlationVariable]) { "Correlation variable ${correlation.correlationVariable} is missing" }
      val messageCorrelation = messageApiClient.deliverMessage(
        CorrelationMessageDto()
          .messageName(cmd.messageName)
          .localCorrelationKeys(valueMapper.mapValues(mapOf(correlation.correlationKey to correlationValue)))
          .processVariables(valueMapper.mapValues(payload))
          .resultEnabled(true)
          .applyRestrictions(
            ensureSupported(cmd.restrictions)
          )
      )
      requireNotNull(messageCorrelation.body) { "Could not correlate message ${cmd.messageName}" }
      Empty
    }
  }

  override fun getSupportedRestrictions(): Set<String> = setOf(
    CommonRestrictions.TENANT_ID,
    CommonRestrictions.WITHOUT_TENANT_ID
  )

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }

}
