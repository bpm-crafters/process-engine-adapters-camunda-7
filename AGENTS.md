# AGENTS.md

## 1. Overview

This repository contains the Camunda 7 implementation of the BPM Crafters Process Engine Adapter API. It is organized as a Maven multi-module workspace with library modules, Spring Boot starters, shared test support, and runnable examples.

## 2. Folder Structure

- `.github`: GitHub workflow and issue template configuration for CI and repository hygiene.
- `.mvn`: Maven wrapper support files used by `./mvnw`.
- `bom`: Maven BOM module publishing aligned dependency versions for consumers of the adapter.
- `docs`: user-facing adapter documentation, including quickstarts and embedded/remote reference guides.
- `engine-adapter`: main library modules.
  - `adapter-testing`: shared Kotlin test support and JGiven-based integration test utilities.
  - `c7-adapter-common`: shared functionality used by both implementations.
  - `c7-embedded-core`: embedded Camunda 7 adapter implementation, grouped by concerns such as process, task, decision, deploy, correlation, and shared engine helpers.
  - `c7-embedded-spring-boot-starter`: Spring Boot auto-configuration and scheduling/bootstrap wiring for the embedded adapter.
  - `c7-remote-core`: remote Camunda 7 adapter implementation, mirroring the embedded core structure where possible.
  - `c7-remote-spring-boot-starter`: Spring Boot auto-configuration, client wiring, and polling/subscription setup for the remote adapter.
- `examples`: runnable sample applications and shared example code.
  - `java-common-fixture`: shared Java example domain, ports, adapters, controllers, and task handlers.
  - `java-c7-embedded`: embedded example application and tests.
  - `java-c7-remote`: remote example application.
  - `java-c7-remote-sb4`: remote example variant for newer Spring Boot setup.
- `features`: project feature implementation plans, named with an issue number follwed by caption.
- `mvnw`: project Maven wrapper entrypoint.
- `pom.xml`: root aggregator POM, shared dependency management, build plugin defaults, and example profile activation.

Ignore generated build output under `target/`; those directories are checked into the working tree only as build artifacts and are not source locations for edits.

## 3. Working Agreements

- Respond in English; keep Java, Kotlin, Maven, Spring Boot, Camunda, and API terms in English.
- Before editing, inspect the matching embedded/remote or core/starter counterpart so changes follow existing symmetry instead of drifting module behavior.
- Prefer small, module-scoped changes; do not introduce new abstraction layers or compatibility shims unless the task explicitly requires them.
- Place new code beside the existing concern-based packages and follow established suffixes such as `*ApiImpl`, `*AutoConfiguration`, `*Properties`, `*Condition`, `*ITest`, and `*Test`.
- Do not edit `target/` outputs or vendored example web assets unless the task is explicitly about generated/static resources.
- Do not add tests, lint tasks, or formatting-only churn unless the user asks for them; when behavior spans mirrored modules, verify whether both sides need the same change.
- Add comments only for non-obvious invariants or engine-specific behavior; keep them short and factual.
- Ask for clarification when the target surface is ambiguous, especially whether work belongs in the published adapter modules, Spring Boot starters, shared test utilities, or examples.
