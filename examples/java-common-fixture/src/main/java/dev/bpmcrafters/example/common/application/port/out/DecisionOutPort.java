package dev.bpmcrafters.example.common.application.port.out;

import dev.bpmcrafters.example.common.application.model.Offer;
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation;
import java.util.List;

public interface DecisionOutPort {

  DeploymentInformation deployMainDecision();

  Integer evaluateSingleHitSingleOutputDecision(int id, int amount);

  Offer evaluateSingleHitMultiOutputDecision(int id, int amount);

  List<Offer> evaluateMultiHitMultiOutputDecision(int id, int amount);

}
