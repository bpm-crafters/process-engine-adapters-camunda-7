package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.outbox

import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.*
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.feign.C7RemoteException
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.client.exception.RestException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

internal class C7RemoteOutboxAutoConfigurationTest {

  private val prefix = "dev.bpm-crafters.process-api.adapter.c7remote"
  private val runner = ApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(C7RemoteJdbcOutboxAutoConfiguration::class.java, C7RemoteOutboxAutoConfiguration::class.java))
    .withUserConfiguration(Adapter::class.java)
    .withPropertyValues(
      "$prefix.enabled=true",
      "$prefix.service-tasks.worker-id=worker",
      "$prefix.service-tasks.delivery-strategy=remote_scheduled",
      "$prefix.user-tasks.delivery-strategy=disabled",
    )

  @Test
  fun `stays off by default`() {
    runner.run {
    assertThat(it).doesNotHaveBean(EngineCommandOutbox::class.java)
    assertThat(it).doesNotHaveBean(OutboxEnqueuer::class.java)
  }
  }

  @Test
  fun `property activates the jdbc outbox with relay`() {
    runner
    .withPropertyValues("$prefix.outbox.enabled=true")
    .run {
      assertThat(it).hasSingleBean(JdbcEngineCommandOutbox::class.java)
      assertThat(it).hasSingleBean(EngineCommandOutboxRelay::class.java)
      assertThat(it.getBean(ServiceTaskCompletionApi::class.java)).isInstanceOf(OutboxServiceTaskCompletionApiImpl::class.java)
      assertThat(it.getBean(CorrelationApi::class.java)).isInstanceOf(OutboxCorrelationApiImpl::class.java)
      assertThat(it.getBean(SignalApi::class.java)).isInstanceOf(OutboxSignalApiImpl::class.java)
      assertThat(it).doesNotHaveBean(OutboxUserTaskCompletionApiImpl::class.java)
    }
  }

  @Test
  fun `user task api is decorated when configured`() {
    runner
    .withUserConfiguration(UserTasks::class.java)
    .withPropertyValues("$prefix.outbox.enabled=true")
    .run { assertThat(it.getBean(UserTaskCompletionApi::class.java)).isInstanceOf(OutboxUserTaskCompletionApiImpl::class.java) }
  }

  @Test
  fun `custom outbox activates without relay`() {
    runner
    .withUserConfiguration(CustomOutbox::class.java)
    .run {
      assertThat(it).doesNotHaveBean(JdbcEngineCommandOutbox::class.java)
      assertThat(it).doesNotHaveBean(EngineCommandOutboxRelay::class.java)
      assertThat(it).hasSingleBean(EngineCommandDelivery::class.java)
      assertThat(it.getBean(SignalApi::class.java)).isInstanceOf(OutboxSignalApiImpl::class.java)
    }
  }

  @Test
  fun `property deactivates a custom outbox`() {
    runner
    .withUserConfiguration(CustomOutbox::class.java)
    .withPropertyValues("$prefix.outbox.enabled=false")
    .run { assertThat(it).doesNotHaveBean(OutboxEnqueuer::class.java) }
  }

  @Test
  fun `reads status of both clients`() {
    assertThat(httpStatusOf(C7RemoteException("", null, 400))).isEqualTo(400)
    assertThat(httpStatusOf(RestException("", "", 404).apply { httpStatusCode = 404 })).isEqualTo(404)
    assertThat(httpStatusOf(RuntimeException())).isNull()
  }

  @Configuration
  class Adapter {
    @Bean("c7remote-service-task-completion-api")
    fun completion(): ServiceTaskCompletionApi = mock()

    @Bean("c7remote-correlation-api")
    fun correlation(): CorrelationApi = mock()

    @Bean("c7remote-signal-api")
    fun signal(): SignalApi = mock()

    @Bean("c7remote-failure-retry-supplier")
    fun retries(): FailureRetrySupplier = mock()

    @Bean
    fun repository(): SubscriptionRepository = mock()

    @Bean
    fun dataSource(): DataSource = outboxDataSource()
  }

  @Configuration
  class UserTasks {
    @Bean("c7remote-user-task-completion-api")
    fun userTasks(): UserTaskCompletionApi = mock()
  }

  @Configuration
  class CustomOutbox {
    @Bean
    fun outbox(): EngineCommandOutbox = mock()
  }
}
