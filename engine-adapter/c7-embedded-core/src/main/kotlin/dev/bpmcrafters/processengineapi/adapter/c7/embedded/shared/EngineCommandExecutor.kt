package dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

/**
 * Controls how engine commands are dispatched.
 * By default, commands run asynchronously on [ForkJoinPool.commonPool] —
 * mirroring the behavior of a remote engine.
 *
 * This design choice was made on purpose to keep adapters portable across engine implementations
 * (embedded ↔ remote, Camunda 7 ↔ Zeebe).
 *
 * However, engine calls do not join the caller's transaction — a rollback triggered elsewhere
 * will not undo what the engine already executed, risking data inconsistencies between your service and the engine.
 *
 * Override this bean with any [Executor] to change the dispatch strategy.
 * The most common use case for this is transactional coupling via a same-thread executor:
 * ```
 * @Bean fun engineCommandExecutor() = EngineCommandExecutor { it.run() }
 * ```
 */
class EngineCommandExecutor(
  private val executor: Executor = ForkJoinPool.commonPool()
) {
  fun <T> execute(
    supplier: () -> T
  ): CompletableFuture<T> {
    return CompletableFuture.supplyAsync({ supplier() }, executor)
  }
}