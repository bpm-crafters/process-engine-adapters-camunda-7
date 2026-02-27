package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import org.springframework.boot.system.JavaVersion
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class VirtualThreadingCondition : Condition {

  override fun matches(
    context: ConditionContext,
    metadata: AnnotatedTypeMetadata
  ): Boolean {
    return context.environment.getProperty("spring.threads.virtual.enabled", java.lang.Boolean.TYPE, false)
      && JavaVersion.getJavaVersion().isEqualOrNewerThan(JavaVersion.TWENTY_ONE)
  }

}
