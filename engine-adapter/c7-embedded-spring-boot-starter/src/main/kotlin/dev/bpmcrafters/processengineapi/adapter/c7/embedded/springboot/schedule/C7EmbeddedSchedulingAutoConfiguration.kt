package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.*
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties.ExternalServiceTaskDeliveryStrategy.EMBEDDED_SCHEDULED
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot.C7EmbeddedAdapterProperties.UserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullUserTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.toolisticon.spring.condition.ConditionalOnMissingQualifiedBean
import jakarta.annotation.PostConstruct
import org.camunda.bpm.engine.ExternalTaskService
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.engine.TaskService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.task.SimpleAsyncTaskSchedulerBuilder
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.core.annotation.Order
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor

private val logger = KotlinLogging.logger {}

/**
 * Configures scheduled polling for embedded user tasks and external service tasks.
 *
 * The scheduler selection uses local conditions instead of Spring Boot's former
 * `ConditionalOnThreading` API so the same starter artifact can be compiled and used with both
 * Spring Boot 3 and Spring Boot 4.
 */
@AutoConfiguration
@EnableScheduling
@EnableAsync
@AutoConfigureAfter(C7EmbeddedAdapterAutoConfiguration::class)
@Conditional(C7EmbeddedAdapterEnabledCondition::class)
class C7EmbeddedSchedulingAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-201: Configuration for schedule-based deliver applied." }
  }

  @Bean("c7embedded-task-scheduler")
  @Qualifier("c7embedded-task-scheduler")
  @Order(200)
  @ConditionalOnMissingQualifiedBean(beanClass = TaskScheduler::class, qualifier = "c7embedded-task-scheduler")
  fun taskScheduler(): TaskScheduler {
    val threadPoolTaskScheduler = ThreadPoolTaskScheduler()
    threadPoolTaskScheduler.setPoolSize(2) // we have two schedulers, one for user tasks one for service tasks
    threadPoolTaskScheduler.setThreadNamePrefix("C7EMBEDDED-SCHEDULER-")
    return threadPoolTaskScheduler
  }

  @Bean("taskScheduler")
  @Order(100)
  @Conditional(VirtualThreadingCondition::class)
  fun taskSchedulerVirtualThreads(builder: SimpleAsyncTaskSchedulerBuilder): SimpleAsyncTaskScheduler {
    return builder.build()
  }

  @Bean("taskScheduler")
  @Order(100)
  @Conditional(PlatformThreadingCondition::class)
  fun taskSchedulerPlatformThreads(threadPoolTaskSchedulerBuilder: ThreadPoolTaskSchedulerBuilder): ThreadPoolTaskScheduler {
    return threadPoolTaskSchedulerBuilder.build()
  }

  @Bean("c7embedded-service-task-delivery")
  @Qualifier("c7embedded-service-task-delivery")
  @ConditionalOnServiceTaskDeliveryStrategy(
    strategy = EMBEDDED_SCHEDULED
  )
  fun serviceTaskDelivery(
    subscriptionRepository: SubscriptionRepository,
    externalTaskService: ExternalTaskService,
    c7AdapterProperties: C7EmbeddedAdapterProperties,
    @Qualifier("c7embedded-service-task-worker-executor")
    executor: ThreadPoolExecutor,
    metrics: EmbeddedPullServiceTaskDeliveryMetrics,
  ) = EmbeddedPullServiceTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    externalTaskService = externalTaskService,
    workerId = c7AdapterProperties.serviceTasks.workerId,
    maxTasks = c7AdapterProperties.serviceTasks.maxTaskCount,
    lockDurationInSeconds = c7AdapterProperties.serviceTasks.lockTimeInSeconds,
    retryTimeoutInSeconds = c7AdapterProperties.serviceTasks.retryTimeoutInSeconds,
    retries = c7AdapterProperties.serviceTasks.retries,
    executor = executor,
    metrics = metrics
  )


  @Bean("c7embedded-process-definition-meta-data-resolver")
  @Qualifier("c7embedded-process-definition-meta-data-resolver")
  @ConditionalOnMissingQualifiedBean(beanClass = ProcessDefinitionMetaDataResolver::class, qualifier = "c7embedded-process-definition-meta-data-resolver")
  fun cachingProcessDefinitionMetaDataResolver(repositoryService: RepositoryService): ProcessDefinitionMetaDataResolver {
    return CachingProcessDefinitionMetaDataResolver(repositoryService = repositoryService)
  }


  @Bean("c7embedded-schedule-user-task-delivery")
  @Qualifier("c7embedded-schedule-user-task-delivery")
  @ConditionalOnUserTaskDeliveryStrategy(
    strategies = [UserTaskDeliveryStrategy.EMBEDDED_SCHEDULED]
  )
  fun embeddedScheduledUserTaskDelivery(
    subscriptionRepository: SubscriptionRepository,
    taskService: TaskService,
    @Qualifier("c7embedded-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    c7AdapterProperties: C7EmbeddedAdapterProperties,
    @Qualifier("c7embedded-service-task-worker-executor")
    executorService: ExecutorService
  ): EmbeddedPullUserTaskDelivery {
    return EmbeddedPullUserTaskDelivery(
      subscriptionRepository = subscriptionRepository,
      taskService = taskService,
      processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
      executorService = executorService
    )
  }
}
