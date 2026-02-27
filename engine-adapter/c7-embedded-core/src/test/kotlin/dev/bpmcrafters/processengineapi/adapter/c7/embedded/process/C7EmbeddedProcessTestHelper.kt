package dev.bpmcrafters.processengineapi.adapter.c7.embedded.process

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.decision.EvaluateDecisionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion.C7ServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion.C7UserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullUserTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.NoOpPullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.subscription.C7TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.decision.EvaluateDecisionApi
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.process.ProcessInformation
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.test.ProcessTestHelper
import org.camunda.bpm.engine.ProcessEngine
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


const val WORKER_ID = "execute-action-external"

class C7EmbeddedProcessTestHelper(private val processEngine: ProcessEngine) : ProcessTestHelper {

  private var subscriptionRepository: InMemSubscriptionRepository = InMemSubscriptionRepository()

  private var embeddedPullUserTaskDelivery: EmbeddedPullUserTaskDelivery = EmbeddedPullUserTaskDelivery(
    taskService = processEngine.taskService,
    subscriptionRepository = subscriptionRepository,
    processDefinitionMetaDataResolver = CachingProcessDefinitionMetaDataResolver(repositoryService = processEngine.repositoryService),
    executorService = Executors.newFixedThreadPool(3)
  )

  private var embeddedPullExternalTaskDelivery: EmbeddedPullServiceTaskDelivery = EmbeddedPullServiceTaskDelivery(
    externalTaskService = processEngine.externalTaskService,
    workerId = WORKER_ID,
    subscriptionRepository = subscriptionRepository,
    maxTasks = 100,
    lockDurationInSeconds = 10L,
    retryTimeoutInSeconds = 10L,
    retries = 3,
    executor = ThreadPoolExecutor(
      3,  // corePoolSize
      8,  // maximumPoolSize
      60L,  // keepAliveTime
      TimeUnit.SECONDS,  // time unit
      LinkedBlockingQueue(100),  // work queue
      Executors.defaultThreadFactory(),
      ThreadPoolExecutor.AbortPolicy() // rejection handler
    ),
    metrics = NoOpPullServiceTaskDeliveryMetrics()
  )

  override fun getStartProcessApi(): StartProcessApi = StartProcessApiImpl(
    runtimeService = processEngine.runtimeService,
    repositoryService = processEngine.repositoryService,
    commandExecutor = EngineCommandExecutor { it.run() },
  )

  override fun getTaskSubscriptionApi(): TaskSubscriptionApi = C7TaskSubscriptionApiImpl(
    subscriptionRepository = subscriptionRepository
  )

  override fun getUserTaskCompletionApi(): UserTaskCompletionApi = C7UserTaskCompletionApiImpl(
    taskService = processEngine.taskService,
    subscriptionRepository = subscriptionRepository,
    commandExecutor = EngineCommandExecutor(),
  )

  override fun getEvaluateDecisionApi(): EvaluateDecisionApi = EvaluateDecisionApiImpl(
    decisionService = processEngine.decisionService,
    objectMapper = jacksonObjectMapper(),
    commandExecutor = EngineCommandExecutor()
  )

  override fun getServiceTaskCompletionApi(): ServiceTaskCompletionApi = C7ServiceTaskCompletionApiImpl(
    workerId = WORKER_ID,
    externalTaskService = processEngine.externalTaskService,
    subscriptionRepository = subscriptionRepository,
    failureRetrySupplier = LinearMemoryFailureRetrySupplier(
      retry = 1,
      retryTimeout = 10
    ),
    commandExecutor = EngineCommandExecutor()
  )

  override fun triggerPullingUserTaskDeliveryManually() = embeddedPullUserTaskDelivery.refresh()

  override fun subscribeForUserTasks() {
    TODO("Not yet implemented")
  }

  override fun triggerExternalTaskDeliveryManually() = embeddedPullExternalTaskDelivery.refresh()

  override fun getProcessInformation(instanceId: String): ProcessInformation =
    processEngine.runtimeService
      .createProcessInstanceQuery()
      .processInstanceId(instanceId)
      .singleResult()
      .toProcessInformation()

  override fun clearAllSubscriptions() = subscriptionRepository.deleteAllTaskSubscriptions()


}
