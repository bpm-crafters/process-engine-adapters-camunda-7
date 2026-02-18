package dev.bpmcrafters.processengineapi.adapter.c7.embedded.correlation

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.correlation.SendSignalCmd
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

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
}