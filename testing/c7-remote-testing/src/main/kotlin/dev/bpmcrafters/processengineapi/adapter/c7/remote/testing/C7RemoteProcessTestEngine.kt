package dev.bpmcrafters.processengineapi.adapter.c7.remote.testing

/**
 * Configures the engine used by the remote process-test provider.
 *
 * When [url] is blank, the provider starts [image] in Testcontainers. A supplied
 * URL is used as-is and is never stopped by the provider.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class C7RemoteProcessTestEngine(
  val url: String = "",
  val image: String = DEFAULT_IMAGE,
) {
  companion object {
    const val DEFAULT_IMAGE = "camunda/camunda-bpm-platform:run-7.24.0"
  }
}
