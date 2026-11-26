package dev.bpmcrafters.processengineapi.test

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory

fun captureAdapterLogs(run: () -> Unit): List<ILoggingEvent> {
  val logger = LoggerFactory.getLogger("dev.bpmcrafters.processengineapi.adapter.c7") as Logger
  val previousLevel = logger.level
  val appender = ListAppender<ILoggingEvent>().also { it.start() }
  logger.level = Level.DEBUG
  logger.addAppender(appender)
  return try {
    run()
    appender.list.toList()
  } finally {
    logger.detachAppender(appender)
    logger.level = previousLevel
    appender.stop()
  }
}

fun assertMissingTaskLoggedOnce(events: List<ILoggingEvent>, taskId: String) {
  val missingTaskMessages = Regex(
    """\btask\b.*\b${Regex.escape(taskId)}\b|\b${Regex.escape(taskId)}\b.*\btask\b""",
    RegexOption.IGNORE_CASE
  )
  val missing = Regex("not found|no longer|does not exist|gone|missing|vanished", RegexOption.IGNORE_CASE)
  val taskEvents = events.filter { missingTaskMessages.containsMatchIn(it.formattedMessage) }
  val missingTaskEvents = taskEvents.filter { missing.containsMatchIn(it.formattedMessage) }

  assertThat(missingTaskEvents.map { it.level }).containsExactly(Level.DEBUG)
  assertThat(taskEvents.filter { it.level.isGreaterOrEqual(Level.WARN) }.map { it.formattedMessage }).isEmpty()
}
