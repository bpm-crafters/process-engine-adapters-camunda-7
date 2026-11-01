package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.outbox

import dev.bpmcrafters.processengineapi.adapter.c7.common.threading.ThreadContextClassLoaderThreadFactory
import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.*
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemotePullServicesAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.client.OfficialClientServiceTaskAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.feign.C7RemoteException
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import feign.FeignException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.client.exception.ConnectionLostException
import org.camunda.bpm.client.exception.RestException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.util.ClassUtils
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

private const val SERVICE_TASK_API = "c7remote-service-task-completion-api"
private const val USER_TASK_API = "c7remote-user-task-completion-api"
private const val CORRELATION_API = "c7remote-correlation-api"
private const val SIGNAL_API = "c7remote-signal-api"
private const val SERVICE_UNAVAILABLE = 503

/**
 * Activates the outbox when an [EngineCommandOutbox] bean exists, unless `outbox.enabled=false`.
 * Decorators exist only for adapter APIs that are configured.
 */
@AutoConfiguration(
  after = [
    C7RemoteAdapterAutoConfiguration::class,
    C7RemotePullServicesAutoConfiguration::class,
    OfficialClientServiceTaskAutoConfiguration::class,
  ]
)
@EnableConfigurationProperties(C7RemoteAdapterProperties::class)
@Conditional(C7RemoteAdapterEnabledCondition::class)
@ConditionalOnBean(EngineCommandOutbox::class)
@ConditionalOnClass(TransactionSynchronizationManager::class)
@ConditionalOnProperty(prefix = "${C7RemoteAdapterProperties.DEFAULT_PREFIX}.outbox", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class C7RemoteOutboxAutoConfiguration {

  @Bean
  fun transactionalEngineCommandDispatcher(outbox: EngineCommandOutbox, subscriptionRepository: SubscriptionRepository) =
    TransactionalEngineCommandDispatcher(outbox, subscriptionRepository, SpringOutboxTransaction)

  @Bean
  @Primary
  @ConditionalOnBean(name = [SERVICE_TASK_API])
  fun outboxServiceTaskCompletionApi(
    beanFactory: BeanFactory,
    dispatcher: TransactionalEngineCommandDispatcher,
    @Qualifier("c7remote-failure-retry-supplier") failureRetrySupplier: FailureRetrySupplier,
  ): ServiceTaskCompletionApi =
    OutboxServiceTaskCompletionApiImpl(beanFactory.getBean(SERVICE_TASK_API, ServiceTaskCompletionApi::class.java), dispatcher, failureRetrySupplier)

  @Bean
  @Primary
  @ConditionalOnBean(name = [USER_TASK_API])
  fun outboxUserTaskCompletionApi(beanFactory: BeanFactory, dispatcher: TransactionalEngineCommandDispatcher): UserTaskCompletionApi =
    OutboxUserTaskCompletionApiImpl(beanFactory.getBean(USER_TASK_API, UserTaskCompletionApi::class.java), dispatcher)

  @Bean
  @Primary
  fun outboxCorrelationApi(beanFactory: BeanFactory, dispatcher: TransactionalEngineCommandDispatcher): CorrelationApi =
    OutboxCorrelationApiImpl(beanFactory.getBean(CORRELATION_API, CorrelationApi::class.java), dispatcher)

  @Bean
  @Primary
  fun outboxSignalApi(beanFactory: BeanFactory, dispatcher: TransactionalEngineCommandDispatcher): SignalApi =
    OutboxSignalApiImpl(beanFactory.getBean(SIGNAL_API, SignalApi::class.java), dispatcher)

  /**
   * Delivers to the engine directly. Also the entry point for relays of custom outboxes.
   */
  @Bean
  fun engineCommandDelivery(beanFactory: BeanFactory) = EngineCommandDelivery(
    serviceTaskCompletionApi = if (beanFactory.containsBean(SERVICE_TASK_API)) beanFactory.getBean(SERVICE_TASK_API, ServiceTaskCompletionApi::class.java) else null,
    userTaskCompletionApi = if (beanFactory.containsBean(USER_TASK_API)) beanFactory.getBean(USER_TASK_API, UserTaskCompletionApi::class.java) else null,
    correlationApi = beanFactory.getBean(CORRELATION_API, CorrelationApi::class.java),
    signalApi = beanFactory.getBean(SIGNAL_API, SignalApi::class.java),
    httpStatusOf = ::httpStatusOf,
  )

  @Bean
  @ConditionalOnBean(RelayedEngineCommandOutbox::class)
  fun engineCommandOutboxRelay(
    outbox: RelayedEngineCommandOutbox,
    delivery: EngineCommandDelivery,
    properties: C7RemoteAdapterProperties,
  ) = properties.outbox.let {
    EngineCommandOutboxRelay(
      outbox = outbox,
      delivery = delivery,
      batchSize = it.batchSize,
      leaseDuration = it.leaseDuration,
      maxAttempts = it.maxAttempts,
      initialBackoff = it.relayInterval,
    )
  }

  @Bean
  @ConditionalOnBean(EngineCommandOutboxRelay::class)
  fun engineCommandOutboxRelayLifecycle(relay: EngineCommandOutboxRelay, properties: C7RemoteAdapterProperties) =
    OutboxRelayLifecycle(relay, properties.outbox.relayInterval.toMillis())
}

/**
 * Runs the relay on an own thread, as the adapter scheduler pool is sized for the pollers.
 */
class OutboxRelayLifecycle(
  private val relay: EngineCommandOutboxRelay,
  private val intervalInMillis: Long,
) : InitializingBean, DisposableBean {

  private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
    ThreadContextClassLoaderThreadFactory(OutboxRelayLifecycle::class.java.classLoader)
  )

  override fun afterPropertiesSet() {
    scheduler.scheduleWithFixedDelay(::relaySafely, intervalInMillis, intervalInMillis, TimeUnit.MILLISECONDS)
  }

  override fun destroy() {
    scheduler.shutdown()
  }

  private fun relaySafely() {
    try {
      relay.relay()
    } catch (e: Throwable) {
      logger.error(e) { "PROCESS-ENGINE-C7-REMOTE-125: outbox relay run failed: ${e.message}" }
    }
  }
}

internal object SpringOutboxTransaction : OutboxTransaction {
  override fun isActive() = TransactionSynchronizationManager.isActualTransactionActive()

  override fun afterCommit(action: () -> Unit) {
    TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
      override fun afterCommit() = action()
    })
  }
}

private val officialClientPresent = ClassUtils.isPresent(
  "org.camunda.bpm.client.exception.RestException", C7RemoteOutboxAutoConfiguration::class.java.classLoader
)

internal fun httpStatusOf(failure: Exception): Int? = when {
  failure is C7RemoteException -> failure.httpStatus
  failure is FeignException -> failure.status().takeIf { it > 0 }
  officialClientPresent -> officialClientStatus(failure)
  else -> null
}

private fun officialClientStatus(failure: Exception): Int? = when (failure) {
  is ConnectionLostException -> SERVICE_UNAVAILABLE
  is RestException -> failure.httpStatusCode
  else -> null
}
