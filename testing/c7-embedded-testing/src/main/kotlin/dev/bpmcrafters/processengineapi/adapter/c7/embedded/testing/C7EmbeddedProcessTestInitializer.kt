package dev.bpmcrafters.processengineapi.adapter.c7.embedded.testing

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.correlation.CorrelationApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.correlation.SignalApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.deploy.DeploymentApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.StartProcessApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion.C7ServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion.C7UserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.modification.C7UserTaskModificationApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.subscription.C7TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.testing.bootstrap.ProcessTestInitializationRequest
import dev.bpmcrafters.processengineapi.testing.bootstrap.ProcessTestInitializer
import org.camunda.bpm.engine.ProcessEngineConfiguration
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration
import org.camunda.bpm.engine.test.mock.MockExpressionManager

class C7EmbeddedProcessTestInitializer : ProcessTestInitializer {

  companion object {
    const val QUALIFIER = "c7embedded"
  }

  override fun qualifier() = QUALIFIER

  override fun initialize(request: ProcessTestInitializationRequest): C7EmbeddedProcessTestContext {
    val engine = object : StandaloneInMemProcessEngineConfiguration() {
      init {
        history = ProcessEngineConfiguration.HISTORY_AUDIT
        databaseSchemaUpdate = ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE
        jobExecutorActivate = false
        expressionManager = MockExpressionManager()
      }
    }.buildProcessEngine()
    val commandExecutor = EngineCommandExecutor { it.run() }
    val subscriptions = InMemSubscriptionRepository()
    val metadata = CachingProcessDefinitionMetaDataResolver(engine.repositoryService)
    val workerId = "process-test-${request.testClass.simpleName}"

    return C7EmbeddedProcessTestContext(
      processEngine = engine,
      runtimeApis = C7EmbeddedProcessTestRuntimeApis(
        startProcessApi = StartProcessApiImpl(engine.runtimeService, engine.repositoryService, commandExecutor, metadata),
        taskSubscriptionApi = C7TaskSubscriptionApiImpl(subscriptions),
        userTaskCompletionApi = C7UserTaskCompletionApiImpl(engine.taskService, subscriptions, commandExecutor),
        userTaskModificationApi = C7UserTaskModificationApiImpl(engine.taskService, commandExecutor),
        serviceTaskCompletionApi = C7ServiceTaskCompletionApiImpl(
          workerId, engine.externalTaskService, subscriptions, LinearMemoryFailureRetrySupplier(3, 1), commandExecutor
        ),
        correlationApi = CorrelationApiImpl(engine.runtimeService, commandExecutor),
        signalApi = SignalApiImpl(engine.runtimeService, commandExecutor),
        deploymentApi = DeploymentApiImpl(engine.repositoryService, commandExecutor),
      ),
      metadata = metadata,
      workerId = workerId,
    )
  }
}
