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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity

@ExtendWith(MockitoExtension::class)
class StartProcessApiImplTest {

  @Spy
  private val valueMapper: ValueMapper = ValueMapper(
    objectMapper = jacksonObjectMapper(),
    valueTypeResolver = ValueTypeResolverImpl(),
    customValueMapper = listOf(SpinValueMapper(ValueTypeResolverImpl()))
  )

  @Mock
  private lateinit var processDefinitionApiClient: ProcessDefinitionApiClient

  @Mock
  private lateinit var messageApiClient: MessageApiClient

  @InjectMocks
  private lateinit var startProcessApi: StartProcessApiImpl

  @Test
  fun `should start process via definition without payload`() {
    // given
    val startProcessByDefinitionCmd = StartProcessByDefinitionCmd("definitionKey") { emptyMap() }
    val processInstance: ProcessInstanceWithVariablesDto = ProcessInstanceWithVariablesDto().apply {
      this.id = "someId"
    }
    whenever(processDefinitionApiClient.startProcessInstanceByKey(anyString(), any())).thenReturn(ResponseEntity.ok(processInstance))

    // when
    startProcessApi.startProcess(startProcessByDefinitionCmd).get()

    // then
    verify(processDefinitionApiClient).startProcessInstanceByKey("definitionKey", StartProcessInstanceDto().variables(mapOf()))
  }

  @Test
  fun `should start process via definition with payload and business key`() {
    // given
    val processInstance: ProcessInstanceWithVariablesDto = ProcessInstanceWithVariablesDto().apply {
      this.id = "someId"
    }
    whenever(processDefinitionApiClient.startProcessInstanceByKey(anyString(), any())).thenReturn(ResponseEntity.ok(processInstance))

    val startProcessByDefinitionCmd = StartProcessByDefinitionCmd("definitionKey") {
      mapOf(
        "key" to "value",
        CommonRestrictions.BUSINESS_KEY to "businessKey"
      )
    }

    // when
    startProcessApi.startProcess(startProcessByDefinitionCmd).get()

    // then
    verify(processDefinitionApiClient).startProcessInstanceByKey(
      "definitionKey", StartProcessInstanceDto().apply {
        this.businessKey = "businessKey"
        this.variables = valueMapper.mapValues(
          mapOf(
            "key" to "value",
            CommonRestrictions.BUSINESS_KEY to "businessKey"
          )
        )
      }
    )
  }

  @Test
  fun `should start process via message with business key`() {
    // given
    val payload = mapOf(CommonRestrictions.BUSINESS_KEY to "testBusinessKey", "key" to "value")
    val startProcessByMessageCmd = StartProcessByMessageCmd("testMessage") { payload }
    val message = MessageCorrelationResultWithVariableDto().processInstance(
      ProcessInstanceDto()
        .id("instance-id")
        .businessKey("testBusinessKey")
        .definitionKey("testDefinitionKey")
        .definitionId("testDefinitionId")
        .tenantId("tenantId")
    )

    whenever(messageApiClient.deliverMessage(any())).thenReturn(ResponseEntity.ok(listOf(message)))

    // When
    val info = startProcessApi.startProcess(startProcessByMessageCmd).get()

    // Then
    assertThat(info.instanceId).isEqualTo("instance-id")
    assertThat(info.meta[CommonRestrictions.BUSINESS_KEY]).isEqualTo("testBusinessKey")
    assertThat(info.meta[CommonRestrictions.PROCESS_DEFINITION_KEY]).isEqualTo("testDefinitionKey")
    assertThat(info.meta[CommonRestrictions.PROCESS_DEFINITION_ID]).isEqualTo("testDefinitionId")
    assertThat(info.meta[CommonRestrictions.TENANT_ID]).isEqualTo("tenantId")
    verify(messageApiClient).deliverMessage(
      CorrelationMessageDto()
        .messageName("testMessage")
        .businessKey("testBusinessKey")
        .processVariables(valueMapper.mapValues(payload))
        .resultEnabled(true)
    )
  }

}
