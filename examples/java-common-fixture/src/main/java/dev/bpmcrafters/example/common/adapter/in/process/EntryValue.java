package dev.bpmcrafters.example.common.adapter.in.process;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EntryValue {

    private LocalDateTime time;
    private String taskId;
    private SomeEnum action;

}
