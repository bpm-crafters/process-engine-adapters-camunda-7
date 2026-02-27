package dev.bpmcrafters.example.common.application.usecase.decision;

import dev.bpmcrafters.example.common.application.port.in.decision.DeployDecisionInPort;
import dev.bpmcrafters.example.common.application.port.out.DecisionOutPort;
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeployDecisionUseCase implements DeployDecisionInPort {

  private final DecisionOutPort decisionOutPort;

  @Override
  public Future<DeploymentInformation> deployDecision() {
    return CompletableFuture.completedFuture(
      decisionOutPort.deployMainDecision()
    );
  }

}
