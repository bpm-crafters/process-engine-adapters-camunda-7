# Examples

## C7 embedded

Module: `java-c7-embedded`

Port: 8080

## C7 embedded Spring Boot 4

Module: `java-c7-embedded-sb4`

Port: 8082

Uses Spring Boot `4.1.0` with Camunda 7 CE `7.24.0`. Camunda 7 CE `7.24.0` is the last public CE release on Maven
Central; maintained Enterprise patch versions need the consuming application to configure its private Camunda
repositories and dependency versions.

Serialization profile: embedded Camunda 7 with Spin JSON serialization and Jackson 2.

## C7 embedded Spring Boot 4 without Spin

Module: `java-c7-embedded-sb4-jackson3`

Port: 8083

Uses Spring Boot `4.1.0` with Camunda 7 CE `7.24.0`.

Serialization profile: embedded Camunda 7 without Spin and with Jackson 3.

## C7 remote Spring Boot 4

Module: `java-c7-remote-sb4`

Port: 8081

Uses Spring Boot `4.0.2` with Camunda 7 CE `7.24.0`.

Serialization profile: remote adapter with Jackson 3.

## Jackson configuration summary

- Spin JSON serialization works with Jackson 2 only.
- Embedded Camunda 7 with Spin should use the Jackson-2 example.
- Embedded Camunda 7 with Jackson 3 should not configure Spin JSON serialization.
- Remote adapter setups can use Jackson 3 because they are not bound to embedded Spin.

## C7 Remote

Camunda Run im Docker
