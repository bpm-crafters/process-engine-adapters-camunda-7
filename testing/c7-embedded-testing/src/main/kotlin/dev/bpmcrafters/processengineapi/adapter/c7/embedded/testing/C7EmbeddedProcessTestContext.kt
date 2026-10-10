package dev.bpmcrafters.processengineapi.adapter.c7.embedded.testing

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.testing.api.*
import dev.bpmcrafters.processengineapi.testing.config.ProcessTestPollingConfiguration
import org.camunda.bpm.engine.ProcessEngine
import org.camunda.bpm.engine.impl.util.ClockUtil
import java.time.Duration

class C7EmbeddedProcessTestContext(
  private val processEngine: ProcessEngine,
  private val runtimeApis: C7EmbeddedProcessTestRuntimeApis,
  metadata: ProcessDefinitionMetaDataResolver,
  workerId: String,
) : ProcessTestContext {
  private val queryApi = C7EmbeddedProcessTestQueryApi(processEngine, metadata, workerId)
  private val payloadApi = C7EmbeddedProcessTestPayloadApi(processEngine)
  private val polling = ProcessTestPollingConfiguration(Duration.ofMillis(25), Duration.ofSeconds(5))

  override fun qualifier() = C7EmbeddedProcessTestInitializer.QUALIFIER
  override fun runtime() = runtimeApis
  override fun query(): ProcessTestQueryApi = queryApi
  override fun assertions(): ProcessTestAssertApi = C7EmbeddedProcessTestAssertApi(processEngine, queryApi, polling)
  override fun payloads(): ProcessTestPayloadApi = payloadApi
  override fun capabilities(): ProcessTestCapabilities = C7EmbeddedProcessTestCapabilities(processEngine)
  override fun diagnostics() = ProcessTestDiagnostics(polling)
  override fun close() {
    ClockUtil.reset()
    processEngine.close()
  }
}
