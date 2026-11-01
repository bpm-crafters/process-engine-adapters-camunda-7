package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.EngineCommandDelivery.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Duration

private val logger = KotlinLogging.logger {}

private val MAX_BACKOFF = Duration.ofMinutes(5)

/**
 * Delivers commands one by one, so that a lease covers one engine call and no transaction is held during it.
 */
class EngineCommandOutboxRelay(
  private val outbox: RelayedEngineCommandOutbox,
  private val delivery: EngineCommandDelivery,
  private val batchSize: Int,
  private val leaseDuration: Duration,
  private val maxAttempts: Int,
  private val initialBackoff: Duration,
  private val clock: Clock = Clock.systemUTC(),
) {

  fun relay() {
    repeat(batchSize) {
      val entry = outbox.claimNext(clock.instant().plus(leaseDuration)) ?: return
      process(entry)
    }
  }

  private fun process(entry: OutboxEntry) {
    val command = try {
      entry.command()
    } catch (e: Exception) {
      markDead(entry, e)
      return
    }
    val outcome = delivery.deliver(command)
    when (outcome.result) {
      Result.DELIVERED -> outbox.delete(entry.id)
      Result.UNAVAILABLE -> reschedule(entry, entry.rejections, outcome.failure)
      Result.REJECTED ->
        if (entry.rejections + 1 >= maxAttempts) markDead(entry, outcome.failure) else reschedule(entry, entry.rejections + 1, outcome.failure)
    }
  }

  private fun reschedule(entry: OutboxEntry, rejections: Int, failure: Exception?) {
    val attempts = entry.attempts + 1
    logger.warn { "PROCESS-ENGINE-C7-REMOTE-122: delivery of outbox entry ${entry.id} failed, attempt $attempts: ${failure?.message}" }
    outbox.reschedule(entry.id, attempts, rejections, clock.instant().plus(backoff(attempts)), failure?.message)
  }

  private fun markDead(entry: OutboxEntry, failure: Exception?) {
    logger.error(failure) { "PROCESS-ENGINE-C7-REMOTE-123: giving up delivery of outbox entry ${entry.id}." }
    outbox.markDead(entry.id, failure?.message)
  }

  private fun backoff(attempts: Int): Duration =
    initialBackoff.multipliedBy(1L shl (attempts - 1).coerceIn(0, 20)).coerceAtMost(MAX_BACKOFF)
}
