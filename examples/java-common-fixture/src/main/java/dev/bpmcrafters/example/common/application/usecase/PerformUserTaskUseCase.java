package dev.bpmcrafters.example.common.application.usecase;

import dev.bpmcrafters.example.common.application.port.out.UserTaskOutPort;
import dev.bpmcrafters.example.common.application.port.in.PerformUserTaskInPort;
import dev.bpmcrafters.processengineapi.task.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class PerformUserTaskUseCase implements PerformUserTaskInPort {

  private final UserTaskOutPort userTaskPort;

  @Override
  public CompletableFuture<List<TaskInformation>> getUserTasks() {
    log.info("Retrieving tasks");
    return CompletableFuture.completedFuture(userTaskPort.getAllTasks());
  }

  @Override
  public CompletableFuture<Void> complete(String taskId, String value) {
    log.info("Completing task {}", taskId);
    return CompletableFuture.runAsync(() -> userTaskPort.complete(taskId, value));
  }

  @Override
  public CompletableFuture<Void> completeWithError(String taskId, String value) {
    // FIXME -> currently not working in C8: no API in the task-list-client!
    log.info("Completing task {} with error", taskId);
    return CompletableFuture.runAsync(() -> userTaskPort.completeWithError(taskId, value));
  }
}
