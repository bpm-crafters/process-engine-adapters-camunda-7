package dev.bpmcrafters.example.common.application.port.in.decision;

import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation;
import java.util.concurrent.Future;

public interface DeployDecisionInPort {
  Future<DeploymentInformation> deployDecision();
}
