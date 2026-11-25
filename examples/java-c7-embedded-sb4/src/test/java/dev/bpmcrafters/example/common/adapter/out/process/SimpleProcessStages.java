package dev.bpmcrafters.example.common.adapter.out.process;

import com.tngtech.jgiven.annotation.As;
import com.tngtech.jgiven.annotation.Quoted;
import dev.bpmcrafters.processengineapi.correlation.Correlation;
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements.EVENT_RECEIVED_MESSAGE;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements.EVENT_SIGNAL_OCCURRED;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements.USER_TASK_PERFORM_TASK;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions.ERROR_ACTION_ERROR;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions.FAILURE_REASON;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions.JOB_TYPE_EXECUTE_ACTION_EXTERNAL;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions.JOB_TYPE_SEND_MESSAGE_EXTERNAL;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions.MESSAGE_1;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions.SIGNAL_1;
import static dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.KEY;

/** JGiven stages backed by the public Process Engine API testing support. */
public class SimpleProcessStages {

  static class ActionStage extends dev.bpmcrafters.processengineapi.testing.jgiven.ActionStage<ActionStage> {
    private String correlationKey;

    @As("simple process started with value $value and intValue $intValue")
    public ActionStage simpleProcessStarted(@Quoted String value, @Quoted Integer intValue) {
      processIsStartedByDefinition(KEY, Map.of("stringValue", value, "intValue", intValue, "listVariable", List.of("element1", "element2")));
      correlationKey = value;
      return self();
    }

    @As("service task execute action completed with $value")
    public ActionStage serviceExecuteActionIsCompleted(@Quoted String value) {
      return externalTaskIsCompleted(JOB_TYPE_EXECUTE_ACTION_EXTERNAL, Map.of("action1", value));
    }

    @As("service task execute action completed with error")
    public ActionStage serviceExecuteActionIsCompletedWithError() {
      return externalTaskIsCompletedWithError(JOB_TYPE_EXECUTE_ACTION_EXTERNAL, ERROR_ACTION_ERROR, Map.of(), null);
    }

    @As("service task execute action failed")
    public ActionStage serviceExecuteActionIsFailed(int retries) {
      externalTaskIsSelected(JOB_TYPE_EXECUTE_ACTION_EXTERNAL);
      return externalTaskHasFailed(FAILURE_REASON, retries);
    }

    @As("user task perform task is assigned to kermit and completed with $value")
    public ActionStage userTaskPerformTaskIsCompleted(String value) {
      processWaitsInUserTask(USER_TASK_PERFORM_TASK);
      if (!"kermit".equals(requireCurrentTask().getMeta().get("assignee"))) {
        throw new AssertionError("Expected selected task to be assigned to kermit");
      }
      return userTaskIsCompleted(Map.of("some-user-value", value));
    }

    @As("user task perform task is timed out")
    public ActionStage userTaskPerformTaskIsTimedOut() {
      processWaitsInUserTask(USER_TASK_PERFORM_TASK);
      return timePasses(Duration.ofMinutes(1));
    }

    @As("user task perform task is completed with an error with $value")
    public ActionStage userTaskPerformTaskIsCompletedWithError(String value) {
      processWaitsInUserTask(USER_TASK_PERFORM_TASK);
      runtimeApis().userTaskCompletionApi().completeTaskByError(
        new CompleteTaskByErrorCmd(requireCurrentTask().getTaskId(), "user_error", "some error", Map.of("some-user-value", value))
      ).join();
      return self();
    }

    @As("service task message send completed")
    public ActionStage serviceSendEmailIsCompleted() {
      return externalTaskIsCompleted(JOB_TYPE_SEND_MESSAGE_EXTERNAL);
    }

    @As("message received with $value")
    public ActionStage messageReceived(String value) {
      processWaitsInElement(EVENT_RECEIVED_MESSAGE);
      return messageIsCorrelated(MESSAGE_1, Correlation.withKey(correlationKey), Map.of("message-delivered-value", value));
    }

    @As("signal occurred")
    public ActionStage signalOccurred() {
      processWaitsInElement(EVENT_SIGNAL_OCCURRED);
      return signalIsSent(SIGNAL_1, Map.of("signal-delivered-value", correlationKey));
    }
  }

  static class AssertStage extends dev.bpmcrafters.processengineapi.testing.jgiven.AssertStage<AssertStage> {
  }
}
