package dev.bpmcrafters.processengineapi.adapter.c7.embedded.process

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.correlation.applyTenantRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.metaOf
import dev.bpmcrafters.processengineapi.process.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.runtime.ProcessInstance
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

/**
 * Implementation of a start proces sapi using runtime service.
 */
class StartProcessApiImpl(
  private val runtimeService: RuntimeService,
  private val repositoryService: RepositoryService,
  private val commandExecutor: EngineCommandExecutor,
) : StartProcessApi {

  override fun startProcess(cmd: StartProcessCommand): CompletableFuture<ProcessInformation> {
    return when (cmd) {
      is StartProcessByDefinitionCmd ->
        commandExecutor.execute {
          logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-004: starting a new process instance by definition ${cmd.definitionKey}." }
          ensureSupported(cmd.restrictions)
          val payload = cmd.payloadSupplier.get()
          val tenantId = cmd.restrictions[CommonRestrictions.TENANT_ID]
          if (!tenantId.isNullOrBlank()) {
            val processDefinition = requireNotNull(
              repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(cmd.definitionKey)
                .tenantIdIn(tenantId)
                .active()
                .latestVersion()
                .singleResult()
            )
            runtimeService.startProcessInstanceById(
              processDefinition.id,
              payload[CommonRestrictions.BUSINESS_KEY]?.toString(),
              payload,
            ).toProcessInformation()
          } else {
            runtimeService.startProcessInstanceByKey(
              cmd.definitionKey,
              payload[CommonRestrictions.BUSINESS_KEY]?.toString(),
              payload,
            ).toProcessInformation()
          }
        }

      is StartProcessByMessageCmd ->
        commandExecutor.execute {
          logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-005: starting a new process instance by message ${cmd.messageName}." }
          val payload = cmd.payloadSupplier.get()
          var correlationBuilder = runtimeService
            .createMessageCorrelation(cmd.messageName)
          payload[CommonRestrictions.BUSINESS_KEY]?.apply {
            correlationBuilder = correlationBuilder.processInstanceBusinessKey(payload[CommonRestrictions.BUSINESS_KEY]?.toString())
          }
          correlationBuilder
            .applyTenantRestrictions(ensureSupported(cmd.restrictions))
            .setVariables(payload)
            .correlateStartMessage()
            .toProcessInformation()
        }

      is StartProcessByDefinitionAtElementCmd ->
        commandExecutor.execute {
          logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-006: starting a new process instance by definition ${cmd.definitionKey} at element ${cmd.elementId}" }
          val startProcessCommand = StartProcessByDefinitionCmd(
            definitionKey = cmd.definitionKey,
            payloadSupplier = cmd.payloadSupplier,
            restrictions = cmd.restrictions,
          )
          val instance = this.startProcess(startProcessCommand).get()
          val processDefinitionId = instance.meta[CommonRestrictions.PROCESS_DEFINITION_KEY] as String
          runtimeService.createModification(processDefinitionId)
            .processInstanceIds(instance.instanceId)
            .startBeforeActivity(cmd.elementId)
            .execute()
          instance
        }

      else -> throw IllegalArgumentException("Unsupported start command $cmd")
    }
  }

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO()
  }

  override fun getSupportedRestrictions(): Set<String> = setOf(
    CommonRestrictions.TENANT_ID,
    CommonRestrictions.WITHOUT_TENANT_ID,
  )
}

fun ProcessInstance.toProcessInformation() = ProcessInformation(
  instanceId = this.id,
  meta = metaOf(
    CommonRestrictions.PROCESS_DEFINITION_KEY to this.processDefinitionId,
    CommonRestrictions.BUSINESS_KEY to this.businessKey,
    CommonRestrictions.TENANT_ID to this.tenantId,
    "rootProcessInstanceId" to this.rootProcessInstanceId,
    CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
  )
)
