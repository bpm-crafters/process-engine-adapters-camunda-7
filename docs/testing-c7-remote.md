---
title: Camunda 7 Remote testing
---

# Camunda 7 Remote testing

Use `process-engine-adapter-camunda-platform-c7-remote-testing` with qualifier `c7remote`. Without
configuration it starts the Camunda 7 REST image in Testcontainers, so Docker must be available.
Use `@C7RemoteProcessTestEngine(url = "...")` to target an existing engine instead; externally
supplied engines are not stopped by the test integration.

## Maven dependencies

```xml
<dependencies>
  <dependency>
    <groupId>dev.bpm-crafters.process-engine-api-testing</groupId>
    <artifactId>process-engine-api-testing-jgiven</artifactId>
    <version>${process-engine-api-testing.version}</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
    <artifactId>process-engine-adapter-camunda-platform-c7-remote-testing</artifactId>
    <version>${process-engine-adapter-c7.version}</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

## First scenario

=== "Kotlin"

    ```kotlin
    @UseProcessTestInitializer(C7RemoteProcessTestInitializer.QUALIFIER)
    class OrderProcessTest : ProcessScenarioTest<OrderActions, OrderAssertions>() {
      @Test fun `order waits for approval`() {
        given {
          processIsDeployed("bpmn/order-approval.bpmn")
          processIsStartedByDefinition("order-approval")
        }
        then {
          processWaitsInUserTask("approve-order")
        }
      }
    }
    open class OrderActions : ActionStage<OrderActions>()
    open class OrderAssertions : AssertStage<OrderAssertions>()
    ```

=== "Java"

    ```java
    @UseProcessTestInitializer(C7RemoteProcessTestInitializer.QUALIFIER)
    class OrderProcessTest extends ProcessScenarioTest<OrderActions, OrderAssertions> {
      @Test
      void orderWaitsForApproval() {
        given()
          .processIsDeployed("bpmn/order-approval.bpmn")
          .processIsStartedByDefinition("order-approval");
        then()
          .processWaitsInUserTask("approve-order");
      }
    }
    class OrderActions extends ActionStage<OrderActions> {}
    class OrderAssertions extends AssertStage<OrderAssertions> {}
    ```

## Configuration

The provider supports the common deployment, start, user/external-task, message, signal, payload,
path, completion, and incident operations. Clock control is not supported for a remote engine, so
`timePasses` fails fast. Use an embedded integration for deterministic timer tests.

| Setting | Default | Purpose |
| --- | --- | --- |
| `@C7RemoteProcessTestEngine.url` | Empty | REST URL of an existing engine. If set, the test does not create or stop a container. |
| `@C7RemoteProcessTestEngine.image` | `bpmcrafters/camunda-bpm-platform:run-7.24.0` | Image started by Testcontainers when no URL is supplied. |
| Assertion polling | 100 ms interval, 10 s timeout | Fixed defaults used by the testing provider. |
| Time travel | Unsupported | Remote clock control is unavailable. |
