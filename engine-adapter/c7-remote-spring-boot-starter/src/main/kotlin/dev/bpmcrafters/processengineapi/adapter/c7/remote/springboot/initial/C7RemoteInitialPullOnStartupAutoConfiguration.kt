package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties.Companion.DEFAULT_PREFIX
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.client.OfficialClientServiceTaskAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.variables.ValueMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.scheduling.annotation.EnableAsync
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor

private val logger = KotlinLogging.logger {}

/**
 * This configuration configures the initial pull bound to the application started event.
 * It is not relying on any delivery strategies but just configures the initial pull to happen
 * and deliver tasks to the task handlers.
 */
@AutoConfiguration
@AutoConfigureAfter(OfficialClientServiceTaskAutoConfiguration::class)
@EnableAsync
@Conditional(C7RemoteAdapterEnabledCondition::class)
class C7RemoteInitialPullOnStartupAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-203: Configuration applied." }
  }

  @Bean("c7remote-user-task-initial-pull")
  @Qualifier("c7remote-user-task-initial-pull")
  @ConditionalOnProperty(prefix = DEFAULT_PREFIX, name = ["user-tasks.execute-initial-pull-on-startup"])
  fun configureInitialPullForUserTaskDelivery(
    taskApiClient: TaskApiClient,
    processDefinitionApiClient: ProcessDefinitionApiClient,
    subscriptionRepository: SubscriptionRepository,
    @Qualifier("c7remote-user-task-worker-executor")
    executorService: ExecutorService,
    valueMapper: ValueMapper,
    @Qualifier("c7remote-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    c7AdapterProperties: C7RemoteAdapterProperties
  ) = C7RemoteInitialPullUserTasksDeliveryBinding(
    taskApiClient = taskApiClient,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    subscriptionRepository = subscriptionRepository,
    executorService = executorService,
    valueMapper = valueMapper,
    c7RemoteAdapterProperties = c7AdapterProperties
  )

  @Bean("c7remote-service-task-initial-pull")
  @Qualifier("c7remote-service-task-initial-pull")
  @ConditionalOnProperty(prefix = DEFAULT_PREFIX, name = ["service-tasks.execute-initial-pull-on-startup"])
  fun configureInitialPullForExternalServiceTaskDelivery(
    externalTaskApi: ExternalTaskApiClient,
    subscriptionRepository: SubscriptionRepository,
    c7AdapterProperties: C7RemoteAdapterProperties,
    @Qualifier("c7remote-service-task-worker-executor")
    executor: ThreadPoolExecutor,
    valueMapper: ValueMapper,
    @Qualifier("c7remote-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    metrics: PullServiceTaskDeliveryMetrics,
  ) = C7RemoteInitialPullServiceTasksDeliveryBinding(
    externalTaskApiClient = externalTaskApi,
    subscriptionRepository = subscriptionRepository,
    c7AdapterProperties = c7AdapterProperties,
    executor = executor,
    valueMapper = valueMapper,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    metrics = metrics,
  )
}
