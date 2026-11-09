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
docker run \
  -p 8080:8080 \
  -e CAMUNDA_RUN_REST=true \
  -e CAMUNDA_RUN_WEBAPPS=true \
  bpmcrafters/camunda-bpm-platform:run-7.24.0
```

- REST API: `http://localhost:8080/engine-rest`
- Webapps (with `--webapps`): `http://localhost:8080/camunda/app/`

## Environment variables

Use the following environment variables to select the Camunda Run components without replacing the image command. This is the recommended approach for Docker Compose and Kubernetes deployments.

| Environment variable | Camunda Run argument when set to `true` |
|---|---|
| `CAMUNDA_RUN_REST` | `--rest` |
| `CAMUNDA_RUN_WEBAPPS` | `--webapps` |
| `CAMUNDA_RUN_EXAMPLE` | `--example` |
| `CAMUNDA_RUN_PRODUCTION` | `--production` |
| `CAMUNDA_RUN_OAUTH2` | `--oauth2` |

Each variable accepts `true` or `false`. Unset variables retain Camunda Run's normal defaults. When one or more component variables (`REST`, `WEBAPPS`, or `EXAMPLE`) are `true`, only those selected components are enabled. A value of `false` adds no argument: setting all component variables to `false` therefore still starts Camunda Run's defaults. `CAMUNDA_RUN_PRODUCTION=true` enables production mode; `CAMUNDA_RUN_OAUTH2=true` enables the Spring Security OAuth2 integration.

Other Spring Boot and Camunda properties can continue to be configured through their environment variables, for example `CAMUNDA_BPM_DEFAULT-SERIALIZATION-FORMAT=application/json`.

### Command arguments

Direct command arguments remain supported for one-off runs and existing integrations:

```bash
docker run -p 8080:8080 bpmcrafters/camunda-bpm-platform:run-7.24.0 ./camunda.sh --rest --webapps
```

Explicit `--rest`, `--webapps`, and `--example` arguments take precedence as a group over the corresponding `CAMUNDA_RUN_*` component variables. Explicit `--production` and `--oauth2` arguments take precedence over their corresponding environment variables. The bundled Camunda Run 7.24 launcher does not support `--swaggerui`.

## Volumes

The image supports independent, optional mounts for external configuration and JAR extensions. Both are evaluated during startup; restart the container after changing either mount.

| Mount path | Contents | Typical Kubernetes source |
|---|---|---|
| `/camunda/configuration/external` | `application.yml`, `application.yaml`, and profile-specific Spring Boot configuration | ConfigMap or Secret |
| `/camunda/extensions` | Direct `*.jar` files, such as JDBC drivers or Camunda Run extensions | PVC populated by an init container |

Configuration is added through Spring Boot's `SPRING_CONFIG_ADDITIONAL_LOCATION`, so it supplements Camunda Run's bundled defaults instead of replacing them. If `SPRING_CONFIG_ADDITIONAL_LOCATION` is already set, the mounted location is appended and takes precedence over that additional file location. JAR files are linked into Camunda Run's `configuration/userlib/` directory and are therefore loaded using the normal Camunda Run extension mechanism.

```bash
docker run \
  -p 8080:8080 \
  -v "$PWD/config:/camunda/configuration/external:ro" \
  -v "$PWD/extensions:/camunda/extensions:ro" \
  -e CAMUNDA_RUN_REST=true \
  -e CAMUNDA_RUN_WEBAPPS=true \
  bpmcrafters/camunda-bpm-platform:run-7.24.0
```

The container runs as UID `1000`; mounted files must be readable by that user. The image must also be able to write `/camunda/configuration/userlib` while preparing extension links, so `readOnlyRootFilesystem: true` and arbitrary runtime UIDs are not supported for JAR provisioning. A mounted JAR cannot use the name of an existing `userlib` entry; startup fails with an actionable collision error instead of replacing a bundled library. Treat JAR mounts as trusted executable code and mount them read-only whenever possible. Extension JARs must be compatible with Camunda Run 7.24 and Java 17.

### Verify JDBC JAR provisioning with PostgreSQL

The repository contains a self-contained PostgreSQL verification fixture in [`docker/camunda-bpm-platform/test`](https://github.com/bpm-crafters/process-engine-adapters-camunda-7/tree/develop/docker/camunda-bpm-platform/test). From a repository checkout, run:

```bash
./docker/camunda-bpm-platform/test/verify-postgresql.sh
```

The script downloads the PostgreSQL JDBC driver into its `extensions/` mount, starts PostgreSQL and the published Camunda Run image with Docker Compose, waits for the REST engine endpoint, and verifies that Camunda created its tables in PostgreSQL. Set `POSTGRESQL_JDBC_VERSION`, `CAMUNDA_TEST_PORT`, or `CAMUNDA_IMAGE` if needed.

To validate local image changes, make the script build the normal image tag before running Compose:

```bash
BUILD_IMAGE=true ./docker/camunda-bpm-platform/test/verify-postgresql.sh
```

To run the setup manually, put a PostgreSQL JDBC driver JAR in `extensions/` and create `config/application.yml`:

```text
extensions/
  postgresql-<version>.jar
config/
  application.yml
```

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/camunda
    username: camunda
    password: camunda
    driver-class-name: org.postgresql.Driver
```

Start PostgreSQL and Camunda Run on the same Docker network:

```bash
docker network create camunda-net

docker run --detach --rm \
  --name postgres \
  --network camunda-net \
  -e POSTGRES_DB=camunda \
  -e POSTGRES_USER=camunda \
  -e POSTGRES_PASSWORD=camunda \
  postgres:16

docker run --detach --rm \
  --name camunda \
  --network camunda-net \
  -p 8080:8080 \
  -v "$PWD/config:/camunda/configuration/external:ro" \
  -v "$PWD/extensions:/camunda/extensions:ro" \
  -e CAMUNDA_RUN_REST=true \
  bpmcrafters/camunda-bpm-platform:run-7.24.0
```

Wait for the engine endpoint to respond:

```bash
curl --fail http://localhost:8080/engine-rest/engine/
```

Then confirm that Camunda tables were created in PostgreSQL:

```bash
docker exec postgres psql -U camunda -d camunda -c '\dt act_*'
```

If the JAR is missing or incompatible, startup fails before the endpoint is available with a driver-class loading error for `org.postgresql.Driver`. Stop the example with `docker stop camunda postgres` and remove the network with `docker network rm camunda-net`.

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
