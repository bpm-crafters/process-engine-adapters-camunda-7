package dev.bpmcrafters.example.common.application.port.in.decision;

import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation;
import java.util.concurrent.CompletableFuture;

public interface DeployDecisionInPort {
  CompletableFuture<DeploymentInformation> deployDecision();
}
