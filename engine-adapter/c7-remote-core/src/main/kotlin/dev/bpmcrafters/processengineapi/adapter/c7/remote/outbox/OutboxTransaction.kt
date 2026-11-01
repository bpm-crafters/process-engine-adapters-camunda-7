package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

/**
 * View on the transaction of the caller, keeping the core free of spring-tx.
 */
interface OutboxTransaction {
  fun isActive(): Boolean
  fun afterCommit(action: () -> Unit)
}
