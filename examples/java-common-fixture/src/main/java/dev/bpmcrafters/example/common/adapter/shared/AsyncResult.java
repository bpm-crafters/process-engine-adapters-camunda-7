package dev.bpmcrafters.example.common.adapter.shared;

import lombok.Lombok;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class AsyncResult {
  private AsyncResult() {
  }

  public static <T> T await(CompletableFuture<T> future) {
    try {
      return future.join();
    } catch (CompletionException exception) {
      Throwable failure = exception;
      var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
      while ((failure instanceof CompletionException || failure instanceof ExecutionException)
          && failure.getCause() != null && visited.add(failure)) {
        failure = failure.getCause();
      }
      throw Lombok.sneakyThrow(failure);
    }
  }
}
