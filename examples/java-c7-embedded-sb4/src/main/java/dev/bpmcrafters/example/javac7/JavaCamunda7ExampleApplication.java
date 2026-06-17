package dev.bpmcrafters.example.javac7;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot 4 embedded Camunda application.
 *
 * Keeping the application class empty is intentional: the example should prove that the starter
 * auto-configures the embedded engine and BPM Crafters API without project-local workaround beans.
 */
@SpringBootApplication
public class JavaCamunda7ExampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(JavaCamunda7ExampleApplication.class, args);
  }
}
