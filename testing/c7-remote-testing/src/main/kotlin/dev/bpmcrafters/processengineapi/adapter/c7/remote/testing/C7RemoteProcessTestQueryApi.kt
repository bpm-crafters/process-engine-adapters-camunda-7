package dev.bpmcrafters.processengineapi.adapter.c7.remote.testing

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.toProcessInformation
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.process.ProcessInformation
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestQueryApi
import feign.FeignException
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.client.model.ExternalTaskQueryDto
import org.camunda.community.rest.client.model.FetchExternalTaskTopicDto
import org.camunda.community.rest.client.model.FetchExternalTasksDto
import org.camunda.community.rest.client.model.TaskQueryDto
import org.springframework.beans.factory.getBean
import org.springframework.context.ConfigurableApplicationContext
import java.util.function.Predicate

internal class C7RemoteProcessTestQueryApi(application: ConfigurableApplicationContext, private val workerId: String) : ProcessTestQueryApi {
  private val tasks = application.getBean<TaskApiClient>()
  private val externalTasks = application.getBean<ExternalTaskApiClient>()
  private val processes = application.getBean<ProcessInstanceApiClient>()
  override fun getSupportedRestrictions() = SUPPORTED_RESTRICTIONS
  override fun findUserTasks(taskDescriptionKey: String?, restrictions: Map<String, String>, predicate: Predicate<TaskInformation>?): List<TaskInformation> {
    ensureSupported(restrictions);
    val query =
      TaskQueryDto().applyRestrictions(restrictions); if (taskDescriptionKey != null) query.taskDefinitionKey(taskDescriptionKey); return tasks.queryTasks(
      0,
      1_000,
      query
    ).body.orEmpty().map { it.toTaskInformation(tasks.getIdentityLinks(it.id!!, "candidate").body.orEmpty().toSet(), NoOpMetadataResolver) }
      .filter { predicate?.test(it) ?: true }
  }

  override fun findExternalTasks(
    taskDescriptionKey: String?,
    restrictions: Map<String, String>,
    predicate: Predicate<TaskInformation>?
  ): List<TaskInformation> {
    ensureSupported(restrictions);
    val query = ExternalTaskQueryDto().applyRestrictions(restrictions); if (taskDescriptionKey != null) query.topicName(taskDescriptionKey);
    val candidates = externalTasks.queryExternalTasks(0, 1_000, query).body.orEmpty(); return candidates.groupBy { it.topicName }.flatMap { (topic, selected) ->
      externalTasks.fetchAndLock(
        FetchExternalTasksDto(workerId, selected.size).addTopicsItem(
          FetchExternalTaskTopicDto(
            topic,
            300_000
          )
        )
      ).body.orEmpty().filter { locked -> selected.any { it.id == locked.id } }.map { it.toTaskInformation(NoOpMetadataResolver) }
    }.filter { predicate?.test(it) ?: true }
  }

  override fun getProcessInformation(instanceId: String): ProcessInformation? = try {
    processes.getProcessInstance(instanceId).body?.toProcessInformation()
  } catch (_: FeignException.NotFound) {
    null
  }
}

private fun TaskQueryDto.applyRestrictions(restrictions: Map<String, String>) = apply {
  restrictions.forEach { (key, value) ->
    when (key) {
      CommonRestrictions.PROCESS_INSTANCE_ID -> processInstanceId(value); CommonRestrictions.PROCESS_DEFINITION_ID -> processDefinitionId(value); CommonRestrictions.ACTIVITY_ID -> taskDefinitionKey(
      value
    ); CommonRestrictions.TENANT_ID -> tenantIdIn(listOf(value))
    }
  }
}

private fun ExternalTaskQueryDto.applyRestrictions(restrictions: Map<String, String>) = apply {
  restrictions.forEach { (key, value) ->
    when (key) {
      CommonRestrictions.PROCESS_INSTANCE_ID -> processInstanceId(value); CommonRestrictions.PROCESS_DEFINITION_ID -> processDefinitionId(value); CommonRestrictions.PROCESS_DEFINITION_KEY -> processDefinitionKey(
      value
    ); CommonRestrictions.ACTIVITY_ID -> activityId(value); CommonRestrictions.TENANT_ID -> tenantIdIn(listOf(value))
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
