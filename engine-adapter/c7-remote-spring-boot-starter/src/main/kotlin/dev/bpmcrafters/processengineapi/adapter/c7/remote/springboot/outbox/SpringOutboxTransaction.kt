package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.outbox

import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.*
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

internal object SpringOutboxTransaction : OutboxTransaction {
  override fun isActive() = TransactionSynchronizationManager.isActualTransactionActive()

  override fun afterCommit(action: () -> Unit) {
    TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
      override fun afterCommit() = action()
    })
  }
}
