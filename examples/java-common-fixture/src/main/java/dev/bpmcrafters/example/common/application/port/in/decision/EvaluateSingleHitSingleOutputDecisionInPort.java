package dev.bpmcrafters.example.common.application.port.in.decision;

import java.util.concurrent.Future;

public interface EvaluateSingleHitSingleOutputDecisionInPort {

  Future<Integer> evaluate(int id, int amount);

}
