package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import org.springframework.boot.system.JavaVersion
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Boot-3/Boot-4 neutral replacement for Spring Boot's former `ConditionalOnThreading(VIRTUAL)`.
 *
 * Spring may expose the virtual-thread property on older JVMs, but the scheduler can only use
 * virtual threads safely on Java 21 or newer.
 */
class VirtualThreadingCondition : Condition {

  override fun matches(
    context: ConditionContext,
    metadata: AnnotatedTypeMetadata
  ): Boolean {
    return context.environment.getProperty("spring.threads.virtual.enabled", java.lang.Boolean.TYPE, false)
      && JavaVersion.getJavaVersion().isEqualOrNewerThan(JavaVersion.TWENTY_ONE)
  }

}
