package dev.bpmcrafters.processengineapi.adapter.c7.remote.process

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.adapter.c7.remote.correlation.applyRestrictions
import dev.bpmcrafters.processengineapi.process.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.MessageApiClient
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.model.*
import org.camunda.community.rest.variables.ValueMapper
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

class StartProcessApiImpl(
  private val processDefinitionApiClient: ProcessDefinitionApiClient,
  private val messageApiClient: MessageApiClient,
  private val processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  private val valueMapper: ValueMapper,
) : StartProcessApi {

  override fun startProcess(cmd: StartProcessCommand): CompletableFuture<ProcessInformation> {
    return when (cmd) {
      is StartProcessByDefinitionCmd ->
        CompletableFuture.supplyAsync {
          logger.debug { "PROCESS-ENGINE-C7-REMOTE-004: starting a new process instance by definition ${cmd.definitionKey}." }
          ensureSupported(cmd.restrictions)
          val payload = cmd.payloadSupplier.get()
          val tenantId = cmd.restrictions[CommonRestrictions.TENANT_ID]
          val processDefinitionId = getProcessDefinitionId(cmd.definitionKey, tenantId)

          val instance = processDefinitionApiClient.startProcessInstance(
            processDefinitionId,
            StartProcessInstanceDto()
              .apply {
                if (payload.containsKey(CommonRestrictions.BUSINESS_KEY)) {
                  this.businessKey = payload.getValue(CommonRestrictions.BUSINESS_KEY).toString()
                }
                this.variables = valueMapper.mapValues(payload)
              }
          )

          requireNotNull(instance.body) { "Could not start process instance ${cmd.definitionKey}, resulting status was ${instance.statusCode}" }.toProcessInformation()
        }

      is StartProcessByMessageCmd ->
        CompletableFuture.supplyAsync {
          logger.debug { "PROCESS-ENGINE-C7-REMOTE-005: starting a new process instance by message ${cmd.messageName}." }
          ensureSupported(cmd.restrictions)
          val payload = cmd.payloadSupplier.get()
          val messageCorrelation = messageApiClient.deliverMessage(
            CorrelationMessageDto()
              .messageName(cmd.messageName)
              .processVariables(valueMapper.mapValues(cmd.payloadSupplier.get()))
              .resultEnabled(true)
              .applyRestrictions(
                ensureSupported(cmd.restrictions)
              )
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

      is StartProcessByDefinitionAtElementCmd ->
        CompletableFuture.supplyAsync {
          logger.debug { "PROCESS-ENGINE-C7-REMOTE-006: starting a new process instance by definition ${cmd.definitionKey} at element ${cmd.elementId}" }
          val payload = cmd.payloadSupplier.get()
          val tenantId = cmd.restrictions[CommonRestrictions.TENANT_ID]
          val processDefinitionId = getProcessDefinitionId(cmd.definitionKey, tenantId)

          val startInstructionDto = ProcessInstanceModificationInstructionDto()
          startInstructionDto.type = ProcessInstanceModificationInstructionDto.TypeEnum.START_BEFORE_ACTIVITY
          startInstructionDto.activityId = cmd.elementId

          val startProcessInstanceDto = StartProcessInstanceDto()
          startProcessInstanceDto.businessKey = payload[CommonRestrictions.BUSINESS_KEY]?.toString()
          startProcessInstanceDto.variables = valueMapper.mapValues(payload)
          startProcessInstanceDto.startInstructions(listOf(startInstructionDto))

          val instance = processDefinitionApiClient.startProcessInstance(processDefinitionId, startProcessInstanceDto)
          val processInformation = instance.body?.toProcessInformation()
          requireNotNull(processInformation) { "Could not start process instance ${cmd.definitionKey}, resulting status was ${instance.statusCode}" }
        }

      else -> throw IllegalArgumentException("Unsupported start command $cmd")
    }
  }

  override fun getSupportedRestrictions(): Set<String> = setOf(
    CommonRestrictions.TENANT_ID
  )

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO()
  }

  private fun getProcessDefinitionId(processKey: String, tenantId: String?): String {
    val definitionId = processDefinitionMetaDataResolver.getProcessDefinitionId(processDefinitionKey = processKey, tenantId = tenantId)
    return requireNotNull(definitionId) { "Could not find process definition id for key $processKey and tenant $tenantId." }
  }

}

fun MessageCorrelationResultWithVariableDto.toProcessInformation() = ProcessInformation(
  instanceId = this.processInstance!!.id!!,
  meta = mapOf(
    CommonRestrictions.PROCESS_DEFINITION_KEY to this.processInstance.definitionKey,
    CommonRestrictions.BUSINESS_KEY to this.processInstance.businessKey,
    CommonRestrictions.TENANT_ID to this.processInstance.tenantId,
    CommonRestrictions.PROCESS_DEFINITION_ID to this.processInstance.definitionId,
  )
)

fun ProcessInstanceWithVariablesDto.toProcessInformation() = ProcessInformation(
  instanceId = this.id!!,
  meta = mapOf(
    CommonRestrictions.PROCESS_DEFINITION_KEY to this.definitionKey,
    CommonRestrictions.BUSINESS_KEY to this.businessKey,
    CommonRestrictions.TENANT_ID to this.tenantId,
    CommonRestrictions.PROCESS_DEFINITION_ID to this.definitionId,
  )
)

fun ProcessInstanceDto.toProcessInformation() = ProcessInformation(
  instanceId = this.id!!,
  meta = mapOf(
    CommonRestrictions.PROCESS_DEFINITION_KEY to this.definitionKey,
    CommonRestrictions.BUSINESS_KEY to this.businessKey,
    CommonRestrictions.TENANT_ID to this.tenantId,
    CommonRestrictions.PROCESS_DEFINITION_ID to this.definitionId,
  )
)

