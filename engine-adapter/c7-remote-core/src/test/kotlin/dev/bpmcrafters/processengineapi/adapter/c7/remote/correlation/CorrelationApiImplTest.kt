package dev.bpmcrafters.processengineapi.adapter.c7.remote.correlation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.Correlation
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.MessageApiClient
import org.camunda.community.rest.client.model.CorrelationMessageDto
import org.camunda.community.rest.client.model.MessageCorrelationResultWithVariableDto
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
class CorrelationApiImplTest {

  private val messageApiClient: MessageApiClient = mock()

  @Spy
  private val valueMapper: ValueMapper = ValueMapper(
    objectMapper = jacksonObjectMapper(),
    valueTypeResolver = ValueTypeResolverImpl(),
    customValueMapper = listOf(SpinValueMapper(ValueTypeResolverImpl()))
  )

  @InjectMocks
  private lateinit var correlationApi: CorrelationApiImpl

  @Test
  fun `correlate message by correlation variable`() {

    whenever(messageApiClient.deliverMessage(any())).thenReturn(
      ResponseEntity.ok(
        listOf(
          MessageCorrelationResultWithVariableDto()
        )
      )
    )

    correlationApi.correlateMessage(
      CorrelateMessageCmd(
        messageName = "messageName",
        payloadSupplier = { mapOf("some" to 1L) },
        correlation = { Correlation.withKey("myCorrelation").withVariable("varValue") },
        restrictions = mapOf(CommonRestrictions.TENANT_ID to "tenantId")
      )
    ).get()

    verify(messageApiClient).deliverMessage(
      CorrelationMessageDto()
        .messageName("messageName")
        .tenantId("tenantId")
        .localCorrelationKeys(valueMapper.mapValues(mapOf("myCorrelation" to "varValue")))
        .processVariables(valueMapper.mapValues(mapOf("some" to 1L)))
        .resultEnabled(true)
    )
  }

  @Test
  fun `rejects illegal restrictions`() {
    val exception = assertThrows<ExecutionException> {
      correlationApi.correlateMessage(
        CorrelateMessageCmd(
          messageName = "messageName",
          payloadSupplier = { mapOf() },
          correlation = { Correlation.withKey("myCorrelation").withVariable("correlationId") },
          restrictions = mapOf(CommonRestrictions.PROCESS_DEFINITION_ID to "definitionId")
        )
      ).get()
    }
    val expected = correlationApi.getSupportedRestrictions().joinToString(", ")
    assertThat(exception.cause!!.message).isEqualTo("Only $expected are supported but processDefinitionId were found.")
  }
}
