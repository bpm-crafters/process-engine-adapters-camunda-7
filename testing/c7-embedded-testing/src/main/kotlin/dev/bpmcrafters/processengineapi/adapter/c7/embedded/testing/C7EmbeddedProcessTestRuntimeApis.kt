package dev.bpmcrafters.processengineapi.adapter.c7.embedded.testing

import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskModificationApi
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestRuntimeApis

class C7EmbeddedProcessTestRuntimeApis(
  private val startProcessApi: StartProcessApi, private val taskSubscriptionApi: TaskSubscriptionApi,
  private val userTaskCompletionApi: UserTaskCompletionApi, private val userTaskModificationApi: UserTaskModificationApi,
  private val serviceTaskCompletionApi: ServiceTaskCompletionApi, private val correlationApi: CorrelationApi,
  private val signalApi: SignalApi, private val deploymentApi: DeploymentApi,
) : ProcessTestRuntimeApis {
  override fun startProcessApi() = startProcessApi
  override fun taskSubscriptionApi() = taskSubscriptionApi
  override fun userTaskCompletionApi() = userTaskCompletionApi
  override fun userTaskModificationApi() = userTaskModificationApi
  override fun serviceTaskCompletionApi() = serviceTaskCompletionApi
  override fun correlationApi() = correlationApi
  override fun signalApi() = signalApi
  override fun deploymentApi() = deploymentApi
}
