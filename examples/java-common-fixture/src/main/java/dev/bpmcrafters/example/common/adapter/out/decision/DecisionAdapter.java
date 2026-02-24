package dev.bpmcrafters.example.common.adapter.out.decision;

import dev.bpmcrafters.example.common.application.model.Offer;
import dev.bpmcrafters.example.common.application.port.out.DecisionOutPort;
import dev.bpmcrafters.processengineapi.decision.DecisionByRefEvaluationCommand;
import dev.bpmcrafters.processengineapi.decision.EvaluateDecisionApi;
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand;
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi;
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation;
import dev.bpmcrafters.processengineapi.deploy.NamedResource;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DecisionAdapter implements DecisionOutPort {

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private final DeploymentApi deploymentApi;
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private final EvaluateDecisionApi evaluateDecisionApi;

  @Override
  @SneakyThrows
  public DeploymentInformation deployMainDecision() {
    return deploymentApi.deploy(
      new DeployBundleCommand(
        List.of(
          NamedResource.fromClasspath(MainDecisionConst.DMN)
        ),
        null
      )
    ).get();
  }

  @Override
  @SneakyThrows
  public Integer evaluateSingleHitSingleOutputDecision(int id, int amount) {
    var payload = Map.of(
      "id", id,
      "amount", amount
    );
    var decisionEvaluationResult = evaluateDecisionApi.evaluateDecision(
      new DecisionByRefEvaluationCommand(
        MainDecisionConst.SINGLE_HIT_SINGLE_OUTPUT_DECISION,
        payload,
        Map.of()
      )
    ).get();

    return decisionEvaluationResult
      .asSingle()
      .asType(Integer.class);
  }

  @Override
  @SneakyThrows
  public Offer evaluateSingleHitMultiOutputDecision(int id, int amount) {
    var payload = Map.of(
      "id", id,
      "amount", amount
    );
    var decisionEvaluationResult = evaluateDecisionApi.evaluateDecision(
      new DecisionByRefEvaluationCommand(
        MainDecisionConst.MULTI_HIT_MULTI_OUTPUT_DECISION,
        payload,
        Map.of()
      )
    ).get();

    return decisionEvaluationResult
      .asSingle()
      .asType(Offer.class);
  }

  @Override
  @SneakyThrows
  public List<Offer> evaluateMultiHitMultiOutputDecision(int id, int amount) {
    var payload = Map.of(
      "id", id,
      "amount", amount
    );
    var decisionEvaluationResult = evaluateDecisionApi.evaluateDecision(
      new DecisionByRefEvaluationCommand(
        MainDecisionConst.MULTI_HIT_MULTI_OUTPUT_DECISION,
        payload,
        Map.of()
      )
    ).get();

    return decisionEvaluationResult
      .asList()
      .stream()
      .map(result -> result.asType(Offer.class))
      .toList();
  }

}
