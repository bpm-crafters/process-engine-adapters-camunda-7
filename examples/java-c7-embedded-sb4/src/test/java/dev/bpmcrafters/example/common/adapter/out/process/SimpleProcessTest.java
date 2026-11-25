package dev.bpmcrafters.example.common.adapter.out.process;

import dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst;
import dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements;
import dev.bpmcrafters.processengineapi.testing.config.UseProcessTestInitializer;
import dev.bpmcrafters.processengineapi.testing.jgiven.ProcessScenarioTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Process-level regression test reused by the Spring Boot 4 embedded example.
 *
 * These scenarios exercise the same BPMN paths as the existing embedded example and make sure the
 * SB4 module still handles happy path, retries, BPMN errors, timeouts, and abnormal termination.
 */
@UseProcessTestInitializer(qualifier = "c7embedded")
public class SimpleProcessTest extends ProcessScenarioTest<SimpleProcessStages.ActionStage, SimpleProcessStages.AssertStage> {

  @BeforeEach
  public void setup() {
    given()
      .processIsDeployed(SimpleProcessWorkflowConst.BPMN);
  }

  @Test
  public void should_start_process_and_run_happy_path() {
    given()
      .simpleProcessStarted("test", 123);

    when()
      .serviceExecuteActionIsCompleted("value1")
      .and()
      .userTaskPerformTaskIsCompleted("user-task-value")
      .and()
      .serviceSendEmailIsCompleted()
      .and()
      .messageReceived("message-value");

    then()
      .processIsFinished()
      .and()
      .processHasPassed(Elements.END_EVENT);
  }

  @Test
  public void should_start_process_and_use_retries_if_service_task_fails() {
    given()
      .simpleProcessStarted("test", 123);

    when()
      .serviceExecuteActionIsFailed(1)
      .and()
      .serviceExecuteActionIsFailed(0);

    then()
      .processHasIncidents(java.util.Map.of());
  }

  @Test
  public void should_start_process_and_handle_service_task_error() {
    given()
      .simpleProcessStarted("test", 123);

    when()
      .serviceExecuteActionIsCompletedWithError()
      .and()
      .signalOccurred();

    then()
      .processIsFinished()
      .and()
      .processHasPassed(Elements.END_EVENT_ABNORMALLY);
  }

  @Test
  public void should_start_process_and_handle_user_task_timeout() {
    given()
      .simpleProcessStarted("test", 123);

    when()
      .serviceExecuteActionIsCompleted("value1")
      .and()
      .userTaskPerformTaskIsTimedOut()
      .and()
      .signalOccurred();

    then()
      .processIsFinished()
      .and()
      .processHasPassed(Elements.END_EVENT_ABNORMALLY);
  }

  @Test
  public void should_start_process_and_handle_user_task_error() {
    given()
      .simpleProcessStarted("test", 123);

    when()
      .serviceExecuteActionIsCompleted("value1")
      .and()
      .userTaskPerformTaskIsCompletedWithError("task error value")
      .and()
      .signalOccurred();

    then()
      .processIsFinished()
      .and()
      .processHasPassed(Elements.END_EVENT_ABNORMALLY);
  }

}
