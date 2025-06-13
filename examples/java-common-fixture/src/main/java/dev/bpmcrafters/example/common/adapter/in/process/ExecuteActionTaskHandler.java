package dev.bpmcrafters.example.common.adapter.in.process;

import dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions;
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecuteActionTaskHandler extends AbstractSynchronousTaskHandler {

  public ExecuteActionTaskHandler(TaskSubscriptionApi taskSubscriptionApi, ServiceTaskCompletionApi externalTaskCompletionApi) {
    super(taskSubscriptionApi, externalTaskCompletionApi, "execute-action-external");
  }

  @Override
  public Map<String, Object> execute(TaskInformation taskInfo, Map<String, ?> variables) throws TaskHandlerException {
    log.info("[HANDLER EXECUTE ACTION EXTERNAL]: Working on task {}", taskInfo.getTaskId());
    Integer intValue = (Integer) variables.get("intValue");

    SomeComplexObject protocol = new SomeComplexObject(
      intValue,
      42
    );

    var entry = new EntryValue();
    entry.setTaskId(taskInfo.getTaskId());
    entry.setAction(SomeEnum.VAL23);
    entry.setTime(LocalDateTime.now());

    protocol.add(entry);

    if (intValue % 42 == 0) {
      log.info("[HANDLER EXECUTE ACTION EXTERNAL]: Detected answer to the Ultimate Question of Life, the Universe, and Everything");
      throw new TaskHandlerException(Expressions.ERROR_ACTION_ERROR); // will throw BPMN error
    }

    return Map.of(
      "action1", "value1",
      "replicationProtocol", protocol
    );
  }
}
