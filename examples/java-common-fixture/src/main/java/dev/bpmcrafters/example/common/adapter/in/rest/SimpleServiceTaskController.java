package dev.bpmcrafters.example.common.adapter.in.rest;

import static dev.bpmcrafters.example.common.adapter.shared.AsyncResult.await;

import dev.bpmcrafters.example.common.application.port.in.*;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

import static org.springframework.http.ResponseEntity.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/simple-service-tasks")
public class SimpleServiceTaskController {

  private final DeployInPort deployPort;
  private final StartProcessInstanceInPort processInstancePort;
  private final PerformUserTaskInPort taskPort;
  private final CorrelateInPort correlatePort;
  private final SignalInPort signalPort;

  @PostMapping("/deploy")
  public ResponseEntity<String> deploy() {
    val info = await(deployPort.deploy());
    log.info("Deployed process {}", info.getDeploymentKey());
    return created(URI.create(info.getDeploymentKey())).build();
  }

  @PostMapping("/start-process")
  public ResponseEntity<String> startUseCase(Dto dto) {
    val processInstanceId = await(processInstancePort.startNew(dto.value, dto.intValue));
    log.info("Started process instance {}", processInstanceId);
    return created(URI.create(processInstanceId)).build();
  }

  @PostMapping("/correlate/{correlationKey}")
  public ResponseEntity<Void> correlateMessage(@PathVariable("correlationKey") String correlationKey, String value) {
    log.info("Correlated message using correlation key {}", correlationKey);
    await(correlatePort.correlateMessage(correlationKey, value));
    return noContent().build();
  }

  @PostMapping("/signal")
  public ResponseEntity<Void> sendSignal(String value) {
    log.info("Sending signal");
    await(signalPort.deliverSignal(value));
    return noContent().build();
  }

  @GetMapping("/tasks")
  public ResponseEntity<List<TaskInformation>> getTasks() {
    return ok(await(taskPort.getUserTasks()));
  }

  @PostMapping("/tasks/{taskId}/complete")
  public ResponseEntity<Void> complete(@PathVariable("taskId") String taskId, String value) {
    await(taskPort.complete(taskId, value));
    return noContent().build();
  }

  @PostMapping("/tasks/{taskId}/error")
  public ResponseEntity<Void> error(@PathVariable("taskId") String taskId, String value) {
    await(taskPort.completeWithError(taskId, value));
    return noContent().build();
  }

  public record Dto(
    String value,
    Integer intValue
  ) {
  }


}
