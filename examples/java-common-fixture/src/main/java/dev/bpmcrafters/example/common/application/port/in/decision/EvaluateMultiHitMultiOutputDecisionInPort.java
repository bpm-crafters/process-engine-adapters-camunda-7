package dev.bpmcrafters.example.common.application.port.in.decision;

import dev.bpmcrafters.example.common.application.model.Offer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EvaluateMultiHitMultiOutputDecisionInPort {

  CompletableFuture<List<Offer>> evaluate(int id, int amount);
}
