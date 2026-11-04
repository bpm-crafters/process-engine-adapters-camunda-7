package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

class Camunda7RunTestContainer(tag: String) : GenericContainer<Camunda7RunTestContainer>("ghcr.io/bpm-crafters/camunda-bpm-run:$tag") {

  init {
    withCommand("./camunda.sh", "--rest")
    withEnv("CAMUNDA_BPM_DEFAULT-SERIALIZATION-FORMAT", "application/json")
    withExposedPorts(8080)
    waitingFor(Wait
      .forHttp("/engine-rest/engine/")
      .forPort(8080)
    )
  }

}
