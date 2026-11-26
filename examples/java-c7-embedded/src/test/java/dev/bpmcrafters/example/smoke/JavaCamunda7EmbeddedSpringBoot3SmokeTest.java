package dev.bpmcrafters.example.smoke;

import dev.bpmcrafters.example.javac7.JavaCamunda7ExampleApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.assertj.core.api.Assertions.assertThat;

class JavaCamunda7EmbeddedSpringBoot3SmokeTest {

  @Test
  void shouldStartApplicationAndServeOpenApi() throws Exception {
    try (var context = SpringApplication.run(
      JavaCamunda7ExampleApplication.class,
      "--server.port=0"
    ); var client = HttpClient.newHttpClient()) {
      var port = context.getEnvironment().getRequiredProperty("local.server.port");
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs"))
        .timeout(Duration.ofSeconds(15))
        .GET()
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("\"openapi\"");
    }
  }
}
