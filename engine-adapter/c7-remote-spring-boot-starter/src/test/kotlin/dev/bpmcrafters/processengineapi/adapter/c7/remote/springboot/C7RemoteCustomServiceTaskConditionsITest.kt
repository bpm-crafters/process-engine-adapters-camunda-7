package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
  properties = [
    "dev.bpm-crafters.process-api.adapter.c7remote.service-tasks.delivery-strategy = custom",
    "dev.bpm-crafters.process-api.adapter.c7remote.user-tasks.delivery-strategy = disabled"
  ]
)
@ActiveProfiles("itest")
class C7RemoteCustomServiceTaskConditionsITest {

  @Autowired
  lateinit var context: ApplicationContext

  @Test
  fun `does not provide service completion API for custom delivery`() {
    assertThrows<NoSuchBeanDefinitionException> {
      context.getBean(PullServiceTaskDelivery::class.java)
    }
    assertThrows<NoSuchBeanDefinitionException> {
      context.getBean(ServiceTaskCompletionApi::class.java)
    }
  }
}
