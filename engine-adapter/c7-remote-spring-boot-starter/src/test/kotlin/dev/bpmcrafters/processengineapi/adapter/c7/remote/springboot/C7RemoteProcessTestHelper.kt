package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.toProcessInformation
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.ServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.UserTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.RefreshableDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.subscribe.SubscribingServiceTaskDelivery
import dev.bpmcrafters.processengineapi.decision.EvaluateDecisionApi
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.process.ProcessInformation
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.test.ProcessTestHelper
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.camunda.community.rest.client.model.ActivityInstanceDto

class C7RemoteProcessTestHelper(
  private val startProcessApi: StartProcessApi,
  private val userTaskDelivery: UserTaskDelivery,
  private val serviceTaskDelivery: ServiceTaskDelivery,
  private val taskSubscriptionApi: TaskSubscriptionApi,
  private val userTaskCompletionApi: UserTaskCompletionApi,
  private val serviceTaskCompletionApi: ServiceTaskCompletionApi,
  private val subscriptionRepository: SubscriptionRepository,
  private val processInstanceApiClient: ProcessInstanceApiClient,
  private val evaluateDecisionApi: EvaluateDecisionApi,
) : ProcessTestHelper {

  override fun getStartProcessApi(): StartProcessApi = startProcessApi
  override fun getTaskSubscriptionApi(): TaskSubscriptionApi = taskSubscriptionApi
  override fun getUserTaskCompletionApi(): UserTaskCompletionApi = userTaskCompletionApi
  override fun getServiceTaskCompletionApi(): ServiceTaskCompletionApi = serviceTaskCompletionApi
  override fun getEvaluateDecisionApi(): EvaluateDecisionApi = evaluateDecisionApi
  override fun triggerPullingUserTaskDeliveryManually() = userTaskDelivery.refresh()

  override fun subscribeForUserTasks() {
    TODO("Not yet implemented")
  }

  override fun triggerExternalTaskDeliveryManually() {
    if (serviceTaskDelivery is SubscribingServiceTaskDelivery) {
      serviceTaskDelivery.subscribe()
    } else if (serviceTaskDelivery is RefreshableDelivery) {
      serviceTaskDelivery.refresh()
    }
  }

  override fun getProcessInformation(instanceId: String): ProcessInformation =
    processInstanceApiClient
      .getProcessInstance(instanceId)
      .body!!.toProcessInformation()


  override fun getActiveElements(instanceId: String): Collection<String> {
    val tree = processInstanceApiClient.getActivityInstanceTree(instanceId).body ?: return emptyList()
    return tree.collectActiveElements()
  }

  /**
   * Flattens the activity instance tree into the element ids that currently hold a token.
   * The root node represents the process instance itself and is therefore skipped; only the
   * descendant activity instances (and transition instances, i.e. async-before/after tokens) count.
   */
  private fun ActivityInstanceDto.collectActiveElements(isRoot: Boolean = true): List<String> {
    val children = (childActivityInstances ?: emptyList()).flatMap { it.collectActiveElements(isRoot = false) }
    val transitions = (childTransitionInstances ?: emptyList()).mapNotNull { it.activityId }
    return if (isRoot) children + transitions else listOf(activityId) + children + transitions
  }

  override fun clearAllSubscriptions() {
    (subscriptionRepository as InMemSubscriptionRepository).deleteAllTaskSubscriptions()
    if (serviceTaskDelivery is SubscribingServiceTaskDelivery) {

      serviceTaskDelivery.unsubscribe()
    }
  }

}
