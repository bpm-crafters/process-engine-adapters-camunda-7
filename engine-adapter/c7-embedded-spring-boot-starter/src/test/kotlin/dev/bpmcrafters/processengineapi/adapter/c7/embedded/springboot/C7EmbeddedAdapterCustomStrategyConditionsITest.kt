package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.task.delivery.pull.EmbeddedPullUserTaskDelivery
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskModificationApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
  properties = [
    "dev.bpm-crafters.process-api.adapter.c7embedded.service-tasks.delivery-strategy = custom",
    "dev.bpm-crafters.process-api.adapter.c7embedded.user-tasks.delivery-strategy = custom"
  ]
)
@ActiveProfiles("itest")
class C7EmbeddedAdapterCustomStrategyConditionsITest {

  @Autowired
  lateinit var context: ApplicationContext

  @Test
  fun `provides command APIs without adapter-managed delivery`() {
    assertThrows<NoSuchBeanDefinitionException> {
      context.getBean<EmbeddedPullServiceTaskDelivery>()
    }
    assertThrows<NoSuchBeanDefinitionException> {
      context.getBean<EmbeddedPullUserTaskDelivery>()
    }
    assertThat(context.getBean<ServiceTaskCompletionApi>()).isNotNull()
    assertThat(context.getBean<UserTaskCompletionApi>()).isNotNull()
    assertThat(context.getBean<UserTaskModificationApi>()).isNotNull()
  }
}
