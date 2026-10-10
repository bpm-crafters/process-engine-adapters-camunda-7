package dev.bpmcrafters.processengineapi.adapter.c7.embedded.testing

import dev.bpmcrafters.processengineapi.testing.api.ProcessTestPayloadApi
import org.camunda.bpm.engine.ProcessEngine

internal class C7EmbeddedProcessTestPayloadApi(private val engine: ProcessEngine) : ProcessTestPayloadApi {
  override fun updateProcessPayload(instanceId: String, payload: Map<String, Any?>) = engine.runtimeService.setVariables(instanceId, payload)
  override fun getProcessPayload(instanceId: String): Map<String, Any?> = engine.runtimeService.getVariables(instanceId).toMap()
  override fun getTaskPayload(taskId: String): Map<String, Any?> = engine.taskService.getVariablesLocal(taskId).toMap()
}
