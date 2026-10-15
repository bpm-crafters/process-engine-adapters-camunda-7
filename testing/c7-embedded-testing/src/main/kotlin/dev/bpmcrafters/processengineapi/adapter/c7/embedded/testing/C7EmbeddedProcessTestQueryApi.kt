package dev.bpmcrafters.processengineapi.adapter.c7.embedded.testing

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.toProcessInformation
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.process.ProcessInformation
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestQueryApi
import org.camunda.bpm.engine.ProcessEngine
import java.util.function.Predicate

internal class C7EmbeddedProcessTestQueryApi(
  private val engine: ProcessEngine, private val metadata: ProcessDefinitionMetaDataResolver, private val workerId: String,
) : ProcessTestQueryApi {
  override fun getSupportedRestrictions() = SUPPORTED_RESTRICTIONS
  override fun findUserTasks(taskDescriptionKey: String?, restrictions: Map<String, String>, predicate: Predicate<TaskInformation>?): List<TaskInformation> {
    ensureSupported(restrictions)
    return engine.taskService.createTaskQuery().active().initializeFormKeys().list().asSequence()
      .filter { taskDescriptionKey == null || taskDescriptionKey == it.taskDefinitionKey || taskDescriptionKey == it.id }
      .filter { taskMatches(it.processInstanceId, it.processDefinitionId, it.taskDefinitionKey, it.tenantId, restrictions) }
      .map { it.toTaskInformation(engine.taskService.getIdentityLinksForTask(it.id).toSet(), metadata.getProcessDefinitionKey(it.processDefinitionId)) }
      .filter { predicate?.test(it) ?: true }.toList()
  }

  override fun findExternalTasks(
    taskDescriptionKey: String?,
    restrictions: Map<String, String>,
    predicate: Predicate<TaskInformation>?
  ): List<TaskInformation> {
    ensureSupported(restrictions)
    val candidates = engine.externalTaskService.createExternalTaskQuery().list()
      .filter { taskDescriptionKey == null || taskDescriptionKey == it.topicName || taskDescriptionKey == it.id }
      .filter { taskMatches(it.processInstanceId, it.processDefinitionId, it.activityId, it.tenantId, restrictions) }
    return candidates.groupBy { it.topicName }.flatMap { (topic, tasks) ->
      engine.externalTaskService.fetchAndLock(tasks.size, workerId).topic(topic, 300_000).execute()
        .filter { locked -> candidates.any { it.id == locked.id } }.map { it.toTaskInformation() }
    }.filter { predicate?.test(it) ?: true }
  }

  override fun getProcessInformation(instanceId: String): ProcessInformation? =
    engine.runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult()?.toProcessInformation(metadata)

  private fun taskMatches(instanceId: String, definitionId: String, activityId: String, tenantId: String?, restrictions: Map<String, String>) =
    restrictions.all { (key, value) ->
      when (key) {
        CommonRestrictions.PROCESS_INSTANCE_ID -> value == instanceId
        CommonRestrictions.PROCESS_DEFINITION_ID -> value == definitionId
        CommonRestrictions.PROCESS_DEFINITION_KEY -> value == metadata.getProcessDefinitionKey(definitionId)
        CommonRestrictions.ACTIVITY_ID -> value == activityId
        CommonRestrictions.TENANT_ID -> value == tenantId
        else -> false
      }
    }
}

private val SUPPORTED_RESTRICTIONS = setOf(
  CommonRestrictions.PROCESS_INSTANCE_ID,
  CommonRestrictions.PROCESS_DEFINITION_ID,
  CommonRestrictions.PROCESS_DEFINITION_KEY,
  CommonRestrictions.ACTIVITY_ID,
  CommonRestrictions.TENANT_ID
)
