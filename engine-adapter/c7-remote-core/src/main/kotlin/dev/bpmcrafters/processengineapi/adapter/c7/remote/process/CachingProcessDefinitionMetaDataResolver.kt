package dev.bpmcrafters.processengineapi.adapter.c7.remote.process

import org.camunda.community.rest.client.api.ProcessDefinitionApiClient

/**
 * Simple in-memory caching resolver for process definition for a given process definition id.
 */
data class CachingProcessDefinitionMetaDataResolver(
  val processDefinitionApiClient: ProcessDefinitionApiClient,
  private val keys: MutableMap<String, String> = mutableMapOf(),
  private val versionTags: MutableMap<String, String?> = mutableMapOf(),
  private val processDefinitionIds: MutableMap<Pair<String, String?>, String> = mutableMapOf()
) : ProcessDefinitionMetaDataResolver {

  override fun getProcessDefinitionKey(processDefinitionId: String?): String? {
    return if (processDefinitionId == null) {
      null
    } else {
      if (!keys.containsKey(processDefinitionId)) {
        fetchProcessByDefinitionId(processDefinitionId)
      }
      keys[processDefinitionId]
    }
  }

  override fun getProcessDefinitionVersionTag(processDefinitionId: String?): String? {
    return if (processDefinitionId == null) {
      null
    } else {
      if (!versionTags.containsKey(processDefinitionId)) {
        fetchProcessByDefinitionId(processDefinitionId)
      }
      versionTags[processDefinitionId]
    }
  }

  override fun getProcessDefinitionId(processDefinitionKey: String, tenantId: String?): String? {
    val keyWithTenant = processDefinitionKey to tenantId
    if (!processDefinitionIds.containsKey(keyWithTenant)) {
      fetchProcessByKeyAndTenant(processDefinitionKey, tenantId)
    }
    return processDefinitionIds[keyWithTenant]
  }

  private fun fetchProcessByKeyAndTenant(processDefinitionKey: String, tenantId: String?) {
    val result = if (tenantId != null) {
      processDefinitionApiClient.getLatestProcessDefinitionByTenantId(
        processDefinitionKey,
        tenantId
      )
    } else {
      processDefinitionApiClient.getProcessDefinitionByKey(
        processDefinitionKey
      )
    }
    val processDefinition = result.body
    if (processDefinition != null) {
      processDefinitionIds[processDefinitionKey to tenantId] = processDefinition.id
      versionTags[processDefinition.id] = processDefinition.versionTag
      keys[processDefinition.id] = processDefinition.key
    }
  }

  private fun fetchProcessByDefinitionId(processDefinitionId: String) {
    val result = processDefinitionApiClient.getProcessDefinition(processDefinitionId)
    val definition =
      requireNotNull(result.body) { "Could not retrieve process definition for id $processDefinitionId, resulted in status code ${result.statusCode}" }
    this.keys[processDefinitionId] = definition.key
    this.versionTags[processDefinitionId] = definition.versionTag
  }
}
