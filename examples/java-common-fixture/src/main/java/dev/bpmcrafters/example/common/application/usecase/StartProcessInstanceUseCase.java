package dev.bpmcrafters.example.common.application.usecase;

import dev.bpmcrafters.example.common.application.port.in.StartProcessInstanceInPort;
import dev.bpmcrafters.example.common.application.port.out.WorkflowOutPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;


@Component
@RequiredArgsConstructor
public class StartProcessInstanceUseCase implements StartProcessInstanceInPort {

  private final WorkflowOutPort workflowOutPort;

  @Override
  public CompletableFuture<String> startNew(String value, Integer intValue) {
    return CompletableFuture.supplyAsync(() -> workflowOutPort.startSimpleProcess(value, intValue));
  }
}
