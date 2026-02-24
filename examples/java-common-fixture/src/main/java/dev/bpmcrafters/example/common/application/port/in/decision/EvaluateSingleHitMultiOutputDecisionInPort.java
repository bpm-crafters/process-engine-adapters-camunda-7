package dev.bpmcrafters.example.common.application.port.in.decision;

import dev.bpmcrafters.example.common.application.model.Offer;
import java.util.concurrent.Future;

public interface EvaluateSingleHitMultiOutputDecisionInPort {

  Future<Offer> evaluate(int id, int amount);
}
