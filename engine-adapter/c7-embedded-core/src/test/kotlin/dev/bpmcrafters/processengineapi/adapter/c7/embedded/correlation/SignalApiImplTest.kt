package dev.bpmcrafters.processengineapi.adapter.c7.embedded.correlation

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.correlation.SendSignalCmd
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.util.concurrent.ExecutionException
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class SignalApiImplTest {

  @Mock
  private lateinit var runtimeService: RuntimeService

  private lateinit var signalApi: SignalApiImpl

  @BeforeEach
  fun setUp() {
    signalApi = SignalApiImpl(
      runtimeService = runtimeService,
      commandExecutor = EngineCommandExecutor { it.run() }
    )
  }

  @Test
  fun `should send signal and return completedFuture`() {
    val signalBuilder = mock<SignalEventReceivedBuilder>()
    val payload = mapOf("key" to "value")
    val cmd = SendSignalCmd(signalName = "mySignal", payloadSupplier = { payload })
    whenever(runtimeService.createSignalEvent(any())).thenReturn(signalBuilder)
    whenever(signalBuilder.setVariables(any())).thenReturn(signalBuilder)
    doAnswer { }.whenever(signalBuilder).send()

    val future = signalApi.sendSignal(cmd = cmd).get()

    assertThat(future).isEqualTo(Empty)
    verify(runtimeService).createSignalEvent("mySignal")
    verify(signalBuilder).setVariables(payload)
    verify(signalBuilder).send()
    verifyNoMoreInteractions(signalBuilder)
  }

  @Test
  fun `should send signal with tenantId alone`() {
    val signalBuilder = mock<SignalEventReceivedBuilder>()
    val cmd = SendSignalCmd(
      signalName = "mySignal",
      payloadSupplier = { emptyMap() },
      restrictions = mapOf(CommonRestrictions.TENANT_ID to "tenant-a"),
    )
    whenever(runtimeService.createSignalEvent(any())).thenReturn(signalBuilder)
    whenever(signalBuilder.tenantId(any())).thenReturn(signalBuilder)
    whenever(signalBuilder.setVariables(any())).thenReturn(signalBuilder)
    doAnswer { }.whenever(signalBuilder).send()

    val result = signalApi.sendSignal(cmd = cmd).get()

    assertThat(result).isEqualTo(Empty)
    verify(signalBuilder).tenantId("tenant-a")
    verify(signalBuilder).send()
  }

  @Test
  fun `should send signal with withoutTenantId alone`() {
    val signalBuilder = mock<SignalEventReceivedBuilder>()
    val cmd = SendSignalCmd(
      signalName = "mySignal",
      payloadSupplier = { emptyMap() },
      restrictions = mapOf(CommonRestrictions.WITHOUT_TENANT_ID to "true"),
    )
    whenever(runtimeService.createSignalEvent(any())).thenReturn(signalBuilder)
    whenever(signalBuilder.withoutTenantId()).thenReturn(signalBuilder)
    whenever(signalBuilder.setVariables(any())).thenReturn(signalBuilder)
    doAnswer { }.whenever(signalBuilder).send()

    val result = signalApi.sendSignal(cmd = cmd).get()

    assertThat(result).isEqualTo(Empty)
    verify(signalBuilder).withoutTenantId()
    verify(signalBuilder).send()
  }

  @Test
  fun `should reject signal with both tenantId and withoutTenantId`() {
    val signalBuilder = mock<SignalEventReceivedBuilder>()
    val cmd = SendSignalCmd(
      signalName = "mySignal",
      payloadSupplier = { emptyMap() },
      restrictions = mapOf(
        CommonRestrictions.TENANT_ID to "tenant-a",
        CommonRestrictions.WITHOUT_TENANT_ID to "true",
      ),
    )
    whenever(runtimeService.createSignalEvent(any())).thenReturn(signalBuilder)
    whenever(signalBuilder.tenantId(any())).thenReturn(signalBuilder)

    assertThatThrownBy { signalApi.sendSignal(cmd = cmd).get() }
      .isInstanceOf(ExecutionException::class.java)
      .hasCauseInstanceOf(IllegalArgumentException::class.java)

    verify(signalBuilder, never()).send()
  }
}
