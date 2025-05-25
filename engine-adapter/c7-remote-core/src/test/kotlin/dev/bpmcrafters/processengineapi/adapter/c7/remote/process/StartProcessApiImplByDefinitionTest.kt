package dev.bpmcrafters.processengineapi.adapter.c7.remote.process

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.Correlation
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.MessageApiClient
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.model.ProcessDefinitionDto
import org.camunda.community.rest.client.model.ProcessInstanceWithVariablesDto
import org.camunda.community.rest.client.model.StartProcessInstanceDto
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
class StartProcessApiImplByDefinitionTest {

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
      ResponseEntity.ok(
        ProcessDefinitionDto()
          .versionTag("versionTag")
          .id("definitionId")
          .key("definitionKey")
          .tenantId("tenantId")
      )
    )
    whenever(processDefinitionApiClient.startProcessInstanceByKey(anyString(), any())).thenReturn(ResponseEntity.ok(processInstance))
    whenever(processDefinitionApiClient.startProcessInstance(anyString(), any())).thenReturn(ResponseEntity.ok(processInstance))
  }

  @Test
  fun `should start process via definition without payload`() {
    // given
    val startProcessByDefinitionCmd = StartProcessByDefinitionCmd("definitionKey", { emptyMap() }, mapOf())

    // when
    startProcessApi.startProcess(startProcessByDefinitionCmd).get()

    // then
    verify(processDefinitionApiClient).startProcessInstance("definitionId", StartProcessInstanceDto().variables(mapOf()))
  }

  @Test
  fun `should start process via definition with tenant without payload`() {
    // given
    val startProcessByDefinitionCmd = StartProcessByDefinitionCmd(
      "definitionKey",
      { emptyMap() },
      mapOf(
        CommonRestrictions.TENANT_ID to "tenantId"
      )
    )

    // when
    startProcessApi.startProcess(startProcessByDefinitionCmd).get()

    // then
    verify(processDefinitionApiClient).startProcessInstance("definitionId", StartProcessInstanceDto().variables(mapOf()))
  }

  @Test
  fun `should start process via definition with payload and business key`() {
    // given
    val startProcessByDefinitionCmd = StartProcessByDefinitionCmd(
      "definitionKey",
      { mapOf("key" to "value", CommonRestrictions.BUSINESS_KEY to "businessKey") },
      mapOf()
    )

    // when
    startProcessApi.startProcess(startProcessByDefinitionCmd).get()

    // then
    verify(processDefinitionApiClient).startProcessInstance(
      "definitionId",
      StartProcessInstanceDto().apply {
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
  fun `rejects illegal restrictions`() {
    val exception = assertThrows<ExecutionException> {
      startProcessApi.startProcess(
        StartProcessByDefinitionCmd(
          "definitionKey",
          { mapOf("key" to "value", CommonRestrictions.BUSINESS_KEY to "businessKey") },
          mapOf(CommonRestrictions.BUSINESS_KEY to "businessKey")
        )
      ).get()
    }
    val expected = startProcessApi.getSupportedRestrictions().joinToString(", ")
    assertThat(exception.cause!!.message).isEqualTo("Only $expected are supported but businessKey were found.")
  }

}
