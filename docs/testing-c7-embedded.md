---
title: Camunda 7 Embedded testing
---

# Camunda 7 Embedded testing

Use `process-engine-adapter-camunda-platform-c7-embedded-testing` with the common process-testing
API. Its `c7embedded` initializer creates a standalone in-memory Camunda 7 engine for every
scenario; no Docker daemon or external engine is required.

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
    <artifactId>process-engine-adapter-camunda-platform-c7-embedded-testing</artifactId>
    <version>${process-engine-adapter-c7.version}</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

## First scenario

=== "Kotlin"

    ```kotlin
    @UseProcessTestInitializer(C7EmbeddedProcessTestInitializer.QUALIFIER)
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
    @UseProcessTestInitializer(C7EmbeddedProcessTestInitializer.QUALIFIER)
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

## Characteristics

It supports the common deployment, start, task, message, signal, payload, path, completion, and
incident operations. Time travel is supported: `timePasses` advances the engine clock and executes
due jobs. The integration is intended for fast, isolated process tests, not for validating a
production Spring Boot configuration.

| Setting | Value | Meaning |
| --- | --- | --- |
| Engine | Standalone in-memory Camunda 7 | A new engine is created for every scenario and closed afterwards. |
| History | Audit | Enables the history information used by path assertions. |
| Assertion polling | 25 ms interval, 5 s timeout | Fixed defaults used by the testing provider. |
| Time travel | Supported | `timePasses` advances the engine clock and executes due jobs. |
