package dev.bpmcrafters.example.common.adapter;

import dev.bpmcrafters.example.common.adapter.in.process.ExecuteActionTaskHandler;
import dev.bpmcrafters.example.common.adapter.in.process.SendingTaskHandler;
import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.springboot.lifecycle.ProcessApplicationStoppingEvent;
import dev.bpmcrafters.processengineapi.springboot.lifecycle.TaskSubscriptionRegistrationEvent;
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi;
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport;
import org.springframework.context.event.EventListener;

public class TaskSubscriptionLifecycleListener {

  private final ExecuteActionTaskHandler executeActionTaskHandler;
  private final SendingTaskHandler sendingTaskHandler;
  private final UserTaskSupport userTaskSupport;
  private TaskSubscriptionApi taskSubscriptionApi;

  public TaskSubscriptionLifecycleListener(
      ExecuteActionTaskHandler executeActionTaskHandler,
      SendingTaskHandler sendingTaskHandler,
      UserTaskSupport userTaskSupport
  ) {
    this.executeActionTaskHandler = executeActionTaskHandler;
    this.sendingTaskHandler = sendingTaskHandler;
    this.userTaskSupport = userTaskSupport;
  }

  @EventListener
  public void register(TaskSubscriptionRegistrationEvent event) {
    taskSubscriptionApi = event.getTaskSubscriptionApi();
    executeActionTaskHandler.register(taskSubscriptionApi);
    sendingTaskHandler.register(taskSubscriptionApi);
    userTaskSupport.subscribe(taskSubscriptionApi, CommonRestrictions.builder().build(), null, null);
  }

  @EventListener
  public void unregister(ProcessApplicationStoppingEvent event) {
    executeActionTaskHandler.unregister();
    sendingTaskHandler.unregister();
    if (taskSubscriptionApi != null) {
      userTaskSupport.unsubscribe(taskSubscriptionApi);
    }
  }
}
