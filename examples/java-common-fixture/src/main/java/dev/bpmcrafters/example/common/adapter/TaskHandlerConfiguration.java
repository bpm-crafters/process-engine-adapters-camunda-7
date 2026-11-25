package dev.bpmcrafters.example.common.adapter;

import dev.bpmcrafters.example.common.adapter.in.process.ExecuteActionTaskHandler;
import dev.bpmcrafters.example.common.adapter.in.process.SendingTaskHandler;
import dev.bpmcrafters.example.common.adapter.out.process.UserTaskAdapter;
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskHandlerConfiguration {

  @Bean
  public ExecuteActionTaskHandler doAction1(
      ServiceTaskCompletionApi serviceTaskCompletionApi
  ) {
    return new ExecuteActionTaskHandler(serviceTaskCompletionApi);
  }

  @Bean
  public SendingTaskHandler doAction2(ServiceTaskCompletionApi serviceTaskCompletionApi) {
    return new SendingTaskHandler(serviceTaskCompletionApi);
  }

  @Bean
  public UserTaskAdapter userTaskHandler(UserTaskSupport support, UserTaskCompletionApi userTaskCompletionApi) {
    return new UserTaskAdapter(support, userTaskCompletionApi);
  }

  @Bean
  public UserTaskSupport userTaskSupport() {
    return new UserTaskSupport();
  }

  @Bean
  public TaskSubscriptionLifecycleListener taskSubscriptionLifecycleListener(
      ExecuteActionTaskHandler executeActionTaskHandler,
      SendingTaskHandler sendingTaskHandler,
      UserTaskSupport userTaskSupport
  ) {
    return new TaskSubscriptionLifecycleListener(
      executeActionTaskHandler,
      sendingTaskHandler,
      userTaskSupport
    );
  }

}
