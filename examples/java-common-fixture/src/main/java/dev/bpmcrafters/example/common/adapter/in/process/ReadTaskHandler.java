package dev.bpmcrafters.example.common.adapter.in.process;

import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class ReadTaskHandler extends AbstractSynchronousTaskHandler {

  public ReadTaskHandler(ServiceTaskCompletionApi externalTaskCompletionApi) {
    super(externalTaskCompletionApi, "read");
  }

  @Override
  public Map<String, Object> execute(TaskInformation taskInfo, Map<String, ?> variables) throws TaskHandlerException {
    return Map.of();
  }

}
