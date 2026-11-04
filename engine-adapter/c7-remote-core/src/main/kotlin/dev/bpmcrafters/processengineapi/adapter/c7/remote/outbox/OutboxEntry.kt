package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

/**
 * Claimed command. It is a supplier, because reading it may fail for a single entry only.
 */
class OutboxEntry(
  val id: String,
  val attempts: Int,
  val rejections: Int,
  val command: () -> EngineCommand,
)
