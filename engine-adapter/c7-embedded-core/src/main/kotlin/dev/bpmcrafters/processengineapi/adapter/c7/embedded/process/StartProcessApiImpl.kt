package dev.bpmcrafters.processengineapi.adapter.c7.embedded.process

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.process.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.runtime.ProcessInstance
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

/**
 * Implementation of a start proces sapi using runtime service.
 */
class StartProcessApiImpl(
  private val runtimeService: RuntimeService,
  private val repositoryService: RepositoryService,
) : StartProcessApi {

  override fun startProcess(cmd: StartProcessCommand): Future<ProcessInformation> {
    return when (cmd) {
      is StartProcessByDefinitionCmd ->
        CompletableFuture.supplyAsync {
          logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-004: starting a new process instance by definition ${cmd.definitionKey}." }
          val payload = cmd.payloadSupplier.get()
          val tenantId = cmd.restrictions[CommonRestrictions.TENANT_ID]
          if (!tenantId.isNullOrBlank()) {
            repositoryService
              .createProcessDefinitionQuery()
              .processDefinitionKey(cmd.definitionKey)
              .tenantIdIn(tenantId)
              .active()
              .latestVersion()
              .singleResult()?.let { processDefinition ->
                runtimeService.startProcessInstanceById(
                  processDefinition.id,
                  payload[CommonRestrictions.BUSINESS_KEY]?.toString(),
                  payload,
                ).toProcessInformation()
              }
          } else {
            runtimeService.startProcessInstanceByKey(
              cmd.definitionKey,
              payload[CommonRestrictions.BUSINESS_KEY]?.toString(),
              payload,
            ).toProcessInformation()
          }
        }

      is StartProcessByMessageCmd ->
        CompletableFuture.supplyAsync {
          logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-005: starting a new process instance by message ${cmd.messageName}." }
          val payload = cmd.payloadSupplier.get()
          var correlationBuilder = runtimeService
            .createMessageCorrelation(cmd.messageName)
          if (payload[CommonRestrictions.BUSINESS_KEY] != null) {
            correlationBuilder = correlationBuilder.processInstanceBusinessKey(payload[CommonRestrictions.BUSINESS_KEY]?.toString())
          }
          if (cmd.restrictions.containsKey(CommonRestrictions.TENANT_ID)) {
            correlationBuilder.tenantId(cmd.restrictions[CommonRestrictions.TENANT_ID])
          } else if (cmd.restrictions.containsKey(CommonRestrictions.WITHOUT_TENANT_ID)) {
            correlationBuilder.withoutTenantId()
          }
          correlationBuilder
            .setVariables(payload)
            .correlateStartMessage()
            .toProcessInformation()
        }

      else -> throw IllegalArgumentException("Unsupported start command $cmd")
    }
  }

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO()
  }

  override fun getSupportedRestrictions(): Set<String> = setOf(
    CommonRestrictions.TENANT_ID
  )
}

fun ProcessInstance.toProcessInformation() = ProcessInformation(
  instanceId = this.id,
  meta = mapOf(
    CommonRestrictions.PROCESS_DEFINITION_KEY to this.processDefinitionId,
    CommonRestrictions.BUSINESS_KEY to this.businessKey,
    CommonRestrictions.TENANT_ID to this.tenantId,
    "rootProcessInstanceId" to this.rootProcessInstanceId
  )
)
