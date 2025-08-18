package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.remote.correlation.CorrelationApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.remote.correlation.SignalApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.remote.deploy.DeploymentApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.StartProcessApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.schedule.DefaultPullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.schedule.NoOpPullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.toolisticon.spring.condition.ConditionalOnMissingQualifiedBean
import jakarta.annotation.PostConstruct
import org.camunda.community.rest.client.api.DeploymentApiClient
import org.camunda.community.rest.client.api.MessageApiClient
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.api.SignalApiClient
import org.camunda.community.rest.variables.ValueMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

@AutoConfiguration(afterName = ["org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration"])
@EnableConfigurationProperties(value = [C7RemoteAdapterProperties::class])
@Conditional(C7RemoteAdapterEnabledCondition::class)
class C7RemoteAdapterAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-200: Configuration applied." }
  }

  @Bean("c7remote-task-subscription-api")
  @Qualifier("c7remote-task-subscription-api")
  fun taskSubscriptionApi(subscriptionRepository: SubscriptionRepository): TaskSubscriptionApi = TaskSubscriptionApiImpl(
    subscriptionRepository = subscriptionRepository
  )

  @Bean("c7remote-start-process-api")
  @Qualifier("c7remote-start-process-api")
  fun startProcessApi(
    processDefinitionApiClient: ProcessDefinitionApiClient,
    messageApiClient: MessageApiClient,
    valueMapper: ValueMapper,
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  ): StartProcessApi = StartProcessApiImpl(
    processDefinitionApiClient = processDefinitionApiClient,
    messageApiClient = messageApiClient,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    valueMapper = valueMapper
  )

  @Bean("c7remote-correlation-api")
  @Qualifier("c7remote-correlation-api")
  fun correlationApi(messageApiClient: MessageApiClient, valueMapper: ValueMapper): CorrelationApi = CorrelationApiImpl(
    messageApiClient = messageApiClient,
    valueMapper = valueMapper
  )

  @Bean("c7remote-signal-api")
  @Qualifier("c7remote-signal-api")
  fun signalApi(signalApiClient: SignalApiClient, valueMapper: ValueMapper): SignalApi = SignalApiImpl(
    signalApiClient = signalApiClient,
    valueMapper = valueMapper
  )

  @Bean("c7remote-deploy-api")
  @Qualifier("c7remote-deploy-api")
  fun deployApi(deploymentApiClient: DeploymentApiClient): DeploymentApi = DeploymentApiImpl(
    deploymentApiClient = deploymentApiClient
  )
  /**
   * Subscription Repository.
   */
  @Bean
  @ConditionalOnMissingBean
  fun subscriptionRepository(): SubscriptionRepository = InMemSubscriptionRepository()

  /**
   * Creates a default fixed thread pool used for external task worker executions.
   * This one is used for pull-strategies only.
   */
  @Bean("c7remote-service-task-worker-executor")
  @Qualifier("c7remote-service-task-worker-executor")
  @ConditionalOnMissingQualifiedBean(beanClass = ThreadPoolExecutor::class, qualifier = "c7remote-service-task-worker-executor")
  fun serviceTaskWorkerExecutor(c7AdapterProperties: C7RemoteAdapterProperties): ThreadPoolExecutor =
    ThreadPoolExecutor(
      c7AdapterProperties.serviceTasks.workerThreadPoolSize,
      c7AdapterProperties.serviceTasks.workerThreadPoolSize,
      0L, TimeUnit.MILLISECONDS,
      LinkedBlockingQueue(c7AdapterProperties.serviceTasks.workerThreadPoolQueueCapacity)
    )

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(MeterRegistry::class)
  fun defaultPullServiceTaskDeliveryMetrics(registry: MeterRegistry): PullServiceTaskDeliveryMetrics =
    DefaultPullServiceTaskDeliveryMetrics(registry)

  @Bean
  @ConditionalOnMissingBean(PullServiceTaskDeliveryMetrics::class, MeterRegistry::class)
  fun noOpPullServiceTaskDeliveryMetrics(): PullServiceTaskDeliveryMetrics =
    NoOpPullServiceTaskDeliveryMetrics()

  /**
   * Creates a default fixed thread pool for 10 threads used for process engine worker executions.
   * This one is used for pull-strategies only.
   */
  @Bean("c7remote-user-task-worker-executor")
  @Qualifier("c7remote-user-task-worker-executor")
  @ConditionalOnMissingQualifiedBean(beanClass = ExecutorService::class, qualifier = "c7remote-user-task-worker-executor")
  fun userTaskWorkerExecutor(): ExecutorService = Executors.newFixedThreadPool(10)

  /**
   * Failure retry supplier.
   */
  @Bean("c7remote-failure-retry-supplier")
  @Qualifier("c7remote-failure-retry-supplier")
  @ConditionalOnMissingBean
  fun defaultFailureRetrySupplier(c7AdapterProperties: C7RemoteAdapterProperties): FailureRetrySupplier {
    return LinearMemoryFailureRetrySupplier(
      retry = c7AdapterProperties.serviceTasks.retries,
      retryTimeout = c7AdapterProperties.serviceTasks.retryTimeoutInSeconds
    )
  }

}
