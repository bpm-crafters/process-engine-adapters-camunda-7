package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull

import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskHandler
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskInformation.Companion.CREATE
import dev.bpmcrafters.processengineapi.task.TaskType
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.model.ExternalTaskDto
import org.camunda.community.rest.client.model.FetchExternalTasksDto
import org.camunda.community.rest.client.model.LockedExternalTaskDto
import org.camunda.community.rest.variables.ValueMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.lenient
import org.mockito.kotlin.*
import org.springframework.http.ResponseEntity
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

internal class PullServiceTaskDeliveryThreadingTest {

  private val externalTaskApiClient = mock<ExternalTaskApiClient>()

  private val workerId = "best-worker-in-town"

  private val subscriptionRepository = InMemSubscriptionRepository()

  private val executor = ThreadPoolExecutor(2, 2, 0, MILLISECONDS, LinkedBlockingQueue(3))

  private val taskDelivery = spy(PullServiceTaskDelivery(
    externalTaskApiClient = externalTaskApiClient,
    processDefinitionMetaDataResolver = mock<ProcessDefinitionMetaDataResolver>(),
    workerId = workerId,
    subscriptionRepository = subscriptionRepository,
    maxTasks = 2,
    lockDurationInSeconds = 10,
    retryTimeoutInSeconds = 10,
    retries = 1,
    executor = executor,
    valueMapper = mock<ValueMapper>(),
    deserializeOnServer = false,
  ))

  private val executingTaskHandlers = LinkedBlockingQueue<TaskHandler>()

  private inner class TestTaskHandlerImpl : TaskHandler {
    override fun accept(taskInformation: TaskInformation, variables: Map<String, Any>) {
      synchronized(this) {
        executingTaskHandlers.offer(this)
        wait()
      }
    }
  }

  @BeforeEach
  fun setUp() {
    subscriptionRepository.createTaskSubscription(TaskSubscriptionHandle(
      TaskType.EXTERNAL,
      null,
      mapOf(),
      "topical-but-not-tropical",
      TestTaskHandlerImpl(),
      mock()
    ))

    lenient()
      .doReturn(ResponseEntity.ok(mutableListOf<ExternalTaskDto>()))
      .whenever(externalTaskApiClient)
      .queryExternalTasks(isNull(), isNull(), any())

    lenient()
      .doAnswer { invocation ->
        val lockedTask = invocation.getArgument<LockedExternalTaskDto>(0)
        val activeSubscription = invocation.getArgument<TaskSubscriptionHandle>(1)
        Callable {
          activeSubscription.action.accept(
            TaskInformation(taskId = lockedTask.id, meta = emptyMap()).withReason(CREATE),
            lockedTask.variables
          )
        }
      }
      .whenever(taskDelivery)
      .createTaskActionHandlerCallable(any(), any())
  }

  @AfterEach
  fun tearDown() {
    executor.shutdownNow()
    executor.awaitTermination(10, SECONDS) // Should be plenty.
  }

  @Test
  fun `simulate throughput`() {
    endlesslySupplyLockedExternalTasks()

    taskDelivery.refresh() // Tick
    assertActiveCount(2)
    assertRemainingCapacity(3)

    taskDelivery.refresh() // Tick
    assertActiveCount(2)
    assertRemainingCapacity(1)

    taskDelivery.refresh() // Tick
    assertActiveCount(2)
    assertRemainingCapacity(0)

    executeTaskHandlers(2)
    assertActiveCount(2)
    assertRemainingCapacity(2)

    taskDelivery.refresh() // Tick
    assertActiveCount(2)
    assertRemainingCapacity(0)

    executeTaskHandlers(5)
    assertActiveCount(0)
    assertRemainingCapacity(3)
  }

  fun endlesslySupplyLockedExternalTasks() {
    val idSequence = AtomicInteger(0)
    doAnswer { invocation ->
      val fetchExternalTasks = invocation.getArgument<FetchExternalTasksDto>(0)
      val taskGenerator = generateSequence {
        LockedExternalTaskDto()
          .id(idSequence.incrementAndGet().toString())
          .topicName("topical-but-not-tropical")
          .variables(mapOf())
      }
      ResponseEntity.ok(taskGenerator.take(fetchExternalTasks.maxTasks).toMutableList())
    }
      .whenever(externalTaskApiClient)
      .fetchAndLock(any())
  }

  fun assertActiveCount(expected: Int) {
    await atMost Duration.ofSeconds(5) untilAsserted {
      assertEquals(expected, executor.activeCount)
    }
  }

  fun assertRemainingCapacity(expected: Int) {
    await atMost Duration.ofSeconds(5) untilAsserted {
      assertEquals(expected, executor.queue.remainingCapacity())
    }
  }

  fun executeTaskHandlers(n: Int) {
    (1..n).forEach { _ ->
      val executingTaskHandler = executingTaskHandlers.poll(5, SECONDS)!!
      synchronized(executingTaskHandler) {
        executingTaskHandler.notifyAll()
      }
    }
  }
}

private fun TaskHandler.wait() = (this as Object).wait()

private fun TaskHandler.notifyAll() = (this as Object).notifyAll()
