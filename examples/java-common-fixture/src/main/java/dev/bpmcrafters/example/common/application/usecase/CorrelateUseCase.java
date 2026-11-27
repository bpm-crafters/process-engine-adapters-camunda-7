package dev.bpmcrafters.example.common.application.usecase;

import dev.bpmcrafters.example.common.application.port.in.CorrelateInPort;
import dev.bpmcrafters.example.common.application.port.out.WorkflowOutPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;


@Component
@RequiredArgsConstructor
public class CorrelateUseCase implements CorrelateInPort {

  private final WorkflowOutPort workflowOutPort;

  @Override
  public CompletableFuture<Void> correlateMessage(String correlationValue, String variableValue) {
    return CompletableFuture.runAsync(() -> workflowOutPort.correlateMessage(correlationValue, variableValue));
  }
}
