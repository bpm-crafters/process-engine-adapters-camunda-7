package dev.bpmcrafters.processengineapi.adapter.c7.remote.process

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.TestFixtures
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionAtElementCmd
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.MessageApiClient
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.model.ProcessDefinitionDto
import org.camunda.community.rest.client.model.ProcessInstanceModificationInstructionDto
import org.camunda.community.rest.client.model.ProcessInstanceModificationInstructionDto.TypeEnum.START_BEFORE_ACTIVITY
import org.camunda.community.rest.client.model.ProcessInstanceWithVariablesDto
import org.camunda.community.rest.client.model.StartProcessInstanceDto
import org.camunda.community.rest.variables.ValueMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mockito.*
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity

@ExtendWith(MockitoExtension::class)
class StartProcessApiImplByDefinitionAtElementTest {

  @Suppress("unused")
  private val messageApiClient: MessageApiClient = mock()
  private val processDefinitionApiClient: ProcessDefinitionApiClient = mock()

  @Spy
  private val valueMapper: ValueMapper = TestFixtures.valueMapper()

  @Spy
  @Suppress("unused")
  private val processDefinitionMetaDataResolver = CachingProcessDefinitionMetaDataResolver(
    processDefinitionApiClient
  )

  @InjectMocks
  private lateinit var startProcessApi: StartProcessApiImpl

  @BeforeEach
  fun `setup mock`() {

    val instance = ProcessInstanceWithVariablesDto()
    instance.id = "instanceId"

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

    whenever(processDefinitionApiClient.startProcessInstance(anyString(), any())).thenReturn(
      ResponseEntity.ok(instance)
    )
  }

  @Test
  fun `should start process at element without payload`() {

    // given
    val startProcessByDefinitionAtElementCmd = StartProcessByDefinitionAtElementCmd(
      definitionKey = "definitionKey",
      elementId = "myActivity",
      payloadSupplier = { emptyMap() },
      restrictions = mapOf()
    )

    // when
    startProcessApi.startProcess(startProcessByDefinitionAtElementCmd).get()

    // then
    verify(processDefinitionApiClient).startProcessInstance(
      "definitionId",
      StartProcessInstanceDto().apply {
        this.variables = mapOf()
        this.startInstructions = listOf(
          ProcessInstanceModificationInstructionDto().apply {
            this.type = START_BEFORE_ACTIVITY
            this.activityId = "myActivity"
          }
        )
      }
    )
  }

  @Test
  fun `should start process at element with tenant without payload`() {
    // given
    val startProcessByDefinitionAtElementCmd = StartProcessByDefinitionAtElementCmd(
      definitionKey = "definitionKey",
      elementId = "userTask1",
      payloadSupplier = { emptyMap() },
      restrictions = mapOf(
        CommonRestrictions.TENANT_ID to "tenantId"
      )
    )

    // when
    startProcessApi.startProcess(startProcessByDefinitionAtElementCmd).get()

    // then
    verify(processDefinitionApiClient).startProcessInstance(
      "definitionId",
      StartProcessInstanceDto().apply {
        this.variables = mapOf()
        this.startInstructions = listOf(
          ProcessInstanceModificationInstructionDto().apply {
            this.type = START_BEFORE_ACTIVITY
            this.activityId = "userTask1"
          }
        )
      }
    )
  }

  @Test
  fun `should start process at element with payload and business key`() {
    // given
    val startProcessByDefinitionAtElementCmd = StartProcessByDefinitionAtElementCmd(
      definitionKey = "definitionKey",
      elementId = "serviceTask1",
      payloadSupplier = { mapOf("key" to "value", CommonRestrictions.BUSINESS_KEY to "businessKey") },
      restrictions = mapOf()
    )

    // when
    startProcessApi.startProcess(startProcessByDefinitionAtElementCmd).get()

    // then
    val variables = mapOf("key" to "value", CommonRestrictions.BUSINESS_KEY to "businessKey")
    verify(processDefinitionApiClient).startProcessInstance(
      "definitionId",
      StartProcessInstanceDto().apply {
        this.businessKey = "businessKey"
        this.variables = valueMapper.mapValues(variables)
        this.startInstructions = listOf(
          ProcessInstanceModificationInstructionDto().apply {
            this.type = START_BEFORE_ACTIVITY
            this.activityId = "serviceTask1"
          }
        )
      }
    )
  }

}
