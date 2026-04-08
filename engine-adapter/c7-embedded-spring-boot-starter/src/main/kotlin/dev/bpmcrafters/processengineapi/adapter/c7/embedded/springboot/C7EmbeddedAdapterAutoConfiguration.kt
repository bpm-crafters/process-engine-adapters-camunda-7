package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.correlation.CorrelationApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.correlation.SignalApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.decision.EvaluateDecisionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.deploy.DeploymentApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.StartProcessApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.schedule.DefaultPullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion.C7ServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion.C7UserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.NoOpPullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.modification.C7UserTaskModificationApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.subscription.C7TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import dev.bpmcrafters.processengineapi.decision.EvaluateDecisionApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskModificationApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.toolisticon.spring.condition.ConditionalOnMissingQualifiedBean
import jakarta.annotation.PostConstruct
import org.camunda.bpm.engine.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

@Configuration
@EnableConfigurationProperties(value = [C7EmbeddedAdapterProperties::class])
@Conditional(C7EmbeddedAdapterEnabledCondition::class)
class C7EmbeddedAdapterAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-200: Configuration of services applied." }
  }

  @Bean
  @ConditionalOnMissingBean
  fun engineCommandExecutor(): EngineCommandExecutor = EngineCommandExecutor()

  @Bean("c7embedded-start-process-api")
  @Qualifier("c7embedded-start-process-api")
  fun startProcessApi(
    runtimeService: RuntimeService,
    repositoryService: RepositoryService,
    commandExecutor: EngineCommandExecutor,
  ): StartProcessApi = StartProcessApiImpl(
    runtimeService = runtimeService,
    repositoryService = repositoryService,
    commandExecutor = commandExecutor,
  )

  @Bean("c7embedded-task-subscription-api")
  @Qualifier("c7embedded-task-subscription-api")
  fun taskSubscriptionApi(subscriptionRepository: SubscriptionRepository): TaskSubscriptionApi = C7TaskSubscriptionApiImpl(
    subscriptionRepository = subscriptionRepository
  )

  @Bean("c7embedded-correlation-api")
  @Qualifier("c7embedded-correlation-api")
  fun correlationApi(
    runtimeService: RuntimeService,
    commandExecutor: EngineCommandExecutor,
  ): CorrelationApi = CorrelationApiImpl(
    runtimeService = runtimeService,
    commandExecutor = commandExecutor,
  )

  @Bean("c7embedded-signal-api")
  @Qualifier("c7embedded-signal-api")
  fun signalApi(
    runtimeService: RuntimeService,
    commandExecutor: EngineCommandExecutor,
  ): SignalApi = SignalApiImpl(
    runtimeService = runtimeService,
    commandExecutor = commandExecutor,
  )

  @Bean("c7embedded-deployment-api")
  @Qualifier("c7embedded-deployment-api")
  fun deploymentApi(
    repositoryService: RepositoryService,
    commandExecutor: EngineCommandExecutor,
  ): DeploymentApi = DeploymentApiImpl(
    repositoryService = repositoryService,
    commandExecutor = commandExecutor,
  )

  @Bean("c7embedded-user-task-modification-api")
  @Qualifier("c7embedded-user-task-modification-api")
  fun userTaskModificationApi(
    taskService: TaskService,
    commandExecutor: EngineCommandExecutor,
  ): UserTaskModificationApi = C7UserTaskModificationApiImpl(
    taskService = taskService,
    commandExecutor = commandExecutor,
  )

  @Bean("c7embedded-evaluate-decision-api")
  @Qualifier("c7embedded-evaluate-decision-api")
  fun evaluateDecisionApi(
    decisionService: DecisionService,
    objectMapper: ObjectMapper,
    commandExecutor: EngineCommandExecutor
  ): EvaluateDecisionApi = EvaluateDecisionApiImpl(
    decisionService = decisionService,
    objectMapper = objectMapper,
    commandExecutor = commandExecutor
  )

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(MeterRegistry::class)
  fun defaultEmbeddedPullServiceTaskDeliveryMetrics(registry: MeterRegistry): EmbeddedPullServiceTaskDeliveryMetrics =
    DefaultPullServiceTaskDeliveryMetrics(registry)

  @Bean
  @ConditionalOnMissingBean(EmbeddedPullServiceTaskDeliveryMetrics::class, MeterRegistry::class)
  fun noOpEmbeddedPullServiceTaskDeliveryMetrics(): EmbeddedPullServiceTaskDeliveryMetrics =
    NoOpPullServiceTaskDeliveryMetrics()


  @Bean
  @ConditionalOnMissingBean
  fun subscriptionRepository(): SubscriptionRepository = InMemSubscriptionRepository()

  @Bean("c7embedded-failure-retry-supplier")
  @Qualifier("c7embedded-failure-retry-supplier")
  @ConditionalOnMissingBean
  fun defaultFailureRetrySupplier(c7AdapterProperties: C7EmbeddedAdapterProperties): FailureRetrySupplier {
    return LinearMemoryFailureRetrySupplier(
      retry = c7AdapterProperties.serviceTasks.retries,
      retryTimeout = c7AdapterProperties.serviceTasks.retryTimeoutInSeconds
    )
  }

  @Bean("c7embedded-service-task-completion-api")
  @Qualifier("c7embedded-service-task-completion-api")
  fun serviceTaskCompletionApi(
    externalTaskService: ExternalTaskService,
    subscriptionRepository: SubscriptionRepository,
    c7AdapterProperties: C7EmbeddedAdapterProperties,
    @Qualifier("c7embedded-failure-retry-supplier")
    failureRetrySupplier: FailureRetrySupplier,
    commandExecutor: EngineCommandExecutor
  ): ServiceTaskCompletionApi =
    C7ServiceTaskCompletionApiImpl(
      workerId = c7AdapterProperties.serviceTasks.workerId,
      externalTaskService = externalTaskService,
      subscriptionRepository = subscriptionRepository,
      failureRetrySupplier = failureRetrySupplier,
      commandExecutor = commandExecutor,

    )

  @Bean("c7embedded-user-task-completion-api")
  @Qualifier("c7embedded-user-task-completion-api")
  fun userTaskCompletionApi(
    taskService: TaskService,
    subscriptionRepository: SubscriptionRepository,
    commandExecutor: EngineCommandExecutor
  ): UserTaskCompletionApi =
    C7UserTaskCompletionApiImpl(
      taskService = taskService,
      subscriptionRepository = subscriptionRepository,
      commandExecutor = commandExecutor,
    )

  /**
   * Creates a default fixed thread pool for 10 threads used for process engine worker executions.
   * This one is used for pull-strategies only.
   */
  @Bean("c7embedded-service-task-worker-executor")
  @ConditionalOnMissingQualifiedBean(beanClass = ExecutorService::class, qualifier = "c7embedded-service-task-worker-executor")
  @Qualifier("c7embedded-service-task-worker-executor")
  fun serviceTaskWorkerExecutor(): ExecutorService = Executors.newFixedThreadPool(10)

  /**
   * Creates a default fixed thread pool for 10 threads used for process engine worker executions.
   * This one is used for pull-strategies and async event listener execution.
   */
  @Bean("c7embedded-user-task-worker-executor")
  @ConditionalOnMissingQualifiedBean(beanClass = ExecutorService::class, qualifier = "c7embedded-user-task-worker-executor")
  @Qualifier("c7embedded-user-task-worker-executor")
  fun userTaskWorkerExecutor(): ExecutorService = Executors.newFixedThreadPool(10)

}
