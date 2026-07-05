package dev.bpmcrafters.example.javac7;

import dev.bpmcrafters.example.common.application.port.out.UserTaskOutPort;
import dev.bpmcrafters.example.common.application.port.out.WorkflowOutPort;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import java.time.Duration;
import java.util.UUID;
import org.camunda.bpm.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements.EVENT_RECEIVED_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
  classes = JavaCamunda7ExampleApplication.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "dev.bpm-crafters.process-api.adapter.c7embedded.service-tasks.schedule-delivery-fixed-rate-in-seconds=1",
    "dev.bpm-crafters.process-api.adapter.c7embedded.user-tasks.schedule-delivery-fixed-rate-in-seconds=1"
  }
)
class JavaCamunda7EmbeddedSpringBoot4Jackson3SmokeTest {

  @Autowired
  private WorkflowOutPort workflowOutPort;

  @Autowired
  private UserTaskOutPort userTaskOutPort;

  @Autowired
  private RuntimeService runtimeService;

  @Test
  void shouldStartAndCompleteSimpleProcessWithEmbeddedEngine() {
    var correlationKey = "sb4-jackson3-smoke-" + UUID.randomUUID();

    workflowOutPort.deploySimpleProcess();
    var processInstanceId = workflowOutPort.startSimpleProcess(correlationKey, 123);

    await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
      assertThat(userTaskOutPort.getAllTasks()).isNotEmpty()
    );

    TaskInformation userTask = userTaskOutPort.getAllTasks().get(0);
    userTaskOutPort.complete(userTask.getTaskId(), "user-task-value");

    await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
      assertThat(runtimeService
        .createExecutionQuery()
        .processInstanceId(processInstanceId)
        .activityId(EVENT_RECEIVED_MESSAGE)
        .count()
      ).isEqualTo(1L)
    );

    workflowOutPort.correlateMessage(correlationKey, "message-value");

    await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
      assertThat(runtimeService
        .createProcessInstanceQuery()
        .processInstanceId(processInstanceId)
        .count()
      ).isZero()
    );
  }
}
