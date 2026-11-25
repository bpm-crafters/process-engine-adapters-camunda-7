package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.completion.UserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.delivery.pull.PullUserTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c7.remote.task.modification.UserTaskModificationApiImpl
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskModificationApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
  properties = [
    "dev.bpm-crafters.process-api.adapter.c7remote.service-tasks.delivery-strategy = disabled",
    "dev.bpm-crafters.process-api.adapter.c7remote.user-tasks.delivery-strategy = custom"
  ]
)
@ActiveProfiles("itest")
class C7RemoteCustomUserTaskCommandApiITest {

  @Autowired
  lateinit var context: ApplicationContext

  @Test
  fun `provides user task command APIs without adapter-managed delivery`() {
    assertThrows<NoSuchBeanDefinitionException> {
      context.getBean(PullUserTaskDelivery::class.java)
    }
    assertThat(context.getBean(UserTaskCompletionApi::class.java)).isInstanceOf(UserTaskCompletionApiImpl::class.java)
    assertThat(context.getBean(UserTaskModificationApi::class.java)).isInstanceOf(UserTaskModificationApiImpl::class.java)
  }
}
