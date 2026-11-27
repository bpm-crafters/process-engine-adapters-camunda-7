package dev.bpmcrafters.example.common.adapter;

import static dev.bpmcrafters.example.common.adapter.shared.AsyncResult.await;

import dev.bpmcrafters.example.common.adapter.in.process.ExecuteActionTaskHandler;
import dev.bpmcrafters.example.common.adapter.in.process.SendingTaskHandler;
import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.springboot.lifecycle.ProcessApplicationStoppingEvent;
import dev.bpmcrafters.processengineapi.springboot.lifecycle.TaskSubscriptionRegistrationEvent;
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd;
import dev.bpmcrafters.processengineapi.task.TaskSubscription;
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi;
import dev.bpmcrafters.processengineapi.task.TaskType;
import dev.bpmcrafters.processengineapi.task.UnsubscribeFromTaskCmd;
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport;
import org.springframework.context.event.EventListener;

public class TaskSubscriptionLifecycleListener {

  private final ExecuteActionTaskHandler executeActionTaskHandler;
  private final SendingTaskHandler sendingTaskHandler;
  private final UserTaskSupport userTaskSupport;
  private TaskSubscriptionApi taskSubscriptionApi;
  private TaskSubscription userTaskSubscription;

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
    userTaskSubscription = await(taskSubscriptionApi.subscribeForTask(new SubscribeForTaskCmd(
        CommonRestrictions.builder().build(),
        TaskType.USER,
        null,
        null,
        userTaskSupport::onTaskDelivery,
        userTaskSupport::onTaskRemoval
    )));
  }

  @EventListener
  public void unregister(ProcessApplicationStoppingEvent event) {
    executeActionTaskHandler.unregister();
    sendingTaskHandler.unregister();
    if (userTaskSubscription != null) {
      await(taskSubscriptionApi.unsubscribe(new UnsubscribeFromTaskCmd(userTaskSubscription)));
      userTaskSubscription = null;
    }
  }
}
