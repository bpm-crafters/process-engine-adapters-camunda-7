package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

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
