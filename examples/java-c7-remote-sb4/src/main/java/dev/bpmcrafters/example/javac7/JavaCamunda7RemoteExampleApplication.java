package dev.bpmcrafters.example.javac7;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.ObjectMapperFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JavaCamunda7RemoteExampleApplication {

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }

  public static void main(String[] args) {
    SpringApplication.run(JavaCamunda7RemoteExampleApplication.class, args);
  }

}
