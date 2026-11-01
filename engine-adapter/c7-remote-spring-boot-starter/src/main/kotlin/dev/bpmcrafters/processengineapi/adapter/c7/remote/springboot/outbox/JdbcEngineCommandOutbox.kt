package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.outbox

import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.EngineCommand
import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.OutboxEntry
import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.RelayedEngineCommandOutbox
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputFilter
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.sql.Timestamp
import java.sql.Types
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

private const val STATUS_PENDING = "PENDING"
private const val STATUS_DEAD = "DEAD"
private const val MAX_ERROR_LENGTH = 4000
private const val CLAIM_CANDIDATES = 20

/**
 * Outbox on the host data source. It joins the running transaction through the shared connection.
 */
class JdbcEngineCommandOutbox(
  dataSource: DataSource,
  private val deserializationFilter: String = "",
  private val clock: Clock = Clock.systemUTC(),
) : RelayedEngineCommandOutbox {

  private val jdbcClient = JdbcClient.create(dataSource)
  private val candidateClient = JdbcClient.create(JdbcTemplate(dataSource).apply { maxRows = CLAIM_CANDIDATES })

  override fun enqueue(command: EngineCommand) {
    jdbcClient.sql(
      """
      INSERT INTO c7remote_engine_outbox (id, type, task_id, payload, status, attempts, rejections, next_attempt_at)
      VALUES (:id, :type, :taskId, :payload, :status, 0, 0, :now)
      """
    )
      .param("id", UUID.randomUUID().toString())
      .param("type", command::class.java.simpleName)
      .param("taskId", (command as? EngineCommand.TaskCommand)?.taskId, Types.VARCHAR)
      .param("payload", serialize(command))
      .param("status", STATUS_PENDING)
      .param("now", now())
      .update()
  }

  override fun pendingTaskIds(taskIds: Collection<String>): Set<String> {
    if (taskIds.isEmpty()) return emptySet()
    return jdbcClient.sql("SELECT DISTINCT task_id FROM c7remote_engine_outbox WHERE status = :status AND task_id IN (:taskIds)")
      .param("status", STATUS_PENDING)
      .param("taskIds", taskIds)
      .query(String::class.java)
      .set()
  }

  override fun claimNext(leaseUntil: Instant): OutboxEntry? =
    candidateClient.sql(
      """
      SELECT id FROM c7remote_engine_outbox
      WHERE status = :status AND next_attempt_at <= :now AND (lease_until IS NULL OR lease_until <= :now)
      ORDER BY next_attempt_at
      """
    )
      .param("status", STATUS_PENDING)
      .param("now", now())
      .query(String::class.java)
      .list()
      .firstOrNull { claim(it, leaseUntil) }
      ?.let(::load)

  override fun delete(id: String) {
    jdbcClient.sql("DELETE FROM c7remote_engine_outbox WHERE id = :id").param("id", id).update()
  }

  override fun reschedule(id: String, attempts: Int, rejections: Int, nextAttemptAt: Instant, lastError: String?) {
    jdbcClient.sql(
      """
      UPDATE c7remote_engine_outbox
      SET attempts = :attempts, rejections = :rejections, next_attempt_at = :next, lease_until = NULL, last_error = :error
      WHERE id = :id
      """
    )
      .param("attempts", attempts)
      .param("rejections", rejections)
      .param("next", Timestamp.from(nextAttemptAt))
      .param("error", lastError?.take(MAX_ERROR_LENGTH), Types.VARCHAR)
      .param("id", id)
      .update()
  }

  override fun markDead(id: String, lastError: String?) {
    jdbcClient.sql("UPDATE c7remote_engine_outbox SET status = :status, lease_until = NULL, last_error = :error WHERE id = :id")
      .param("status", STATUS_DEAD)
      .param("error", lastError?.take(MAX_ERROR_LENGTH), Types.VARCHAR)
      .param("id", id)
      .update()
  }

  private fun claim(id: String, leaseUntil: Instant): Boolean =
    jdbcClient.sql(
      """
      UPDATE c7remote_engine_outbox SET lease_until = :leaseUntil
      WHERE id = :id AND status = :status AND (lease_until IS NULL OR lease_until <= :now)
      """
    )
      .param("leaseUntil", Timestamp.from(leaseUntil))
      .param("id", id)
      .param("status", STATUS_PENDING)
      .param("now", now())
      .update() == 1

  private fun load(id: String): OutboxEntry =
    jdbcClient.sql("SELECT attempts, rejections, payload FROM c7remote_engine_outbox WHERE id = :id")
      .param("id", id)
      .query { rs, _ ->
        val bytes = rs.getBytes("payload")
        OutboxEntry(id, rs.getInt("attempts"), rs.getInt("rejections")) { deserialize(bytes) }
      }
      .single()

  private fun now() = Timestamp.from(clock.instant())

  private fun serialize(command: EngineCommand): ByteArray =
    ByteArrayOutputStream().use { bytes ->
      ObjectOutputStream(bytes).use { it.writeObject(command) }
      bytes.toByteArray()
    }

  private fun deserialize(bytes: ByteArray): EngineCommand =
    ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
      if (deserializationFilter.isNotBlank()) {
        input.objectInputFilter = ObjectInputFilter.Config.createFilter(deserializationFilter)
      }
      input.readObject() as EngineCommand
    }
}
