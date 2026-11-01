package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.outbox

import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.EngineCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

internal class JdbcEngineCommandOutboxTest {

  private val dataSource = outboxDataSource()
  private val outbox = JdbcEngineCommandOutbox(dataSource)
  private val lease = Instant.now().plusSeconds(60)
  private val complete = EngineCommand.CompleteServiceTask("task", mapOf("number" to 1L, "none" to null))
  private val message = EngineCommand.CorrelateMessage("message", "key", "variable", mapOf("r" to "1"), mapOf("a" to 1))

  @Test
  fun `all commands survive the round trip`() {
    listOf(
      complete,
      message,
      EngineCommand.SendSignal("signal", emptyMap(), emptyMap()),
      EngineCommand.FailServiceTask("task", "reason", null, 2, Duration.ofSeconds(3)),
      EngineCommand.CompleteUserTaskByError("task", "code", null, emptyMap()),
    ).forEach {
      outbox.enqueue(it)
      assertThat(outbox.claimNext(lease)!!.command()).isEqualTo(it)
    }
  }

  @Test
  fun `second relay does not get a leased entry`() {
    outbox.enqueue(complete)

    assertThat(outbox.claimNext(lease)).isNotNull
    assertThat(outbox.claimNext(lease)).isNull()
  }

  @Test
  fun `expired lease allows reclaiming after a crash`() {
    outbox.enqueue(complete)
    outbox.claimNext(Instant.now().minusSeconds(1))

    assertThat(outbox.claimNext(lease)).isNotNull
  }

  @Test
  fun `reschedule stores counters and delays the entry`() {
    outbox.enqueue(complete)
    val id = outbox.claimNext(lease)!!.id

    outbox.reschedule(id, 3, 1, Instant.now().plusSeconds(60), "error")
    assertThat(outbox.claimNext(lease)).isNull()

    outbox.reschedule(id, 3, 1, Instant.now().minusSeconds(1), null)
    val entry = outbox.claimNext(lease)!!
    assertThat(entry.attempts).isEqualTo(3)
    assertThat(entry.rejections).isEqualTo(1)
  }

  @Test
  fun `only undead task commands are pending`() {
    outbox.enqueue(message)
    outbox.enqueue(complete)
    assertThat(outbox.pendingTaskIds(listOf("task", "message"))).containsExactly("task")

    repeat(2) { outbox.claimNext(lease)?.let { outbox.markDead(it.id, null) } }

    assertThat(outbox.pendingTaskIds(listOf("task"))).isEmpty()
  }

  @Test
  fun `filter rejects payload classes`() {
    outbox.enqueue(complete)

    val entry = JdbcEngineCommandOutbox(dataSource, "!java.lang.Long").claimNext(lease)!!

    assertThat(runCatching { entry.command() }.isFailure).isTrue()
  }
}
