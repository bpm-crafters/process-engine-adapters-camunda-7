package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.outbox

import dev.bpmcrafters.processengineapi.adapter.c7.common.threading.ThreadContextClassLoaderThreadFactory
import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean

private val logger = KotlinLogging.logger {}

/**
 * Runs the relay on an own thread, as the adapter scheduler pool is sized for the pollers.
 */
class OutboxRelayLifecycle(
  private val relay: EngineCommandOutboxRelay,
  private val intervalInMillis: Long,
) : InitializingBean, DisposableBean {

  private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
    ThreadContextClassLoaderThreadFactory(OutboxRelayLifecycle::class.java.classLoader)
  )

  override fun afterPropertiesSet() {
    scheduler.scheduleWithFixedDelay(::relaySafely, intervalInMillis, intervalInMillis, TimeUnit.MILLISECONDS)
  }

  override fun destroy() {
    scheduler.shutdown()
  }

  private fun relaySafely() {
    try {
      relay.relay()
    } catch (e: Throwable) {
      logger.error(e) { "PROCESS-ENGINE-C7-REMOTE-125: outbox relay run failed: ${e.message}" }
    }
  }
}
