package dev.bpmcrafters.processengineapi.adapter.c7.remote.process

/**
 * Retrieves information about process definition.
 */
interface ProcessDefinitionMetaDataResolver {
  /**
   * Retrieves a process definition key for given process definition id.
   * @param processDefinitionId process definition id.
   * @return process definition key.
   */
  fun getProcessDefinitionKey(processDefinitionId: String?): String?
  /**
   * Retrieves a process definition version tag for given process definition id.
   * @param processDefinitionId process definition id.
   * @return process definition tag.
   */
  fun getProcessDefinitionVersionTag(processDefinitionId: String?): String?

  /**
   * Retrieves process definition id for a process definition key and an optional tenant.
   * @param processDefinitionKey process definition key
   * @param tenantId optional tenant id.
   * @return process definition id, or null if not found.
   */
  fun getProcessDefinitionId(processDefinitionKey: String, tenantId: String?): String?
}
