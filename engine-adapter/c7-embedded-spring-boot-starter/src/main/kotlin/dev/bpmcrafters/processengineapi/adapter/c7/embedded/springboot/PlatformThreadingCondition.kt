package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Selects the platform-thread scheduler whenever the virtual-thread condition is not satisfied.
 *
 * Keeping this as the inverse condition preserves the previous default behaviour without depending
 * on Spring Boot's removed `Threading.PLATFORM` enum value.
 */
class PlatformThreadingCondition : Condition {

  override fun matches(
    context: ConditionContext,
    metadata: AnnotatedTypeMetadata
  ): Boolean {
    return !VirtualThreadingCondition().matches(context, metadata)
  }

}
