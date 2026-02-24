package dev.bpmcrafters.example.common.application.usecase.decision;

import dev.bpmcrafters.example.common.application.port.in.decision.EvaluateSingleHitSingleOutputDecisionInPort;
import dev.bpmcrafters.example.common.application.port.out.DecisionOutPort;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EvaluateSingleHitSingleOutputDecisionUseCase implements EvaluateSingleHitSingleOutputDecisionInPort {

  private final DecisionOutPort decisionOutPort;

  @Override
  public Future<Integer> evaluate(int id, int amount) {
    return CompletableFuture.completedFuture(
      decisionOutPort.evaluateSingleHitSingleOutputDecision(id, amount)
    );
  }

}
