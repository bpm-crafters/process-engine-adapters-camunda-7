package dev.bpmcrafters.example.common.adapter.in.process;

import dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions;
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ReadTaskHandler extends AbstractSynchronousTaskHandler {

  public ReadTaskHandler(TaskSubscriptionApi taskSubscriptionApi, ServiceTaskCompletionApi externalTaskCompletionApi) {
    super(taskSubscriptionApi, externalTaskCompletionApi, "read");
  }

  @Override
  public Map<String, Object> execute(TaskInformation taskInfo, Map<String, ?> variables) throws TaskHandlerException {




    return Map.of();
  }

}
