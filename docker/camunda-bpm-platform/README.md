# Camunda 7 Community Edition images

Camunda no longer publishes Camunda 7 Community Edition images on Docker Hub. These images are built by [BPM Crafters](https://bpm-crafters.dev) from the official, unmodified CE distributions, so tests and examples can keep running against Camunda 7 CE.

Source and build workflow: [bpm-crafters/process-engine-adapters-camunda-7](https://github.com/bpm-crafters/process-engine-adapters-camunda-7/tree/develop/docker/camunda-bpm-platform)

## Tags

| Tag | Distribution |
|---|---|
| `run-7.24.0` | Camunda 7 Run 7.24.0 |

Tags follow `<distribution>-<version>`. Images are available for `linux/amd64` and `linux/arm64`.

## Usage

```bash
docker run -p 8080:8080 bpmcrafters/camunda-bpm-platform:run-7.24.0 ./camunda.sh --rest --webapps
```

- REST API: `http://localhost:8080/engine-rest`
- Webapps (with `--webapps`): `http://localhost:8080/camunda/app/`

`./camunda.sh` passes its arguments to Camunda Run, such as `--rest`, `--webapps` or `--production`. Configuration works through Spring Boot environment variables, for example `CAMUNDA_BPM_DEFAULT-SERIALIZATION-FORMAT=application/json`.

### Testcontainers

```kotlin
GenericContainer("bpmcrafters/camunda-bpm-platform:run-7.24.0")
  .withCommand("./camunda.sh", "--rest")
  .withExposedPorts(8080)
  .waitingFor(Wait.forHttp("/engine-rest/engine/").forPort(8080))
```

## Differences to the former `camunda/camunda-bpm-platform` images

- Only the bundled H2 database; no MySQL or PostgreSQL drivers and no `DB_*` variables.
- No `WAIT_FOR`, `tini` or JMX Prometheus agent.
- Based on `eclipse-temurin:17-jre`, running as user `1000`.

These images are meant for development and testing. Camunda 7 CE no longer receives patches; for production use, consider the Camunda 7 Enterprise Edition.

## License

Camunda 7 Community Edition is licensed under the [Apache License 2.0](https://github.com/camunda/camunda-bpm-platform/blob/master/LICENSE).
