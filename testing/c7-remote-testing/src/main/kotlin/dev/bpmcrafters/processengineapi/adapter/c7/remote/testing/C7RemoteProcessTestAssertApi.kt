package dev.bpmcrafters.processengineapi.adapter.c7.remote.testing

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestAssertApi
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestQueryApi
import dev.bpmcrafters.processengineapi.testing.config.ProcessTestPollingConfiguration
import org.awaitility.Awaitility.await
import org.camunda.community.rest.client.api.HistoryApiClient
import org.camunda.community.rest.client.api.IncidentApiClient
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.camunda.community.rest.client.model.ActivityInstanceDto
import org.camunda.community.rest.client.model.HistoricActivityInstanceQueryDto
import org.camunda.community.rest.client.model.HistoricProcessInstanceQueryDto
import org.springframework.beans.factory.getBean
import org.springframework.context.ConfigurableApplicationContext
import java.util.function.Predicate

internal class C7RemoteProcessTestAssertApi(
  application: ConfigurableApplicationContext,
  private val query: ProcessTestQueryApi,
  private val polling: ProcessTestPollingConfiguration
) : ProcessTestAssertApi {
  private val processes = application.getBean<ProcessInstanceApiClient>()
  private val history = application.getBean<HistoryApiClient>()
  private val incidents = application.getBean<IncidentApiClient>()
  override fun getSupportedRestrictions() = query.getSupportedRestrictions()
  override fun processWaitsInUserTask(
    instanceId: String,
    taskDescriptionKey: String?,
    restrictions: Map<String, String>,
    predicate: Predicate<TaskInformation>?
  ) = eventually { selected(query.findUserTasks(taskDescriptionKey, restrictions + (CommonRestrictions.PROCESS_INSTANCE_ID to instanceId), predicate)) }

  override fun userTaskIsSelected(taskDescriptionKey: String?, restrictions: Map<String, String>, predicate: Predicate<TaskInformation>?) =
    eventually { selected(query.findUserTasks(taskDescriptionKey, restrictions, predicate)) }

  override fun externalTaskIsSelected(taskDescriptionKey: String?, restrictions: Map<String, String>, predicate: Predicate<TaskInformation>?) =
    eventually { selected(query.findExternalTasks(taskDescriptionKey, restrictions, predicate)) }

  override fun processWaitsInElement(instanceId: String, elementId: String, restrictions: Map<String, String>) {
    eventually { require(processes.getActivityInstanceTree(instanceId).body.contains(elementId)) { "Process $instanceId is not active in $elementId" } }
  }

  override fun processHasPassed(instanceId: String, restrictions: Map<String, String>, vararg elementIds: String) =
    assertHistory(instanceId, false, true, elementIds)

  override fun processHasPassedInOrder(instanceId: String, restrictions: Map<String, String>, vararg elementIds: String) =
    assertHistory(instanceId, true, true, elementIds)

  override fun processHasNotPassed(instanceId: String, restrictions: Map<String, String>, vararg elementIds: String) =
    assertHistory(instanceId, false, false, elementIds)

  override fun processIsFinished(instanceId: String, restrictions: Map<String, String>) {
    eventually {
      require(query.getProcessInformation(instanceId) == null) { "Process $instanceId is still active" }; require(
      history.queryHistoricProcessInstances(
        0,
        1,
        HistoricProcessInstanceQueryDto().processInstanceId(instanceId)
      ).body.orEmpty().isNotEmpty()
    ) { "No historic process instance found for $instanceId" }
    }
  }

  override fun processHasIncidents(instanceId: String, restrictions: Map<String, String>) {
    eventually {
      require(
        incidents.getIncidents(
          null,
          instanceId,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          0,
          1
        ).body.orEmpty().isNotEmpty()
      ) { "No incidents found" }
    }
  }

  private fun assertHistory(instanceId: String, ordered: Boolean, expected: Boolean, ids: Array<out String>) {
    require(ids.isNotEmpty()) { "At least one element id is required." };
    val passed = history.queryHistoricActivityInstances(0, 1_000, HistoricActivityInstanceQueryDto().processInstanceId(instanceId)).body.orEmpty()
      .sortedBy { it.startTime }.mapNotNull { it.activityId }; if (expected) {
      require(ids.all { it in passed }) { "Expected $ids to be passed; actual: $passed" }; if (ordered) ids.fold(0) { index, id ->
        passed.indexOfFirstFrom(
          index,
          id
        ).also { require(it >= 0) { "Expected $ids in order; actual: $passed" } } + 1
      }
    } else require(ids.none { it in passed }) { "Expected $ids not to be passed; actual: $passed" }
  }

  private fun <T> eventually(supplier: () -> T): T = await().pollInterval(polling.pollInterval).atMost(polling.timeout).until(supplier, Predicate { true })
  private fun selected(tasks: List<TaskInformation>): TaskInformation =
    requireNotNull(tasks.singleOrNull()) { "Expected exactly one task but found ${tasks.size}: $tasks" }
}

private fun ActivityInstanceDto?.contains(elementId: String): Boolean =
  this != null && (activityId == elementId || childActivityInstances.orEmpty().any { it.contains(elementId) })

private fun List<String>.indexOfFirstFrom(start: Int, value: String) = subList(start, size).indexOf(value).let { if (it < 0) -1 else start + it }
