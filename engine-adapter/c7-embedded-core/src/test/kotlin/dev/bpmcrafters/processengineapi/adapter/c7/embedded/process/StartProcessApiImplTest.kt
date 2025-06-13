package dev.bpmcrafters.processengineapi.adapter.c7.embedded.process

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd
import org.camunda.bpm.engine.ProcessEngine
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.repository.ProcessDefinitionQuery
import org.camunda.bpm.engine.runtime.MessageCorrelationBuilder
import org.camunda.bpm.engine.runtime.ProcessInstance
import org.camunda.community.mockito.QueryMocks
import org.camunda.community.mockito.process.ProcessDefinitionFake
import org.camunda.community.mockito.process.ProcessInstanceFake
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class StartProcessApiImplTest {

  @Mock
  private lateinit var repositoryService: RepositoryService
  @Mock
  private lateinit var runtimeService: RuntimeService

  @InjectMocks
  private lateinit var startProcessApi: StartProcessApiImpl

  @Test
  fun `should start process via definition without payload`() {
    // given
    val startProcessByDefinitionCmd = StartProcessByDefinitionCmd("definitionKey", { emptyMap() })
    val processInstance: ProcessInstance = ProcessInstanceFake.builder().id("someId").build()
    whenever(runtimeService.startProcessInstanceByKey(anyString(), anyOrNull(), anyMap())).thenReturn(processInstance)

    // when
    startProcessApi.startProcess(startProcessByDefinitionCmd).get()

    // then
    verify(runtimeService).startProcessInstanceByKey("definitionKey", null, emptyMap())
  }

  @Test
  fun `should start process via definition without payload with tenant`() {
    // given
    val startProcessByDefinitionCmd = StartProcessByDefinitionCmd(
      "definitionKey",
      { emptyMap() },
      mapOf(CommonRestrictions.TENANT_ID to "tenantId")
    )
    whenever(runtimeService.startProcessInstanceById(
      anyString(),
      anyOrNull(),
      anyMap(),
    )).thenReturn(
      ProcessInstanceFake
        .builder()
        .id("someId")
        .build()
    )
    QueryMocks.mockProcessDefinitionQuery(repositoryService).singleResult(
      ProcessDefinitionFake
        .builder()
        .id("definitionId")
        .tenantId("tenantId")
        .build()
    )

    // when
    startProcessApi.startProcess(startProcessByDefinitionCmd).get()

    // then
    verify(runtimeService).startProcessInstanceById("definitionId", null, emptyMap())
  }

  @Test
  fun `should start process via definition with payload and business key`() {
    // given
    val processInstance: ProcessInstance = ProcessInstanceFake.builder().id("someId").build()
    whenever(runtimeService.startProcessInstanceByKey(anyString(), anyOrNull(), anyMap())).thenReturn(processInstance)
    val startProcessByDefinitionCmd = StartProcessByDefinitionCmd("definitionKey", {
      mapOf(
        "key" to "value",
        CommonRestrictions.BUSINESS_KEY to "businessKey"
      )
    })

    // when
    startProcessApi.startProcess(startProcessByDefinitionCmd).get()

    // then
    verify(runtimeService).startProcessInstanceByKey(
      "definitionKey", "businessKey", mapOf(
        "key" to "value",
        CommonRestrictions.BUSINESS_KEY to "businessKey"
      )
    )
  }

  @Test
  fun `should start process via message with business key`() {
    // given
    val payload = mapOf(CommonRestrictions.BUSINESS_KEY to "testBusinessKey", "key" to "value")
    val startProcessByMessageCmd = StartProcessByMessageCmd("testMessage", { payload })
    val correlationBuilder = messageCorrelationMock()
    whenever(runtimeService.createMessageCorrelation(any())).thenReturn(correlationBuilder)

    // When
    startProcessApi.startProcess(startProcessByMessageCmd).get()

    // Then
    verify(runtimeService).createMessageCorrelation("testMessage")
    verify(correlationBuilder).processInstanceBusinessKey("testBusinessKey")
    verify(correlationBuilder).setVariables(mapOf(CommonRestrictions.BUSINESS_KEY to "testBusinessKey", "key" to "value"))
  }

  @Test
  fun `should start process via message with payload`() {
    // given
    val payload = mapOf("key" to "value")
    val startProcessByMessageCmd = StartProcessByMessageCmd("testMessage", { payload })
    val correlationBuilder = messageCorrelationMock()
    whenever(runtimeService.createMessageCorrelation(any())).thenReturn(correlationBuilder)

    // When
    startProcessApi.startProcess(startProcessByMessageCmd).get()

    // Then
    verify(runtimeService).createMessageCorrelation("testMessage")
    verify(correlationBuilder).setVariables(mapOf("key" to "value"))
    verify(correlationBuilder, times(0)).processInstanceBusinessKey(any())
  }

  private fun messageCorrelationMock(): MessageCorrelationBuilder {
    val builder: MessageCorrelationBuilder = mock()
    lenient().whenever(builder.processInstanceBusinessKey(any())).thenReturn(builder)
    whenever(builder.setVariables(anyMap())).thenReturn(builder)
    whenever(builder.correlateStartMessage()).thenReturn(ProcessInstanceFake.builder().id("someId").build())

    return builder
  }
}
