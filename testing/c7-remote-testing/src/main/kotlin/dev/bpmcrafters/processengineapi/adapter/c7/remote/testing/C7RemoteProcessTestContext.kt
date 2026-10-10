package dev.bpmcrafters.processengineapi.adapter.c7.remote.testing

import dev.bpmcrafters.processengineapi.testing.api.*
import dev.bpmcrafters.processengineapi.testing.config.ProcessTestPollingConfiguration
import org.springframework.context.ConfigurableApplicationContext
import java.time.Duration

class C7RemoteProcessTestContext(
  private val application: ConfigurableApplicationContext,
  private val stopHandler: () -> Unit,
  private val runtimeApis: C7RemoteProcessTestRuntimeApis,
  workerId: String
) : ProcessTestContext {
  private val queryApi = C7RemoteProcessTestQueryApi(application, workerId)
  private val polling = ProcessTestPollingConfiguration(Duration.ofMillis(100), Duration.ofSeconds(10))
  override fun qualifier() = C7RemoteProcessTestInitializer.QUALIFIER
  override fun runtime() = runtimeApis
  override fun query(): ProcessTestQueryApi = queryApi
  override fun assertions(): ProcessTestAssertApi = C7RemoteProcessTestAssertApi(application, queryApi, polling)
  override fun payloads(): ProcessTestPayloadApi = C7RemoteProcessTestPayloadApi(application)
  override fun capabilities(): ProcessTestCapabilities = C7RemoteProcessTestCapabilities()
  override fun diagnostics() = ProcessTestDiagnostics(polling)
  override fun close() {
    application.close(); stopHandler.invoke()
  }
}
