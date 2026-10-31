package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskType
import dev.bpmcrafters.processengineapi.test.ProcessTestHelper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.camunda.bpm.engine.RuntimeService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Import(C7EmbeddedScheduledUserTaskDeliveryExecutorITest.NamedExecutorsConfiguration::class)
class C7EmbeddedScheduledUserTaskDeliveryExecutorITest(
  @Autowired processTestHelperImpl: ProcessTestHelper,
  @Autowired private val runtimeService: RuntimeService,
) : AbstractC7EmbeddedSpringApiITest(processTestHelperImpl) {

  @TestConfiguration(proxyBeanMethods = false)
  class NamedExecutorsConfiguration {

    @Bean("c7embedded-service-task-worker-executor")
    @Qualifier("c7embedded-service-task-worker-executor")
    fun serviceTaskWorkerExecutor(): ThreadPoolExecutor = namedExecutor("SERVICE-TASK-EXECUTOR")

    @Bean("c7embedded-user-task-worker-executor")
    @Qualifier("c7embedded-user-task-worker-executor")
    fun userTaskWorkerExecutor(): ThreadPoolExecutor = namedExecutor("USER-TASK-EXECUTOR")

    private fun namedExecutor(prefix: String): ThreadPoolExecutor {
      val threadCounter = AtomicInteger()
      return ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { runnable ->
        Thread(runnable, "$prefix-${threadCounter.incrementAndGet()}")
      }
    }
  }

  @Test
  fun `should deliver user tasks on the user task executor`() {
    val handlerThreadName = AtomicReference<String>()
    processTestHelper.getTaskSubscriptionApi().subscribeForTask(
      SubscribeForTaskCmd(
        restrictions = mapOf(CommonRestrictions.PROCESS_DEFINITION_KEY to KEY),
        taskType = TaskType.USER,
        taskDescriptionKey = USER_TASK,
        action = { _, _ -> handlerThreadName.compareAndSet(null, Thread.currentThread().name) },
        termination = {},
      )
    ).get()

    runtimeService.startProcessInstanceByKey(KEY)

    await().until { handlerThreadName.get() != null }
    assertThat(handlerThreadName.get()).startsWith("USER-TASK-EXECUTOR")
  }
}
