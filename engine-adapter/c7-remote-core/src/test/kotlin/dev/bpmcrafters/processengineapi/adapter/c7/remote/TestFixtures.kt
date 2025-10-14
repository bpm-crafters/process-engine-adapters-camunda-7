package dev.bpmcrafters.processengineapi.adapter.c7.remote

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.camunda.bpm.engine.variable.Variables.SerializationDataFormats
import org.camunda.community.rest.variables.ValueMapper
import org.camunda.community.rest.variables.ValueTypeRegistration
import org.camunda.community.rest.variables.ValueTypeResolverImpl
import org.camunda.community.rest.variables.serialization.SpinJsonValueSerializer

object TestFixtures {

  val objectMapper = jacksonObjectMapper()
  val valueTypeRegistration = ValueTypeRegistration()
  val valueTypeResolver = ValueTypeResolverImpl()
  val spinJsonValueSerializer = SpinJsonValueSerializer(valueTypeResolver, valueTypeRegistration)

  fun valueMapper(): ValueMapper = ValueMapper(
    objectMapper = objectMapper,
    valueTypeResolver = valueTypeResolver,
    valueTypeRegistration = valueTypeRegistration,
    customValueSerializers = listOf(spinJsonValueSerializer),
    serializationFormat = SerializationDataFormats.JSON,
    valueSerializers = listOf()
  )
}
