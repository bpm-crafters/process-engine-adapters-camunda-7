package dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.EngineCommand.*
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.correlation.*
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CompletableFuture

internal class EngineCommandOutboxTest {

  private val outbox: RelayedEngineCommandOutbox = mock()
  private val service: ServiceTaskCompletionApi = mock()
  private val user: UserTaskCompletionApi = mock()
  private val correlation: CorrelationApi = mock()
  private val signal: SignalApi = mock()
  private val repository: SubscriptionRepository = mock()
  private val transaction: OutboxTransaction = mock()
  private val retrySupplier: FailureRetrySupplier = mock()
  private val enqueuer = OutboxEnqueuer(outbox, repository, transaction)
  private var status: Int? = null
  private val delivery = EngineCommandDelivery(service, user, correlation, signal, { status })
  private val relay = EngineCommandOutboxRelay(outbox, delivery, 1, Duration.ofSeconds(60), 2, Duration.ofSeconds(1))
  private val completeTask = CompleteServiceTask("task", emptyMap())
  private val message = CorrelateMessage("message", "key", "variable", emptyMap(), emptyMap())
  private val sendSignal = SendSignal("signal", emptyMap(), emptyMap())
  private val done = CompletableFuture.completedFuture(Empty)

  private fun inTransaction() = whenever(transaction.isActive()).thenReturn(true)

  private fun claimed(command: EngineCommand, attempts: Int = 0, rejections: Int = 0) =
    whenever(outbox.claimNext(any())).thenReturn(OutboxEntry("id", attempts, rejections) { command })

  private fun engineFails(httpStatus: Int?) {
    status = httpStatus
    val failed = CompletableFuture.failedFuture<Empty>(RuntimeException("boom"))
    whenever(service.completeTask(any())).thenReturn(failed)
    whenever(correlation.correlateMessage(any())).thenReturn(failed)
    whenever(signal.sendSignal(any())).thenReturn(failed)
  }

  @Test
  fun `service completion is enqueued and terminated after commit`() {
    inTransaction()

    OutboxServiceTaskCompletionApiImpl(service, enqueuer, retrySupplier).completeTask(CompleteTaskCmd("task") { emptyMap() })

    verify(outbox).enqueue(completeTask)
    verify(transaction).afterCommit(any())
    verifyNoInteractions(service, repository)
  }

  @Test
  fun `no transaction calls delegate`() {
    val cmd = CompleteTaskCmd("task") { emptyMap() }

    OutboxServiceTaskCompletionApiImpl(service, enqueuer, retrySupplier).completeTask(cmd)

    verify(service).completeTask(cmd)
    verifyNoInteractions(outbox)
  }

  @Test
  fun `task failure keeps retries resolved at enqueue`() {
    inTransaction()
    whenever(retrySupplier.apply("task")).thenReturn(FailureRetrySupplier.FailureRetry(2, 30))

    OutboxServiceTaskCompletionApiImpl(service, enqueuer, retrySupplier).failTask(FailTaskCmd("task", "reason", null))

    verify(outbox).enqueue(FailServiceTask("task", "reason", null, 2, Duration.ofSeconds(30)))
  }

  @Test
  fun `user task error is enqueued`() {
    inTransaction()

    OutboxUserTaskCompletionApiImpl(user, enqueuer).completeTaskByError(CompleteTaskByErrorCmd("task", "code", "text") { emptyMap() })

    verify(outbox).enqueue(CompleteUserTaskByError("task", "code", "text", emptyMap()))
    verifyNoInteractions(user)
  }

  @Test
  fun `message is enqueued without termination`() {
    inTransaction()
    whenever(correlation.ensureSupported(any())).thenReturn(emptyMap())

    OutboxCorrelationApiImpl(correlation, enqueuer).correlateMessage(
      CorrelateMessageCmd("message", emptyMap<String, Any?>(), Correlation.withKey("key").withVariable("variable"))
    )

    verify(outbox).enqueue(message)
    verify(transaction, never()).afterCommit(any())
  }

  @Test
  fun `signal is enqueued`() {
    inTransaction()
    whenever(signal.ensureSupported(any())).thenReturn(emptyMap())

    OutboxSignalApiImpl(signal, enqueuer).sendSignal(SendSignalCmd("signal", emptyMap()))

    verify(outbox).enqueue(sendSignal)
  }

  @Test
  fun `delivered command is deleted`() {
    claimed(sendSignal)
    whenever(signal.sendSignal(any())).thenReturn(done)

    relay.relay()

    verify(outbox).delete("id")
  }

  @Test
  fun `missing task counts as delivered`() {
    claimed(completeTask)
    engineFails(404)

    relay.relay()

    verify(outbox).delete("id")
  }

  @Test
  fun `404 on message is no engine answer and retries without counting`() {
    claimed(message, rejections = 1)
    engineFails(404)

    relay.relay()

    verify(outbox).reschedule(eq("id"), eq(1), eq(1), any(), any())
  }

  @Test
  fun `unavailable engine never goes dead`() {
    claimed(completeTask, attempts = 1000, rejections = 1)
    engineFails(503)

    relay.relay()

    verify(outbox, never()).markDead(any(), anyOrNull())
    verify(outbox).reschedule(eq("id"), eq(1001), eq(1), any(), any())
  }

  @Test
  fun `connection failure has no status and retries`() {
    claimed(sendSignal, rejections = 5)
    whenever(signal.sendSignal(any())).thenReturn(CompletableFuture.failedFuture(RuntimeException(IOException("refused"))))

    relay.relay()

    verify(outbox, never()).markDead(any(), anyOrNull())
  }

  @Test
  fun `failure without status and connection problem counts as rejection`() {
    claimed(message)
    engineFails(null)

    relay.relay()

    verify(outbox).reschedule(eq("id"), eq(1), eq(1), any(), any())
  }

  @Test
  fun `rejection counts up`() {
    claimed(message)
    engineFails(500)

    relay.relay()

    verify(outbox).reschedule(eq("id"), eq(1), eq(1), any(), any())
  }

  @Test
  fun `rejection goes dead at max attempts`() {
    claimed(message, rejections = 1)
    engineFails(400)

    relay.relay()

    assertThat(mockingDetails(outbox).invocations.map { it.method.name }).contains("markDead")
  }

  @Test
  fun `unreadable command goes dead`() {
    whenever(outbox.claimNext(any())).thenReturn(OutboxEntry("id", 0, 0) { error("corrupt") })

    relay.relay()

    verify(outbox).markDead("id", "corrupt")
  }
}
