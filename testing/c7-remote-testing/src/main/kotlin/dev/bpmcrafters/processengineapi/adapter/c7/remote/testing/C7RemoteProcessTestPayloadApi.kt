package dev.bpmcrafters.processengineapi.adapter.c7.remote.testing

import dev.bpmcrafters.processengineapi.testing.api.ProcessTestPayloadApi
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.client.model.PatchVariablesDto
import org.camunda.community.rest.variables.ValueMapper
import org.springframework.beans.factory.getBean
import org.springframework.context.ConfigurableApplicationContext

internal class C7RemoteProcessTestPayloadApi(application: ConfigurableApplicationContext) : ProcessTestPayloadApi {
  private val processes = application.getBean<ProcessInstanceApiClient>()
  private val tasks = application.getBean<TaskApiClient>()
  private val mapper = application.getBean<ValueMapper>()
  override fun updateProcessPayload(instanceId: String, payload: Map<String, Any?>) {
    processes.modifyProcessInstanceVariables(instanceId, PatchVariablesDto().modifications(mapper.mapValues(payload)))
  }

  override fun getProcessPayload(instanceId: String): Map<String, Any?> =
    processes.getProcessInstanceVariables(instanceId, false).body.orEmpty().mapValues { it.value.value }

  override fun getTaskPayload(taskId: String): Map<String, Any?> = tasks.getTaskLocalVariables(taskId, false).body.orEmpty().mapValues { it.value.value }
}
