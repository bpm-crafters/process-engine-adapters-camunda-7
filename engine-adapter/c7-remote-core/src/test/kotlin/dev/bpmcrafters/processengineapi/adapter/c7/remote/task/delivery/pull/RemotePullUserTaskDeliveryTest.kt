package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull

import dev.bpmcrafters.processengineapi.adapter.c7.remote.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.TaskApiClient
import org.camunda.community.rest.client.model.IdentityLinkDto
import org.camunda.community.rest.client.model.TaskWithAttachmentAndCommentDto
import org.camunda.community.rest.variables.SpinValueMapper
import org.camunda.community.rest.variables.ValueMapper
import org.camunda.community.rest.variables.ValueTypeResolverImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal class RemotePullUserTaskDeliveryTest {

  private val keys = mutableMapOf("process-definition-id" to "process-definition-key")
  private val versionTags = mutableMapOf<String, String?>("process-definition-id" to null)
  private val taskApiClient: TaskApiClient = mock()
  private val subscriptionRepository: SubscriptionRepository = InMemSubscriptionRepository()

  private val taskCount = 100
  private val repeatCount = 100
  private val tasks: List<TaskWithAttachmentAndCommentDto> = (0..<taskCount).map {
    randomTask()
  }


  private val embeddedPullUserTaskDelivery = PullUserTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    processDefinitionMetaDataResolver = CachingProcessDefinitionMetaDataResolver(
      processDefinitionApiClient = mock(),
      keys = keys,
      versionTags = versionTags
    ),
    executorService = Executors.newFixedThreadPool(3),
    taskApiClient = taskApiClient,
    valueMapper = ValueMapper(customValueMapper = listOf(SpinValueMapper(ValueTypeResolverImpl()))),
    deserializeOnServer = false
  )

  @BeforeEach
  fun setup() {

    whenever(taskApiClient.queryTasks(any(), any(), any()))
      .thenReturn(ResponseEntity.ok(tasks))

    whenever(taskApiClient.getIdentityLinks(any(), anyOrNull()))
      .thenReturn(ResponseEntity.ok(listOf(IdentityLinkDto().userId("kermit").type("user"))))

    whenever(taskApiClient.getTaskVariables(any(), any()))
      .thenReturn(ResponseEntity.ok(mapOf()))

    assertThat(tasks).hasSize(taskCount)
    assertThat(tasks.map { it.id }.distinct()).hasSize(taskCount)
    subscriptionRepository.getTaskSubscriptions().forEach { subscriptionRepository.deleteTaskSubscription(it) }

  }

  @Test
  fun `deliver tasks directly`() {

    // given
    val deliveredDirectly = ConcurrentHashMap<String, TaskInformation>()
    val terminatedDirectly = ConcurrentHashMap<String, String>()

    subscriptionRepository.createTaskSubscription(
      TaskSubscriptionHandle(
        taskType = TaskType.USER,
        restrictions = mapOf(),
        taskDescriptionKey = null,
        payloadDescription = null,
        action = { taskInformation, _ -> synchronized(deliveredDirectly) { deliveredDirectly[taskInformation.taskId] = taskInformation } },
        termination = { taskInformation -> synchronized(terminatedDirectly) { terminatedDirectly[taskInformation.taskId] = taskInformation.taskId } }
      )
    )

    repeat(repeatCount) {
      embeddedPullUserTaskDelivery.refresh()
    }

    assertThat(terminatedDirectly).hasSize(0)
    assertThat(deliveredDirectly).hasSize(tasks.size)
  }

  @Test
  fun `deliver via user task support`() {
    val userTaskSupport = UserTaskSupport()

    val deliveredViaUserTaskSupport = ConcurrentHashMap<String, TaskInformation>()
    val terminatedViaUserTaskSupport = ConcurrentHashMap<String, String>()


    userTaskSupport.subscribe(taskSubscriptionApi = TaskSubscriptionApiImpl(subscriptionRepository), restrictions = mapOf(), payloadDescription = null)
    userTaskSupport.addHandler { taskInformation, _ ->
      synchronized(deliveredViaUserTaskSupport) {
        deliveredViaUserTaskSupport[taskInformation.taskId] = taskInformation
      }
    }
    userTaskSupport.addTerminationHandler { taskInformation ->
      synchronized(terminatedViaUserTaskSupport) {
        terminatedViaUserTaskSupport[taskInformation.taskId] = taskInformation.taskId
      }
    }

    repeat(repeatCount) {
      embeddedPullUserTaskDelivery.refresh()
    }

    assertThat(terminatedViaUserTaskSupport).hasSize(0)
    assertThat(deliveredViaUserTaskSupport).hasSize(tasks.size)
  }

  private fun randomTask() = TaskWithAttachmentAndCommentDto()
    .id(UUID.randomUUID().toString())
    .assignee("kermit")
    .processDefinitionId("process-definition-id")
    .name("Perform user task")
}
