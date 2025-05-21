package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull

import org.camunda.community.rest.client.api.ProcessDefinitionApiClient

/**
 * Simple in-memory caching resolver for process definition for a given process definition id.
 */
data class CachingProcessDefinitionKeyResolver(
  val processDefinitionApiClient: ProcessDefinitionApiClient,
  private val keys: MutableMap<String, String> = mutableMapOf(),
  private val versionTags: MutableMap<String, String> = mutableMapOf()
) {

  /**
   * Resolves process definition key from repository service and uses an in-mem cache.
   * @param processDefinitionId process definition id.
   * @return corresponding key.
   */
  fun getProcessDefinitionKey(processDefinitionId: String?): String? {
    return if (processDefinitionId == null) {
      null
    } else {
      if (!keys.containsKey(processDefinitionId)) {
        fetchProcessByDefinitionId(processDefinitionId)
      }
      keys[processDefinitionId]
    }
  }

  /**
   * Resolves process definition version tag from repository service and uses an in-mem cache.
   * @param processDefinitionId process definition id.
   * @return corresponding version tag.
   */
  fun getProcessDefinitionVersionTag(processDefinitionId: String?): String? {
    return if (processDefinitionId == null) {
      null
    } else {
      if (!versionTags.containsKey(processDefinitionId)) {
        fetchProcessByDefinitionId(processDefinitionId)
      }
      versionTags[processDefinitionId]
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
