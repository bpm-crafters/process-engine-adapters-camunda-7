package dev.bpmcrafters.example.common.adapter.in.process;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Complex payload.
 */
@Data
@AllArgsConstructor
public class ComplexPayload {
  private String stringValue;
  private List<Foo> fooList;

  @AllArgsConstructor
  @Data
  static class Foo {
    private Integer intValue;
    private OffsetDateTime dateTime;
  }
}
