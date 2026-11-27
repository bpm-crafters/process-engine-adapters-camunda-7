package dev.bpmcrafters.example.common.application.usecase;

import dev.bpmcrafters.example.common.application.port.in.SignalInPort;
import dev.bpmcrafters.example.common.application.port.out.WorkflowOutPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Component
public class SignalUseCase implements SignalInPort {

  private final WorkflowOutPort workflowOutPort;

  @Override
  public CompletableFuture<Void> deliverSignal(String variableValue) {
    return CompletableFuture.runAsync(() -> workflowOutPort.deliverSignal(variableValue));
  }

}
