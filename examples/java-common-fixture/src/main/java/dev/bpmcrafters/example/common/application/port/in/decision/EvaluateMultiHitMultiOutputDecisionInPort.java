package dev.bpmcrafters.example.common.application.port.in.decision;

import dev.bpmcrafters.example.common.application.model.Offer;
import java.util.List;
import java.util.concurrent.Future;

public interface EvaluateMultiHitMultiOutputDecisionInPort {

  Future<List<Offer>> evaluate(int id, int amount);
}
