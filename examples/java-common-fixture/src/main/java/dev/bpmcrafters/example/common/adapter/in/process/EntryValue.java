package dev.bpmcrafters.example.common.adapter.in.process;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class EntryValue implements Serializable {

    private LocalDateTime time;
    private String taskId;
    private SomeEnum action;

}
