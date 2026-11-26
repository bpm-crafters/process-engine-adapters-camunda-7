package dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import feign.FeignException
import org.camunda.bpm.client.exception.NotFoundException
import org.camunda.bpm.client.exception.RestException

private val errorMapper by lazy { ObjectMapper() }

internal fun isMissingFeignExternalTask(failure: Exception, taskId: String): Boolean {
  // The Feign client is optional when only the official Camunda client is used.
  if (failure.javaClass.name != "feign.FeignException\$NotFound") return false
  val response = try {
    errorMapper.readTree((failure as FeignException.NotFound).contentUTF8())
  } catch (_: JsonProcessingException) {
    return false
  }
  return response?.path("type")?.asText() == "RestException" &&
    response.path("message").asText() == missingTaskMessage(taskId)
}

internal fun isMissingClientExternalTask(failure: Exception, taskId: String): Boolean {
  if (failure !is NotFoundException) return false
  val response = failure.cause as? RestException ?: return false
  return response.type == "RestException" && response.message == missingTaskMessage(taskId)
}

private fun missingTaskMessage(taskId: String) = "External task with id $taskId does not exist"
