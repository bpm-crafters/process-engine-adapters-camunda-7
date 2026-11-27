package dev.bpmcrafters.example.common.application.port.in;

import java.util.concurrent.CompletableFuture;

public interface CorrelateInPort {

  /**
   * Correlates message.
   * @param correlationKey correlation key.
   * @param value string value.
   * @return nothing.
   */
  CompletableFuture<Void> correlateMessage(String correlationKey, String value);
}
