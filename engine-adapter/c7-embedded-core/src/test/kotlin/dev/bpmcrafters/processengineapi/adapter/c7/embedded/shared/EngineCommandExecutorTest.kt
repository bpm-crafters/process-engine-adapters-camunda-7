package dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException

class EngineCommandExecutorTest {

  private val directExecutor = EngineCommandExecutor { it.run() }

  @Test
  fun `execute returns supplier result`() {
    assertThat(directExecutor.execute { 42 }.get()).isEqualTo(42)
  }

  @Test
  fun `execute completes future exceptionally on supplier failure`() {
    val future = directExecutor.execute<Int> { error("boom") }
    assertThatThrownBy { future.get() }
      .isInstanceOf(ExecutionException::class.java)
      .hasCauseInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("boom")
  }

  @Test
  fun `default executor runs supplier on a different thread`() {
    val callerThread = Thread.currentThread().name
    val executorThread = EngineCommandExecutor().execute { Thread.currentThread().name }.get()
    assertThat(executorThread).isNotEqualTo(callerThread)
  }
}