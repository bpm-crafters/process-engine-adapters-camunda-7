# Java Example to demonstrate usage of process API using Spring Boot 4 with Spin and Jackson 2

This example is a test that we can invoke API defined in Kotlin from Java. It utilizes the API directly and runs an
embedded Camunda 7 engine in a Spring Boot 4 application.

The example uses the public Camunda CE artifacts in version `7.24.0`. This is the last Camunda 7 Community Edition
release published on Maven Central, so newer maintained Camunda 7 patch versions require Enterprise repositories and
must be configured by the consuming application.

The Camunda 7.24 Spring Boot starter still references the Spring Boot 3 Hibernate JPA auto-configuration class. The
embedded adapter starter contains a Spring Boot 4 compatibility auto-configuration that replaces that failing Camunda
auto-configuration path. The example also adds `spring-boot-starter-jdbc`, because the embedded engine needs a
`DataSource` and transaction manager.

This variant keeps Camunda Spin with the JSON-Jackson data format on the classpath. Because Spin still depends on the
Jackson 2 ecosystem, the application provides an explicit `AdapterDataConverter` bean backed by a Jackson 2
`ObjectMapper`. That keeps the adapter serialization path aligned with Spin for the embedded Boot 4 setup.

## Features in the example

There are some features in the C7 adapter already. In addition, there are some features in the example: 

- AbstractSynchronousTaskHandler to complete external tasks in a synchronous way
- In-Memory user task pool for retrieving infos about open user tasks

## Process

![Service Task Process](src/main/resources/simple-process.png)


## How to run

- Build with Maven
- Start `JavaCamunda7ExampleApplication`
- Open http://localhost:8082/swagger-ui/index.html
- Start process
- Wait, wait, wait, check the logs, wait...
- Copy the resulting retrieved user task id
- Complete the user task with id
- Wait, wait, wait, check the logs, wait...
- Correlate message by providing the generated correlation key
- Hint: don't hurry, the error of correlation is not implemented yet (if you try it before both tasks are executed)

## How to run using IntelliJ test script
- Build with Maven
- Start `JavaCamunda7ExampleApplication`
- Run `simple-process-demo.http` script
- Analyze the results
- Run `simple-process-demo-failed-user.http` script
- Analyze the results
