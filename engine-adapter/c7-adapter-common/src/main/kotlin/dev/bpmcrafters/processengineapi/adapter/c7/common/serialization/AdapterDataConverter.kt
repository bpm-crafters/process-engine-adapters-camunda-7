package dev.bpmcrafters.processengineapi.adapter.c7.common.serialization

interface AdapterDataConverter {

  fun <T : Any> convert(value: Any?, type: Class<T>): T?

}
