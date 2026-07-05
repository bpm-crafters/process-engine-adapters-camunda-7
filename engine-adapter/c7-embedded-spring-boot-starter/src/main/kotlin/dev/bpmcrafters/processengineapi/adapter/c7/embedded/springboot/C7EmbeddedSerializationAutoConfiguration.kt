package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.common.serialization.AdapterDataConverter
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.util.ClassUtils

@AutoConfiguration(
  afterName = ["org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"],
  before = [C7EmbeddedAdapterAutoConfiguration::class]
)
class C7EmbeddedSerializationAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(AdapterDataConverter::class)
  fun adapterDataConverter(beanFactory: ListableBeanFactory): AdapterDataConverter {
    val classLoader = javaClass.classLoader
    val jackson2ObjectMapper = beanOfType(beanFactory, classLoader, JACKSON2_OBJECT_MAPPER)
    val jackson3ObjectMapper = beanOfType(beanFactory, classLoader, JACKSON3_OBJECT_MAPPER)

    return when {
      // Spring Boot 4 may expose both ecosystems on the classpath. Prefer Jackson 3 unless the application overrides.
      jackson3ObjectMapper != null -> instantiateConverter(
        classLoader = classLoader,
        converterClassName = JACKSON3_CONVERTER,
        objectMapperClassName = JACKSON3_OBJECT_MAPPER,
        objectMapper = jackson3ObjectMapper,
      )

      jackson2ObjectMapper != null -> instantiateConverter(
        classLoader = classLoader,
        converterClassName = JACKSON2_CONVERTER,
        objectMapperClassName = JACKSON2_OBJECT_MAPPER,
        objectMapper = jackson2ObjectMapper,
      )

      else -> throw IllegalStateException(
        "No supported ObjectMapper bean found. " +
          "Provide either a Jackson 2 com.fasterxml ObjectMapper bean or a Jackson 3 tools.jackson ObjectMapper bean."
      )
    }
  }

  private fun beanOfType(beanFactory: ListableBeanFactory, classLoader: ClassLoader, className: String): Any? {
    if (!ClassUtils.isPresent(className, classLoader)) {
      return null
    }
    val type = ClassUtils.forName(className, classLoader)
    return beanFactory.getBeanProvider(type).ifAvailable
  }

  private fun instantiateConverter(
    classLoader: ClassLoader,
    converterClassName: String,
    objectMapperClassName: String,
    objectMapper: Any,
  ): AdapterDataConverter {
    val converterClass = ClassUtils.forName(converterClassName, classLoader)
    val objectMapperClass = ClassUtils.forName(objectMapperClassName, classLoader)
    return converterClass
      .getConstructor(objectMapperClass)
      .newInstance(objectMapper) as AdapterDataConverter
  }

  companion object {
    private const val JACKSON2_OBJECT_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper"
    private const val JACKSON3_OBJECT_MAPPER = "tools.jackson.databind.ObjectMapper"
    private const val JACKSON2_CONVERTER =
      "dev.bpmcrafters.processengineapi.adapter.c7.common.serialization.Jackson2AdapterDataConverter"
    private const val JACKSON3_CONVERTER =
      "dev.bpmcrafters.processengineapi.adapter.c7.common.serialization.Jackson3AdapterDataConverter"
  }

}
