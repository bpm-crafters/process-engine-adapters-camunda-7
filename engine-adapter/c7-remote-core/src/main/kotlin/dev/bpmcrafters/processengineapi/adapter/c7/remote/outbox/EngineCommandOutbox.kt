package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import java.time.Instant

/**
 * Outbox of engine commands. Registering a bean of this type activates the outbox.
 *
 * Implement this interface to use an existing outbox: store the command in the transaction of the caller,
 * then pass it to [EngineCommandDelivery] from your own relay.
 */
interface EngineCommandOutbox {

  fun enqueue(command: EngineCommand)

  /**
   * Returns the task ids with a command still to be delivered, so that their redelivery does not run the handler again.
   */
  fun pendingTaskIds(taskIds: Collection<String>): Set<String>
}

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

/**
 * Claimed command. It is a supplier, because reading it may fail for a single entry only.
 */
class OutboxEntry(
  val id: String,
  val attempts: Int,
  val rejections: Int,
  val command: () -> EngineCommand,
)
