package dev.bpmcrafters.processengineapi.adapter.c7.common.threading

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Runs [action] with the ClassLoader of [context] as the current thread's context ClassLoader.
 */
fun <T> withThreadContextClassLoader(context: Any, action: () -> T): T {
  val contextClassLoader = context.javaClass.classLoader ?: return action()
  val thread = Thread.currentThread()
  val originalContextClassLoader = thread.contextClassLoader
  if (originalContextClassLoader === contextClassLoader) {
    return action()
  }

  thread.contextClassLoader = contextClassLoader
  return try {
    action()
  } finally {
    thread.contextClassLoader = originalContextClassLoader
  }
}

/**
 * Delegates thread creation and pins the created thread's context ClassLoader.
 */
class ThreadContextClassLoaderThreadFactory(
  private val contextClassLoader: ClassLoader,
  private val delegate: ThreadFactory = Executors.defaultThreadFactory(),
) : ThreadFactory {
  override fun newThread(runnable: Runnable): Thread = delegate.newThread(runnable).apply {
    contextClassLoader = this@ThreadContextClassLoaderThreadFactory.contextClassLoader
  }
}
