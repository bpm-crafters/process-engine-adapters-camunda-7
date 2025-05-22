package dev.bpmcrafters.processengineapi.adapter.c7.remote.process

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.process.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.MessageApiClient
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.model.*
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

class StartProcessApiImpl(
  private val processDefinitionApiClient: ProcessDefinitionApiClient,
  private val messageApiClient: MessageApiClient,
  private val valueMapper: ValueMapper,
) : StartProcessApi {

  override fun startProcess(cmd: StartProcessCommand): Future<ProcessInformation> {
    return when (cmd) {
      is StartProcessByDefinitionCmd ->
        CompletableFuture.supplyAsync {
          logger.debug { "PROCESS-ENGINE-C7-REMOTE-004: starting a new process instance by definition ${cmd.definitionKey}." }
          val payload = cmd.payloadSupplier.get()
          val instance = processDefinitionApiClient.startProcessInstanceByKey(cmd.definitionKey, createStartProcessInstanceDto(payload))

          requireNotNull(instance.body) { "Could not start process instance ${cmd.definitionKey}, resulting status was ${instance.statusCode}" }.toProcessInformation()
        }

      is StartProcessByMessageCmd ->
        CompletableFuture.supplyAsync {
          logger.debug { "PROCESS-ENGINE-C7-REMOTE-005: starting a new process instance by message ${cmd.messageName}." }
          val payload = cmd.payloadSupplier.get()

          val messageCorrelation = messageApiClient.deliverMessage(
            CorrelationMessageDto()
              .messageName(cmd.messageName)
              .processVariables(valueMapper.mapValues(cmd.payloadSupplier.get()))
              .resultEnabled(true)
              .apply {
                if (payload.containsKey(CommonRestrictions.BUSINESS_KEY)) {
                  this.businessKey = payload[CommonRestrictions.BUSINESS_KEY].toString()
                }
              }
          )
          requireNotNull(messageCorrelation.body) { "Could not start process instance by message ${cmd.messageName}." }
          when (messageCorrelation.body?.size) {
            0 -> throw IllegalStateException("No result received")
            1 -> messageCorrelation.body?.get(0)?.toProcessInformation()
            else -> {
              logger.warn { "PROCESS-ENGINE-C7-REMOTE-005: multiple results received, returning the first one." }
              messageCorrelation.body?.get(0)?.toProcessInformation()
            }
          }
        }

      else -> throw IllegalArgumentException("Unsupported start command $cmd")
    }
  }

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO()
  }

  /**
   * Create process instance start DTO.
   */
  private fun createStartProcessInstanceDto(
    variables: Map<String, Any>? = null
  ) = StartProcessInstanceDto().apply {
    if (variables != null) {
      if (variables.containsKey(CommonRestrictions.BUSINESS_KEY)) {
        this.businessKey = variables.getValue(CommonRestrictions.BUSINESS_KEY).toString()
      }
      this.variables = valueMapper.mapValues(variables)
    }
  }

}

fun MessageCorrelationResultWithVariableDto.toProcessInformation() = ProcessInformation(
  instanceId = this.processInstance.id,
  meta = mapOf(
    CommonRestrictions.PROCESS_DEFINITION_KEY to this.processInstance.definitionKey,
    CommonRestrictions.BUSINESS_KEY to this.processInstance.businessKey,
    CommonRestrictions.TENANT_ID to this.processInstance.tenantId,
    CommonRestrictions.PROCESS_DEFINITION_ID to this.processInstance.definitionId,
  )
)

fun ProcessInstanceWithVariablesDto.toProcessInformation() = ProcessInformation(
  instanceId = this.id,
  meta = mapOf(
    CommonRestrictions.PROCESS_DEFINITION_KEY to this.definitionKey,
    CommonRestrictions.BUSINESS_KEY to this.businessKey,
    CommonRestrictions.TENANT_ID to this.tenantId,
    CommonRestrictions.PROCESS_DEFINITION_ID to this.definitionId,
  )
)

fun ProcessInstanceDto.toProcessInformation() = ProcessInformation(
  instanceId = this.id,
  meta = mapOf(
    CommonRestrictions.PROCESS_DEFINITION_KEY to this.definitionKey,
    CommonRestrictions.BUSINESS_KEY to this.businessKey,
    CommonRestrictions.TENANT_ID to this.tenantId,
    CommonRestrictions.PROCESS_DEFINITION_ID to this.definitionId,
  )
)

