package dev.bpmcrafters.processengineapi.adapter.c7.common.serialization

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper as jackson2ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper as jackson3ObjectMapper

class AdapterDataConverterTest {

  data class Offer(
    val score: Double,
    val message: String
  )

  private val converters = listOf(
    Jackson2AdapterDataConverter(jackson2ObjectMapper()),
    Jackson3AdapterDataConverter(jackson3ObjectMapper()),
  )

  @Test
  fun `should convert scalar values`() {
    converters.forEach { converter ->
      assertThat(converter.convert(10, Integer::class.java)).isEqualTo(10)
    }
  }

  @Test
  fun `should convert maps to kotlin objects`() {
    converters.forEach { converter ->
      assertThat(
        converter.convert(
          mapOf("score" to 23.5, "message" to "ok"),
          Offer::class.java,
        )
      ).isEqualTo(Offer(23.5, "ok"))
    }
  }

  @Test
  fun `should keep null values nullable`() {
    converters.forEach { converter ->
      assertThat(converter.convert(null, String::class.java)).isNull()
    }
  }

}
