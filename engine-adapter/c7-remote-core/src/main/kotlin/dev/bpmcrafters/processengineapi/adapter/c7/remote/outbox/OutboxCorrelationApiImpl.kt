package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.EngineCommand.*
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import java.util.concurrent.CompletableFuture

class OutboxCorrelationApiImpl(
  private val delegate: CorrelationApi,
  private val dispatcher: TransactionalEngineCommandDispatcher,
) : CorrelationApi by delegate {

  override fun correlateMessage(cmd: CorrelateMessageCmd): CompletableFuture<Empty> =
    dispatcher.dispatch("correlation of message ${cmd.messageName}", {
      val correlation = cmd.correlation.get()
      CorrelateMessage(
        messageName = cmd.messageName,
        correlationKey = correlation.correlationKey,
        correlationVariable = correlation.correlationVariable,
        restrictions = delegate.ensureSupported(cmd.restrictions),
        payload = cmd.get(),
      )
    }) { delegate.correlateMessage(cmd) }
}
