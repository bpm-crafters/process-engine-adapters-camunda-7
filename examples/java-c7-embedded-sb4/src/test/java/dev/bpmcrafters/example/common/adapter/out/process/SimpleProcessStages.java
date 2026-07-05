package dev.bpmcrafters.example.common.adapter.out.process;

import com.tngtech.jgiven.annotation.As;
import com.tngtech.jgiven.annotation.ProvidedScenarioState;
import com.tngtech.jgiven.annotation.Quoted;
import dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements;
import dev.bpmcrafters.example.common.application.port.out.UserTaskOutPort;
import dev.bpmcrafters.example.common.application.port.out.WorkflowOutPort;
import dev.bpmcrafters.processengineapi.adapter.c7.embedded.testing.AbstractC7EmbeddedStage;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;

import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements.EVENT_RECEIVED_MESSAGE;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements.EVENT_SIGNAL_OCCURRED;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements.SERVICE_TASK_DO_ACTION_1;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements.SERVICE_TASK_DO_ACTION_2;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements.USER_TASK_PERFORM_TASK;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions.ERROR_ACTION_ERROR;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions.FAILURE_REASON;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions.JOB_TYPE_EXECUTE_ACTION_EXTERNAL;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions.JOB_TYPE_SEND_MESSAGE_EXTERNAL;

/**
 * JGiven stages for the embedded SB4 example.
 *
 * The stages intentionally use the public BPM Crafters ports instead of Camunda services directly
 * so the example verifies adapter wiring, task delivery, completion, message correlation, and signal
 * handling through the same surface an application would use.
 */
public class SimpleProcessStages {

  /**
   * Drives the process through the public workflow and user-task ports while the base embedded stage
   * provides Camunda-level assertions.
   */
  static class ActionStage extends AbstractC7EmbeddedStage<ActionStage> {

    @ProvidedScenarioState
    private WorkflowOutPort workflowOutPort;
    private UserTaskOutPort userTaskOutPort;
    private String correlationKey;

    @Override
    public void initialize() {
      workflowOutPort = new WorkflowAdapter(
        startProcessApi,
        signalApi,
        correlationApi,
        deploymentApi
      );

      userTaskOutPort = new UserTaskAdapter(
        userTaskSupport,
        userTaskCompletionApi
      );
    }

    @As("simple process started with value $value and intValue $intValue")
    public void simple_process_started(@Quoted String value, @Quoted Integer intValue) {
      String instanceId = workflowOutPort.startSimpleProcess(value, intValue);
      process_is_started(instanceId); // sets and init the process instance id or later process instance checks
      this.correlationKey = value;
      self();
    }

    @As("service task execute action completed with $value")
    public ActionStage service_execute_action_is_completed(@Quoted String value) {
      VariableMap payload = Variables.createVariables();
      payload.put("action1", value);

      return external_task_exists(JOB_TYPE_EXECUTE_ACTION_EXTERNAL, SERVICE_TASK_DO_ACTION_1)
        .and()
        .external_task_is_completed(JOB_TYPE_EXECUTE_ACTION_EXTERNAL, payload);
    }

    @As("service task execute action completed with error")
    public ActionStage service_execute_action_is_completed_with_error() {
      return external_task_exists(JOB_TYPE_EXECUTE_ACTION_EXTERNAL, SERVICE_TASK_DO_ACTION_1)
        .and()
        .external_task_is_completed_with_error(JOB_TYPE_EXECUTE_ACTION_EXTERNAL, ERROR_ACTION_ERROR, Variables.createVariables());
    }

    @As("service task execute action failed")
    public ActionStage service_execute_action_is_failed(int retries) {
      return external_task_exists(JOB_TYPE_EXECUTE_ACTION_EXTERNAL, SERVICE_TASK_DO_ACTION_1)
        .and()
        .external_task_is_failed(JOB_TYPE_EXECUTE_ACTION_EXTERNAL, FAILURE_REASON, retries);
    }

    @As("user task perform task completed with $value")
    public ActionStage user_task_perform_task_is_completed(String value) {
      process_waits_in(USER_TASK_PERFORM_TASK);
      userTaskOutPort.complete(task().getTaskId(), value);
      return self();
    }

    @As("user task perform task is timed out")
    public ActionStage user_task_perform_task_is_timed_out() {
      process_waits_in(USER_TASK_PERFORM_TASK);
      process_continues(Elements.TIMER_PASSED);
      return self();
    }

    @As("user task perform task is complete with an error with $value")
    public ActionStage user_task_perform_task_is_completed_with_error(String value) {
      process_waits_in(USER_TASK_PERFORM_TASK);
      userTaskOutPort.completeWithError(task().getTaskId(), value);
      return self();
    }

    @As("service task message send completed")
    public ActionStage service_send_email_is_completed() {
      return external_task_exists(JOB_TYPE_SEND_MESSAGE_EXTERNAL, SERVICE_TASK_DO_ACTION_2)
        .and()
        .external_task_is_completed(JOB_TYPE_SEND_MESSAGE_EXTERNAL, Variables.createVariables());
    }

    @As("message received with $value")
    public void message_received(String value) {
      process_waits_in_element(EVENT_RECEIVED_MESSAGE);
      workflowOutPort.correlateMessage(correlationKey, value);
      self();
    }

    @As("signal occurred")
    public void signal_occurred() {
      process_waits_in_element(EVENT_SIGNAL_OCCURRED);
      workflowOutPort.deliverSignal(correlationKey);
      self();
    }
  }

  /**
   * Uses the assertion helpers inherited from the embedded test support; the separate type keeps the
   * given/when and then parts of the JGiven scenario readable.
   */
  static class AssertStage extends AbstractC7EmbeddedStage<AssertStage> {

  }
}
