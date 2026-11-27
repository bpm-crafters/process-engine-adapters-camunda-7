package dev.bpmcrafters.example.common.application.port.in.decision;

import java.util.concurrent.CompletableFuture;

public interface EvaluateSingleHitSingleOutputDecisionInPort {

  CompletableFuture<Integer> evaluate(int id, int amount);

}
