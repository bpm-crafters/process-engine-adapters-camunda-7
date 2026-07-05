package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.common.serialization.AdapterDataConverter
import dev.bpmcrafters.processengineapi.adapter.c7.common.serialization.Jackson2AdapterDataConverter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("itest")
class C7EmbeddedSerializationAutoConfigurationTest {

  @Autowired
  lateinit var dataConverter: AdapterDataConverter

  @Test
  fun `should wire jackson 2 data converter when jackson 2 mapper bean exists`() {
    assertThat(dataConverter).isInstanceOf(Jackson2AdapterDataConverter::class.java)
  }

}
