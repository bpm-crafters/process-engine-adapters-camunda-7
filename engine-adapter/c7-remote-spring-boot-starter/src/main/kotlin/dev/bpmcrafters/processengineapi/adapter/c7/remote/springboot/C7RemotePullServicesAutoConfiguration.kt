package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.FeignServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.UserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullUserTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.toolisticon.spring.condition.ConditionalOnMissingQualifiedBean
import jakarta.annotation.PostConstruct
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.variables.ValueMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * Autoconfiguration for scheduled delivery.
 */
@AutoConfiguration
@EnableAsync
@AutoConfigureAfter(C7RemoteAdapterAutoConfiguration::class)
@Conditional(C7RemoteAdapterEnabledCondition::class)
class C7RemotePullServicesAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-202: Configuration applied." }
  }

  @Bean("c7remote-task-scheduler")
  @Qualifier("c7remote-task-scheduler")
  @ConditionalOnMissingBean
  fun taskScheduler(): TaskScheduler {
    val threadPoolTaskScheduler = ThreadPoolTaskScheduler()
    threadPoolTaskScheduler.poolSize = 2 // we have two schedulers, one for user tasks one for service tasks
    threadPoolTaskScheduler.threadNamePrefix = "C7REMOTE-SCHEDULER-"
    return threadPoolTaskScheduler
  }

  @Bean("c7remote-service-task-delivery")
  @Qualifier("c7remote-service-task-delivery")
  @ConditionalOnServiceTaskDeliveryStrategy(
    strategy = C7RemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy.REMOTE_SCHEDULED
  )
  fun scheduledServiceTaskDelivery(
    externalTaskApiClient: ExternalTaskApiClient,
    @Qualifier("c7remote-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    subscriptionRepository: SubscriptionRepository,
    c7AdapterProperties: C7RemoteAdapterProperties,
    @Qualifier("c7remote-service-task-worker-executor")
    executorService: ExecutorService,
    valueMapper: ValueMapper
  ) = PullServiceTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    workerId = c7AdapterProperties.serviceTasks.workerId,
    maxTasks = c7AdapterProperties.serviceTasks.maxTaskCount,
    lockDurationInSeconds = c7AdapterProperties.serviceTasks.lockTimeInSeconds,
    retryTimeoutInSeconds = c7AdapterProperties.serviceTasks.retryTimeoutInSeconds,
    retries = c7AdapterProperties.serviceTasks.retries,
    executorService = executorService,
    externalTaskApiClient = externalTaskApiClient,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    valueMapper = valueMapper,
    deserializeOnServer = c7AdapterProperties.serviceTasks.deserializeOnServer
  )

  @Bean("c7remote-service-task-completion-api")
  @Qualifier("c7remote-service-task-completion-api")
  @ConditionalOnServiceTaskDeliveryStrategy(
    strategy = C7RemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy.REMOTE_SCHEDULED
  )
  fun scheduledServiceTaskCompletionApi(
    externalTaskApiClient: ExternalTaskApiClient,
    subscriptionRepository: SubscriptionRepository,
    c7AdapterProperties: C7RemoteAdapterProperties,
    @Qualifier("c7remote-failure-retry-supplier")
    failureRetrySupplier: FailureRetrySupplier,
    valueMapper: ValueMapper
  ): ServiceTaskCompletionApi =
    FeignServiceTaskCompletionApiImpl(
      workerId = c7AdapterProperties.serviceTasks.workerId,
      externalTaskApiClient = externalTaskApiClient,
      subscriptionRepository = subscriptionRepository,
      failureRetrySupplier = failureRetrySupplier,
      valueMapper = valueMapper
    )

  @Bean("c7remote-process-definition-meta-data-resolver")
  @Qualifier("c7remote-process-definition-meta-data-resolver")
  @ConditionalOnMissingQualifiedBean(beanClass = ProcessDefinitionMetaDataResolver::class, qualifier = "c7remote-process-definition-meta-data-resolver")
  fun cachingProcessDefinitionMetaDataResolver(processDefinitionApiClient: ProcessDefinitionApiClient): ProcessDefinitionMetaDataResolver {
    return CachingProcessDefinitionMetaDataResolver(processDefinitionApiClient)
  }

  @Bean("c7remote-user-task-delivery")
  @Qualifier("c7remote-user-task-delivery")
  @ConditionalOnUserTaskDeliveryStrategy(
    strategy = C7RemoteAdapterProperties.UserTaskDeliveryStrategy.REMOTE_SCHEDULED
  )
  fun scheduledUserTaskDelivery(
    @Qualifier("c7remote-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    taskApiClient: TaskApiClient,
    subscriptionRepository: SubscriptionRepository,
    c7AdapterProperties: C7RemoteAdapterProperties,
    @Qualifier("c7remote-user-task-worker-executor")
    executorService: ExecutorService,
    valueMapper: ValueMapper
  ): PullUserTaskDelivery {
    return PullUserTaskDelivery(
      subscriptionRepository = subscriptionRepository,
      executorService = executorService,
      valueMapper = valueMapper,
      processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
      taskApiClient = taskApiClient,
      deserializeOnServer = c7AdapterProperties.userTasks.deserializeOnServer
    )
  }

  /**
   * User completion API.
   */
  @Bean("c7remote-user-task-completion-api")
  @Qualifier("c7remote-user-task-completion-api")
  @ConditionalOnUserTaskDeliveryStrategy(
    strategy = C7RemoteAdapterProperties.UserTaskDeliveryStrategy.REMOTE_SCHEDULED
  )
  fun userTaskCompletionApi(
    taskApiClient: TaskApiClient,
    subscriptionRepository: SubscriptionRepository,
    valueMapper: ValueMapper,
  ): UserTaskCompletionApi =
    UserTaskCompletionApiImpl(
      taskApiClient = taskApiClient,
      subscriptionRepository = subscriptionRepository,
      valueMapper = valueMapper
    )


}
