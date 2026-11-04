package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.EngineCommand.*
import dev.bpmcrafters.processengineapi.correlation.SendSignalCmd
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import java.util.concurrent.CompletableFuture

class OutboxSignalApiImpl(
  private val delegate: SignalApi,
  private val dispatcher: TransactionalEngineCommandDispatcher,
) : SignalApi by delegate {

  override fun sendSignal(cmd: SendSignalCmd): CompletableFuture<Empty> =
    dispatcher.dispatch("signal ${cmd.signalName}", {
      SendSignal(cmd.signalName, delegate.ensureSupported(cmd.restrictions), cmd.get())
    }) { delegate.sendSignal(cmd) }
}
