package dev.bpmcrafters.processengineapi.adapter.c7.remote.correlation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.Correlation
import dev.bpmcrafters.processengineapi.correlation.SendSignalCmd
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.MessageApiClient
import org.camunda.community.rest.client.api.SignalApiClient
import org.camunda.community.rest.client.model.CorrelationMessageDto
import org.camunda.community.rest.client.model.MessageCorrelationResultWithVariableDto
import org.camunda.community.rest.client.model.SignalDto
import org.camunda.community.rest.variables.SpinValueMapper
import org.camunda.community.rest.variables.ValueMapper
import org.camunda.community.rest.variables.ValueTypeResolverImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mockito.mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import java.util.concurrent.ExecutionException

@ExtendWith(MockitoExtension::class)
class SignalApiImplTest {

  private val signalApiClient: SignalApiClient = mock()

  @Spy
  private val valueMapper: ValueMapper = ValueMapper(
    objectMapper = jacksonObjectMapper(),
    valueTypeResolver = ValueTypeResolverImpl(),
    customValueMapper = listOf(SpinValueMapper(ValueTypeResolverImpl()))
  )

  @InjectMocks
  private lateinit var signalApi: SignalApiImpl

  @Test
  fun `correlate signal`() {

    whenever(signalApiClient.throwSignal(any())).thenReturn(
      ResponseEntity.ok(null)
    )

    signalApi.sendSignal(
      SendSignalCmd(
        signalName = "signal",
        payloadSupplier = { mapOf("correlationId" to 1L) },
        restrictions = mapOf(CommonRestrictions.TENANT_ID to "tenantId")
      )
    ).get()

    verify(signalApiClient).throwSignal(
      SignalDto()
        .name("signal")
        .tenantId("tenantId")
        .variables(valueMapper.mapValues(mapOf("correlationId" to 1L)))
    )
  }

  @Test
  fun `rejects illegal restrictions`() {
    val exception = assertThrows<ExecutionException> {
      signalApi.sendSignal(
        SendSignalCmd(
          signalName = "signal",
          payloadSupplier = { mapOf("correlationId" to 1L) },
          restrictions = mapOf(CommonRestrictions.PROCESS_DEFINITION_ID to "processDefinitionId")
        )
      ).get()
    }

    val expected = signalApi.getSupportedRestrictions().joinToString(", ")
    assertThat(exception.cause!!.message).isEqualTo("Only $expected are supported but processDefinitionId were found.")
  }

}
