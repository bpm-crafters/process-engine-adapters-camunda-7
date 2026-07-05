package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.system.JavaVersion
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.type.AnnotationMetadata

/**
 * Locks down the local threading conditions that replaced Spring Boot's removed threading enum
 * based conditions for the embedded scheduler.
 */
class C7EmbeddedThreadingConditionTest {

  private val metadata = AnnotationMetadata.introspect(C7EmbeddedThreadingConditionTest::class.java)

  @Test
  fun `uses platform threading by default`() {
    val context = conditionContext()

    assertThat(VirtualThreadingCondition().matches(context, metadata)).isFalse()
    assertThat(PlatformThreadingCondition().matches(context, metadata)).isTrue()
  }

  @Test
  fun `uses platform threading when virtual threading is disabled`() {
    val context = conditionContext("false")

    assertThat(VirtualThreadingCondition().matches(context, metadata)).isFalse()
    assertThat(PlatformThreadingCondition().matches(context, metadata)).isTrue()
  }

  @Test
  fun `uses virtual threading when enabled on Java 21 or newer`() {
    val context = conditionContext("true")
    val expectedVirtualThreading = JavaVersion.getJavaVersion().isEqualOrNewerThan(JavaVersion.TWENTY_ONE)

    assertThat(VirtualThreadingCondition().matches(context, metadata)).isEqualTo(expectedVirtualThreading)
    assertThat(PlatformThreadingCondition().matches(context, metadata)).isEqualTo(!expectedVirtualThreading)
  }

  private fun conditionContext(virtualThreadsEnabled: String? = null): ConditionContext {
    val environment = StandardEnvironment()
    virtualThreadsEnabled?.let {
      environment.propertySources.addFirst(
        MapPropertySource("test", mapOf("spring.threads.virtual.enabled" to it))
      )
    }

    val context = mock<ConditionContext>()
    whenever(context.environment).thenReturn(environment)
    return context
  }
}
