package dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.subscription

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

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }
}
