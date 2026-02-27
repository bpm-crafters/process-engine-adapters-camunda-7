package dev.bpmcrafters.example.common.adapter.in.rest;


import static org.springframework.http.ResponseEntity.created;
import static org.springframework.http.ResponseEntity.ok;

import dev.bpmcrafters.example.common.application.model.Offer;
import dev.bpmcrafters.example.common.application.port.in.decision.DeployDecisionInPort;
import dev.bpmcrafters.example.common.application.port.in.decision.EvaluateMultiHitMultiOutputDecisionInPort;
import dev.bpmcrafters.example.common.application.port.in.decision.EvaluateSingleHitMultiOutputDecisionInPort;
import dev.bpmcrafters.example.common.application.port.in.decision.EvaluateSingleHitSingleOutputDecisionInPort;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/main-decision")
public class MainDecisionController {

  private final DeployDecisionInPort deployDecisionInPort;
  private final EvaluateSingleHitSingleOutputDecisionInPort evaluateSingleHitSingleOutputDecisionInPort;
  private final EvaluateSingleHitMultiOutputDecisionInPort evaluateSingleHitMultiOutputDecisionInPort;
  private final EvaluateMultiHitMultiOutputDecisionInPort evaluateMultiHitMultiOutputDecisionInPort;

  @PostMapping("/deploy")
  @SneakyThrows
  public ResponseEntity<String> deploy() {
    val info = deployDecisionInPort.deployDecision().get();
    log.info("Deployed decision {}", info.getDeploymentKey());
    return created(URI.create(info.getDeploymentKey())).build();
  }

  @PostMapping("/evaluate-single-hit-single-output-decision")
  @SneakyThrows
  public ResponseEntity<Integer> evaluateSingleHitSingleOutputDecision(int id, int amount) {
    val decisionResult = evaluateSingleHitSingleOutputDecisionInPort.evaluate(id, amount).get();
    log.info("Evaluated single hit single output decision {}", decisionResult);
    return ok(decisionResult);
  }

  @PostMapping("/evaluate-single-hit-multiple-output-decision")
  @SneakyThrows
  public ResponseEntity<Offer> evaluateSingleHitMultipleOutputDecision(int id, int amount) {
    val decisionResult = evaluateSingleHitMultiOutputDecisionInPort.evaluate(id, amount).get();
    log.info("Evaluated single hit multi output decision {}", decisionResult);
    return ok(decisionResult);
  }

  @PostMapping("/evaluate-multi-hit-multi-output-decision")
  @SneakyThrows
  public ResponseEntity<List<Offer>> evaluateMultiHitMultiOutputDecision(int id, int amount) {
    val decisionResult = evaluateMultiHitMultiOutputDecisionInPort.evaluate(id, amount).get();
    log.info("Evaluated multi hit multi output decision {}", decisionResult);
    return ok(decisionResult);
  }

}
