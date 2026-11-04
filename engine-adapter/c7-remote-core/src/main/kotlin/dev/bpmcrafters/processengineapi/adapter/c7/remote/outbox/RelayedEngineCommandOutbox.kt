package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import java.time.Instant

/**
 * Outbox delivered by the [EngineCommandOutboxRelay] of the adapter.
 */
interface RelayedEngineCommandOutbox : EngineCommandOutbox {

  /**
   * Leases the next due entry until [leaseUntil]. An entry is returned to one caller only while its lease is valid.
   */
  fun claimNext(leaseUntil: Instant): OutboxEntry?

  fun delete(id: String)

  fun reschedule(id: String, attempts: Int, rejections: Int, nextAttemptAt: Instant, lastError: String?)

  fun markDead(id: String, lastError: String?)
}
