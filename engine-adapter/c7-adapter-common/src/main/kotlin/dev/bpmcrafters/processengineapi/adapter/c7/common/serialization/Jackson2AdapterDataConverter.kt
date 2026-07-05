package dev.bpmcrafters.processengineapi.adapter.c7.common.serialization

import com.fasterxml.jackson.databind.ObjectMapper

class Jackson2AdapterDataConverter(
  private val objectMapper: ObjectMapper
) : AdapterDataConverter {

  override fun <T : Any> convert(value: Any?, type: Class<T>): T? = objectMapper.convertValue(value, type)

}
