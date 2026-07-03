# Java Example to demonstrate usage of process API using Spring Boot 4 without Spin and with Jackson 3

This example is a test that we can invoke API defined in Kotlin from Java. It utilizes the API directly and runs an
embedded Camunda 7 engine in a Spring Boot 4 application.

The Camunda 7.24 Spring Boot starter still references the Spring Boot 3 Hibernate JPA auto-configuration class. The
embedded adapter starter contains a Spring Boot 4 compatibility auto-configuration that replaces that failing Camunda
auto-configuration path. The example also adds `spring-boot-starter-jdbc`, because the embedded engine needs a
`DataSource` and transaction manager.

This variant deliberately does not add Camunda Spin. That leaves the adapter free to auto-configure against the
Jackson 3 mapper provided by Spring Boot 4. The sample process still returns a complex object variable, so this example
relies on Camunda's default Java serialization for that variable instead of forcing JSON serialization.

## Features in the example

There are some features in the C7 adapter already. In addition, there are some features in the example:

- AbstractSynchronousTaskHandler to complete external tasks in a synchronous way
- In-Memory user task pool for retrieving infos about open user tasks

## How to run

- Build with Maven
- Start `JavaCamunda7ExampleApplication`
- Open http://localhost:8083/swagger-ui/index.html
- Start process
- Wait, wait, wait, check the logs, wait...
- Copy the resulting retrieved user task id
- Complete the user task with id
- Wait, wait, wait, check the logs, wait...
- Correlate message by providing the generated correlation key
- Hint: don't hurry, the error of correlation is not implemented yet (if you try it before both tasks are executed)
