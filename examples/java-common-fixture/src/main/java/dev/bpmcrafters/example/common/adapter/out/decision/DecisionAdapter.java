package dev.bpmcrafters.example.common.adapter.out.decision;

import static dev.bpmcrafters.example.common.adapter.shared.AsyncResult.await;

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
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DecisionAdapter implements DecisionOutPort {

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private final DeploymentApi deploymentApi;
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private final EvaluateDecisionApi evaluateDecisionApi;

  @Override
  public DeploymentInformation deployMainDecision() {
    return await(deploymentApi.deploy(
      new DeployBundleCommand(
        List.of(
          NamedResource.fromClasspath(MainDecisionConst.DMN)
        ),
        null
      )
    ));
  }

  @Override
  public Integer evaluateSingleHitSingleOutputDecision(int id, int amount) {
    var payload = Map.of(
      "id", id,
      "amount", amount
    );
    var decisionEvaluationResult = await(evaluateDecisionApi.evaluateDecision(
      new DecisionByRefEvaluationCommand(
        MainDecisionConst.SINGLE_HIT_SINGLE_OUTPUT_DECISION,
        payload,
        Map.of()
      )
    ));

    return decisionEvaluationResult
      .asSingle()
      .asType(Integer.class);
  }

  @Override
  public Offer evaluateSingleHitMultiOutputDecision(int id, int amount) {
    var payload = Map.of(
      "id", id,
      "amount", amount
    );
    var decisionEvaluationResult = await(evaluateDecisionApi.evaluateDecision(
      new DecisionByRefEvaluationCommand(
        MainDecisionConst.MULTI_HIT_MULTI_OUTPUT_DECISION,
        payload,
        Map.of()
      )
    ));

    return decisionEvaluationResult
      .asSingle()
      .asType(Offer.class);
  }

  @Override
  public List<Offer> evaluateMultiHitMultiOutputDecision(int id, int amount) {
    var payload = Map.of(
      "id", id,
      "amount", amount
    );
    var decisionEvaluationResult = await(evaluateDecisionApi.evaluateDecision(
      new DecisionByRefEvaluationCommand(
        MainDecisionConst.MULTI_HIT_MULTI_OUTPUT_DECISION,
        payload,
        Map.of()
      )
    ));

    return decisionEvaluationResult
      .asList()
      .stream()
      .map(result -> result.asType(Offer.class))
      .toList();
  }

}
