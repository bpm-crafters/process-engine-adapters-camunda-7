package dev.bpmcrafters.example.common.application.usecase.decision;

import dev.bpmcrafters.example.common.application.model.Offer;
import dev.bpmcrafters.example.common.application.port.in.decision.EvaluateSingleHitMultiOutputDecisionInPort;
import dev.bpmcrafters.example.common.application.port.out.DecisionOutPort;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EvaluateSingleHitMultiOutputDecisionUseCase implements EvaluateSingleHitMultiOutputDecisionInPort {

  private final DecisionOutPort decisionOutPort;

  @Override
  public Future<Offer> evaluate(int id, int amount) {
    return CompletableFuture.completedFuture(
      decisionOutPort.evaluateSingleHitMultiOutputDecision(id, amount)
    );
  }

}
