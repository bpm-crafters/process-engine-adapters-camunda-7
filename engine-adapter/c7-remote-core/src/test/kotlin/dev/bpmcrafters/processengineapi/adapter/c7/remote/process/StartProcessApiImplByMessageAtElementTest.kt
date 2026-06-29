package dev.bpmcrafters.processengineapi.adapter.c7.remote.process

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.c7.remote.TestFixtures
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageAtElementCmd
import org.camunda.community.rest.client.api.MessageApiClient
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.camunda.community.rest.client.model.CorrelationMessageDto
import org.camunda.community.rest.client.model.MessageCorrelationResultWithVariableDto
import org.camunda.community.rest.client.model.ProcessInstanceDto
import org.camunda.community.rest.client.model.ProcessInstanceModificationDto
import org.camunda.community.rest.client.model.ProcessInstanceModificationInstructionDto
import org.camunda.community.rest.client.model.ProcessInstanceModificationInstructionDto.TypeEnum.START_BEFORE_ACTIVITY
import org.camunda.community.rest.variables.ValueMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mockito.*
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity

@ExtendWith(MockitoExtension::class)
class StartProcessApiImplByMessageAtElementTest {

  @Suppress("unused")
  private val processDefinitionApiClient: ProcessDefinitionApiClient = mock()
  private val messageApiClient: MessageApiClient = mock()
  private val processInstanceApiClient: ProcessInstanceApiClient = mock()

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

    val message = MessageCorrelationResultWithVariableDto().processInstance(
      ProcessInstanceDto()
        .id("instanceId")
        .definitionKey("definitionKey")
        .definitionId("definitionId")
        .tenantId("tenantId")
    )

    whenever(messageApiClient.deliverMessage(any())).thenReturn(
      ResponseEntity.ok(listOf(message))
    )
  }

  @Test
  fun `should start process at element via message without payload`() {

    // given
    val startProcessByMessageAtElementCmd = StartProcessByMessageAtElementCmd(
      messageName = "startMessage",
      elementId = "myActivity",
      payloadSupplier = { emptyMap() },
      restrictions = mapOf()
    )

    // when
    startProcessApi.startProcess(startProcessByMessageAtElementCmd).get()

    // then
    verify(messageApiClient).deliverMessage(
      CorrelationMessageDto()
        .messageName("startMessage")
        .processVariables(valueMapper.mapValues(mapOf()))
        .resultEnabled(true)
    )
    verify(processInstanceApiClient).modifyProcessInstance(
      "instanceId",
      ProcessInstanceModificationDto().apply {
        this.instructions = listOf(
          ProcessInstanceModificationInstructionDto().apply {
            this.type = START_BEFORE_ACTIVITY
            this.activityId = "myActivity"
          }
        )
      }
    )
  }

  @Test
  fun `should start process at element via message with payload, business key and tenant`() {

    // given
    val payload = mapOf("key" to "value", CommonRestrictions.BUSINESS_KEY to "businessKey")
    val startProcessByMessageAtElementCmd = StartProcessByMessageAtElementCmd(
      messageName = "startMessage",
      elementId = "serviceTask1",
      payloadSupplier = { payload },
      restrictions = mapOf(CommonRestrictions.TENANT_ID to "tenantId")
    )

    // when
    startProcessApi.startProcess(startProcessByMessageAtElementCmd).get()

    // then
    verify(messageApiClient).deliverMessage(
      CorrelationMessageDto()
        .messageName("startMessage")
        .businessKey("businessKey")
        .processVariables(valueMapper.mapValues(payload))
        .resultEnabled(true)
        .tenantId("tenantId")
    )
    verify(processInstanceApiClient).modifyProcessInstance(
      "instanceId",
      ProcessInstanceModificationDto().apply {
        this.instructions = listOf(
          ProcessInstanceModificationInstructionDto().apply {
            this.type = START_BEFORE_ACTIVITY
            this.activityId = "serviceTask1"
          }
        )
      }
    )
  }

}
