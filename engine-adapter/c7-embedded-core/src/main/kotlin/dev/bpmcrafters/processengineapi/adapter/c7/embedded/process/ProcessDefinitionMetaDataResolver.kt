package dev.bpmcrafters.processengineapi.adapter.c7.embedded.process

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

}
