package dev.bpmcrafters.example.javac7;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.processengineapi.adapter.c7.common.serialization.AdapterDataConverter;
import dev.bpmcrafters.processengineapi.adapter.c7.common.serialization.Jackson2AdapterDataConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Embedded Boot 4 example with Camunda Spin.
 */
@SpringBootApplication
public class JavaCamunda7ExampleApplication {

  @Bean
  public AdapterDataConverter adapterDataConverter() {
    return new Jackson2AdapterDataConverter(new ObjectMapper().findAndRegisterModules());
  }

  public static void main(String[] args) {
    SpringApplication.run(JavaCamunda7ExampleApplication.class, args);
  }
}
