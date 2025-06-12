package dev.bpmcrafters.example.common.adapter.in.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Value
public class SomeComplexObject {

  int oneNumber;
  int otherNumber;
  List<EntryValue> entries = new ArrayList<>();

  public void add(String taskId, SomeEnum action) {
    EntryValue entry = new EntryValue();
    entry.setTaskId(taskId);
    entry.setAction(action);
    entry.setTime(LocalDateTime.now());
    this.add(entry);
  }

  public void add(EntryValue entry) {
    entries.add(entry);
  }

  @JsonIgnore
  public boolean isEmpty() {
    return entries.isEmpty();
  }

}
