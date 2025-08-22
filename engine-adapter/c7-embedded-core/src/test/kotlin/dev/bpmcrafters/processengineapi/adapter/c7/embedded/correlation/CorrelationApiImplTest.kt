package dev.bpmcrafters.processengineapi.adapter.c7.embedded.correlation

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.Correlation
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.runtime.MessageCorrelationBuilder
import org.camunda.community.mockito.ProcessExpressions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.concurrent.ExecutionException

@ExtendWith(MockitoExtension::class)
class CorrelationApiImplTest {

  private val runtimeService: RuntimeService = mock()

  @InjectMocks
  private lateinit var correlationApi: CorrelationApiImpl

  private lateinit var correlation: MessageCorrelationBuilder

  @BeforeEach
  fun setUp() {
    correlation = ProcessExpressions.mockMessageCorrelation(runtimeService, "messageName")
  }

  @Test
  fun `correlate message by local correlation variable`() {
    correlationApi.correlateMessage(
      CorrelateMessageCmd(
        messageName = "messageName",
        payloadSupplier = { mapOf("some" to 1L) },
        correlation = { Correlation.withKey("varValue").withVariable("myCorrelation") },
        restrictions = mapOf(CommonRestrictions.TENANT_ID to "tenantId")
      )
    ).get()

    verify(correlation).correlateWithResult()
    verify(correlation).tenantId("tenantId")
    verify(correlation).setVariables(mapOf("some" to 1L))
    verify(correlation).localVariableEquals("myCorrelation", "varValue")
    verifyNoMoreInteractions(correlation)
  }

  @Test
  fun `correlate message by global correlation variable`() {
    correlationApi.correlateMessage(
      CorrelateMessageCmd(
        messageName = "messageName",
        payloadSupplier = { mapOf("some" to 1L) },
        correlation = { Correlation.withKey("varValue").withVariable("myCorrelation") },
        restrictions = mapOf(CommonRestrictions.TENANT_ID to "tenantId", CorrelationApiImpl.Restrictions.USE_GLOBAL_CORRELATION_KEY to "TRUE")
      )
    ).get()

    verify(correlation).correlateWithResult()
    verify(correlation).tenantId("tenantId")
    verify(correlation).setVariables(mapOf("some" to 1L))
    verify(correlation).processInstanceVariableEquals("myCorrelation", "varValue")
    verifyNoMoreInteractions(correlation)

  }
}
