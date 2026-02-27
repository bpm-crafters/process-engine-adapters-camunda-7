package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class PlatformThreadingCondition : Condition {

  override fun matches(
    context: ConditionContext,
    metadata: AnnotatedTypeMetadata
  ): Boolean {
    return !VirtualThreadingCondition().matches(context, metadata)
  }

}
