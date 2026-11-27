package dev.bpmcrafters.example.common.application.port.in;

import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation;

import java.util.concurrent.CompletableFuture;

public interface DeployInPort {
  CompletableFuture<DeploymentInformation> deploy();
}
