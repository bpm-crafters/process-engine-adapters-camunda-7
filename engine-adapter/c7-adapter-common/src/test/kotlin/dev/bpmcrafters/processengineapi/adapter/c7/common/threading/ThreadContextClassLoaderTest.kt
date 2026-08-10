package dev.bpmcrafters.processengineapi.adapter.c7.common.threading

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

class ThreadContextClassLoaderTest {

  @Test
  fun `callback uses context class loader and restores original class loader`() {
    val thread = Thread.currentThread()
    val originalContextClassLoader = thread.contextClassLoader
    val workerContextClassLoader = isolatedClassLoader()

    try {
      thread.contextClassLoader = workerContextClassLoader

      val callbackContextClassLoader = withThreadContextClassLoader(this) {
        Thread.currentThread().contextClassLoader
      }

      assertThat(callbackContextClassLoader).isSameAs(javaClass.classLoader)
      assertThat(thread.contextClassLoader).isSameAs(workerContextClassLoader)
    } finally {
      thread.contextClassLoader = originalContextClassLoader
    }
  }

  @Test
  fun `callback restores original class loader after exception`() {
    val thread = Thread.currentThread()
    val originalContextClassLoader = thread.contextClassLoader
    val workerContextClassLoader = isolatedClassLoader()

    try {
      thread.contextClassLoader = workerContextClassLoader

      assertThatThrownBy {
        withThreadContextClassLoader(this) {
          throw IllegalStateException("expected")
        }
      }.isInstanceOf(IllegalStateException::class.java)

      assertThat(thread.contextClassLoader).isSameAs(workerContextClassLoader)
    } finally {
      thread.contextClassLoader = originalContextClassLoader
    }
  }

  @Test
  fun `thread factory assigns configured context class loader`() {
    val targetContextClassLoader = isolatedClassLoader()
    val observedContextClassLoader = AtomicReference<ClassLoader>()
    val thread = ThreadContextClassLoaderThreadFactory(targetContextClassLoader).newThread {
      observedContextClassLoader.set(Thread.currentThread().contextClassLoader)
    }

    thread.start()
    thread.join()

    assertThat(observedContextClassLoader.get()).isSameAs(targetContextClassLoader)
  }

  private fun isolatedClassLoader(): ClassLoader = object : ClassLoader(null) {}
}
