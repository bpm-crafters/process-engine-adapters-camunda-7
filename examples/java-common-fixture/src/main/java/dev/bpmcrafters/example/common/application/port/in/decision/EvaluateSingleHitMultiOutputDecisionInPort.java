package dev.bpmcrafters.example.common.application.port.in.decision;

import dev.bpmcrafters.example.common.application.model.Offer;
import java.util.concurrent.CompletableFuture;

public interface EvaluateSingleHitMultiOutputDecisionInPort {

  CompletableFuture<Offer> evaluate(int id, int amount);
}
