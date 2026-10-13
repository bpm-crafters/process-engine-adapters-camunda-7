package dev.bpmcrafters.processengineapi.adapter.c7.embedded.testing

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestAssertApi
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestQueryApi
import dev.bpmcrafters.processengineapi.testing.config.ProcessTestPollingConfiguration
import org.awaitility.Awaitility.await
import org.camunda.bpm.engine.ProcessEngine
import java.util.function.Predicate

internal class C7EmbeddedProcessTestAssertApi(
  private val engine: ProcessEngine,
  private val query: ProcessTestQueryApi,
  private val polling: ProcessTestPollingConfiguration
) : ProcessTestAssertApi {
  override fun getSupportedRestrictions() = query.getSupportedRestrictions()
  override fun processWaitsInUserTask(
    instanceId: String,
    taskDescriptionKey: String?,
    restrictions: Map<String, String>,
    predicate: Predicate<TaskInformation>?
  ) = eventually("process $instanceId to wait in one user task") {
    selected(
      query.findUserTasks(
        taskDescriptionKey,
        restrictions + (CommonRestrictions.PROCESS_INSTANCE_ID to instanceId),
        predicate
      )
    )
  }

  override fun processWaitsInElement(instanceId: String, elementId: String, restrictions: Map<String, String>) {
    eventually("process $instanceId to wait in element $elementId") {
      require(
        engine.runtimeService.getActiveActivityIds(instanceId).contains(elementId)
      ) { "Active elements: ${engine.runtimeService.getActiveActivityIds(instanceId)}" }
    }
  }

  override fun userTaskIsSelected(taskDescriptionKey: String?, restrictions: Map<String, String>, predicate: Predicate<TaskInformation>?) =
    eventually("one user task to be selected") { selected(query.findUserTasks(taskDescriptionKey, restrictions, predicate)) }

  override fun externalTaskIsSelected(taskDescriptionKey: String?, restrictions: Map<String, String>, predicate: Predicate<TaskInformation>?) =
    eventually("one external task to be selected") { selected(query.findExternalTasks(taskDescriptionKey, restrictions, predicate)) }

  override fun processHasPassed(instanceId: String, restrictions: Map<String, String>, vararg elementIds: String) =
    assertHistory(instanceId, elementIds, false, true)

  override fun processHasPassedInOrder(instanceId: String, restrictions: Map<String, String>, vararg elementIds: String) =
    assertHistory(instanceId, elementIds, true, true)

  override fun processHasNotPassed(instanceId: String, restrictions: Map<String, String>, vararg elementIds: String) =
    assertHistory(instanceId, elementIds, false, false)

  override fun processIsFinished(instanceId: String, restrictions: Map<String, String>) {
    eventually("process $instanceId to finish") {
      require(
        engine.runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult() == null
      ) { "Process is still active" }; require(
      engine.historyService.createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult() != null
    ) { "No historic process instance found" }
    }
  }

  override fun processHasIncidents(instanceId: String, restrictions: Map<String, String>) {
    eventually("process $instanceId to have incidents") {
      require(
        engine.runtimeService.createIncidentQuery().processInstanceId(instanceId).count() > 0
      ) { "No incidents found" }
    }
  }

  private fun assertHistory(instanceId: String, ids: Array<out String>, ordered: Boolean, expected: Boolean) {
    require(ids.isNotEmpty()) { "At least one element id is required." };
    val passed = engine.historyService.createHistoricActivityInstanceQuery().processInstanceId(instanceId).orderByHistoricActivityInstanceStartTime().asc()
      .orderPartiallyByOccurrence().asc().list().map { it.activityId }; if (expected) {
      require(ids.all { it in passed }) { "Expected $ids to be passed; actual finished elements: $passed" }; if (ordered) {
        var nextIndex = 0; ids.forEach { id ->
          val index =
            passed.subList(nextIndex, passed.size).indexOf(id); require(index >= 0) { "Expected $ids in order; actual: $passed" }; nextIndex += index + 1
        }
      }
    } else require(ids.none { it in passed }) { "Expected $ids not to be passed; actual finished elements: $passed" }
  }

  private fun <T> eventually(description: String, supplier: () -> T): T =
    await().pollInterval(polling.pollInterval).atMost(polling.timeout).until({ supplier() }, Predicate { true })

  private fun selected(tasks: List<TaskInformation>): TaskInformation {
    require(tasks.size == 1) { "Expected exactly one task but found ${tasks.size}: $tasks" }; return tasks.single()
  }
}
