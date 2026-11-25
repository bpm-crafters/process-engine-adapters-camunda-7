package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.subscription

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.impl.task.AbstractTaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.TaskExecutionObserver

/**
 * C7 task subscription implementation.
 */
class C7TaskSubscriptionApiImpl(
  subscriptionRepository: SubscriptionRepository,
  observers: List<TaskExecutionObserver> = emptyList()
) : AbstractTaskSubscriptionApiImpl(
  subscriptionRepository = subscriptionRepository,
  observers = observers
) {

  override fun getSupportedRestrictions(): Set<String> = SUPPORTED_RESTRICTIONS

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }

  private companion object {
    val SUPPORTED_RESTRICTIONS = setOf(
      CommonRestrictions.EXECUTION_ID,
      CommonRestrictions.ACTIVITY_ID,
      CommonRestrictions.TENANT_ID,
      CommonRestrictions.WITHOUT_TENANT_ID,
      CommonRestrictions.PROCESS_INSTANCE_ID,
      CommonRestrictions.PROCESS_DEFINITION_ID,
      CommonRestrictions.PROCESS_DEFINITION_KEY,
      CommonRestrictions.PROCESS_DEFINITION_VERSION_TAG,
    )
  }
}
