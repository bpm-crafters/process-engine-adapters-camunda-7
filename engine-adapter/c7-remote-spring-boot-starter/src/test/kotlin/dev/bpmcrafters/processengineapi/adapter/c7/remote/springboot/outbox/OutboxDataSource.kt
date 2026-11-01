package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.outbox

import org.h2.jdbcx.JdbcDataSource
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

internal fun outboxDataSource(): DataSource = JdbcDataSource().apply {
  setURL("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
}.also {
  JdbcClient.create(it).sql(
    """
    CREATE TABLE c7remote_engine_outbox (
      id VARCHAR(36) PRIMARY KEY, type VARCHAR(32) NOT NULL, task_id VARCHAR(64), payload BLOB NOT NULL,
      status VARCHAR(16) NOT NULL, attempts INT NOT NULL, rejections INT NOT NULL, next_attempt_at TIMESTAMP NOT NULL,
      lease_until TIMESTAMP, last_error VARCHAR(4000))
    """
  ).update()
}
