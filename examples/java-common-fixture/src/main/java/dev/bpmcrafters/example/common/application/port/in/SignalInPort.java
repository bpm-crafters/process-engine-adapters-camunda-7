package dev.bpmcrafters.example.common.application.port.in;

import java.util.concurrent.CompletableFuture;

public interface SignalInPort {
  /**
   * Delivers signal.
   * @param value string value.
   * @return nothing.
   */
  CompletableFuture<Void> deliverSignal(String value);
}
