package dev.bpmcrafters.processengineapi.adapter.c7.remote.process

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.MessageApiClient
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.model.*
import org.camunda.community.rest.variables.SpinValueMapper
import org.camunda.community.rest.variables.ValueMapper
import org.camunda.community.rest.variables.ValueTypeResolverImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mockito.*
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import java.util.concurrent.ExecutionException

@ExtendWith(MockitoExtension::class)
class StartProcessApiImplByMessageTest {

  private val processDefinitionApiClient: ProcessDefinitionApiClient = mock()
  private val messageApiClient: MessageApiClient = mock()

  @Spy
  private val valueMapper: ValueMapper = ValueMapper(
    objectMapper = jacksonObjectMapper(),
    valueTypeResolver = ValueTypeResolverImpl(),
    customValueMapper = listOf(SpinValueMapper(ValueTypeResolverImpl()))
  )

  @Spy
  private val processDefinitionMetaDataResolver = CachingProcessDefinitionMetaDataResolver(
    processDefinitionApiClient
  )

  @InjectMocks
  private lateinit var startProcessApi: StartProcessApiImpl

  @BeforeEach
  fun `setup mock`() {
    val processInstance: ProcessInstanceWithVariablesDto = ProcessInstanceWithVariablesDto().apply {
      this.id = "instanceId"
    }
    val processDefinition = ProcessDefinitionDto()
      .versionTag("versionTag")
      .id("definitionId")
      .key("definitionKey")
      .tenantId("tenantId")

    whenever(processDefinitionApiClient.getLatestProcessDefinitionByTenantId(anyString(), any())).thenReturn(
      ResponseEntity.ok(processDefinition)
    )
    whenever(processDefinitionApiClient.getProcessDefinitionByKey(anyString())).thenReturn(
      ResponseEntity.ok(processDefinition)
    )
    whenever(processDefinitionApiClient.startProcessInstanceByKey(anyString(), any())).thenReturn(ResponseEntity.ok(processInstance))
    whenever(processDefinitionApiClient.startProcessInstance(anyString(), any())).thenReturn(ResponseEntity.ok(processInstance))
  }

  @Test
  fun `should start process via message with business key`() {
    // given
    val payload = mapOf(CommonRestrictions.BUSINESS_KEY to "businessKey", "key" to "value")
    val startProcessByMessageCmd = StartProcessByMessageCmd("message", { payload }, mapOf())
    val message = MessageCorrelationResultWithVariableDto().processInstance(
      ProcessInstanceDto()
        .id("instanceId")
        .businessKey("businessKey")
        .definitionKey("definitionKey")
        .definitionId("definitionId")
        .tenantId("tenantId")
    )

    whenever(messageApiClient.deliverMessage(any())).thenReturn(ResponseEntity.ok(listOf(message)))

    // When
    val info = startProcessApi.startProcess(startProcessByMessageCmd).get()

    // Then
    assertThat(info.instanceId).isEqualTo("instanceId")
    assertThat(info.meta[CommonRestrictions.BUSINESS_KEY]).isEqualTo("businessKey")
    assertThat(info.meta[CommonRestrictions.PROCESS_DEFINITION_KEY]).isEqualTo("definitionKey")
    assertThat(info.meta[CommonRestrictions.PROCESS_DEFINITION_ID]).isEqualTo("definitionId")
    assertThat(info.meta[CommonRestrictions.TENANT_ID]).isEqualTo("tenantId")
    verify(messageApiClient).deliverMessage(
      CorrelationMessageDto()
        .messageName("message")
        .businessKey("businessKey")
        .processVariables(valueMapper.mapValues(payload))
        .resultEnabled(true)
    )
  }

  @Test
  fun `rejects illegal restrictions`() {
    val exception = assertThrows<ExecutionException> {
      startProcessApi.startProcess(
        StartProcessByMessageCmd("message", { mapOf() }, mapOf(
          CommonRestrictions.BUSINESS_KEY to "businessKey"
        ))
      ).get()
    }
    val expected = startProcessApi.getSupportedRestrictions().joinToString(", ")
    assertThat(exception.cause!!.message).isEqualTo("Only $expected are supported but businessKey were found.")
  }


}
