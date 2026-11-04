# Feature 279/280/281: Camunda Run image extensions

## Goal

Allow the custom `bpmcrafters/camunda-bpm-platform:run-7.24.0` image to load trusted extensions without deriving a new image:

- JAR files supplied by a mounted volume, including JDBC drivers and Camunda Run extensions (#279)
- additional Spring Boot configuration supplied by its own mounted directory (#280)
- environment-variable equivalents for Camunda Run launch-mode switches, avoiding per-deployment command overrides (#281)

Configuration and JARs use separate mounts because they normally have different delivery and lifecycle mechanisms. Mounted content is evaluated once during container startup. Changing it requires a container restart.

## Current State

- The image is defined by `docker/camunda-bpm-platform/run/Dockerfile`.
- Its `camunda.sh` wrapper delegates directly to Camunda Run's `internal/run.sh start` script.
- Camunda Run already loads extension JARs from `configuration/userlib/`.
- The image currently exposes configuration through Spring Boot environment variables only; it has no documented external extension-volume contract.
- Image users must set `command: ./camunda.sh ...` to pass Run's `--rest`, `--webapps`, `--swaggerui`, or `--production` arguments.

## Target Design

### 1. Provide separate stable mount points

Reserve these paths in the image:

- `/camunda/configuration/external` for externally supplied Spring Boot configuration
- `/camunda/extensions` for externally supplied JARs

Both paths are optional and are normally mounted read-only. Keeping them separate lets a ConfigMap provide configuration while a PVC, an init container, or an image-population workflow provides JARs.

```text
/camunda/configuration/external/
  application.yml

/camunda/extensions/
  postgresql-42.x.jar
  oidc-extension.jar
```

The configuration mount contains Spring Boot configuration using conventional `application.yml`, `application.yaml`, or profile-specific names such as `application-production.yml`.

Every direct `*.jar` file in the JAR mount is an extension library. Nested directory scanning is deliberately out of scope for the first version so the classpath and error reporting remain predictable.

### 2. Extend the startup wrapper

Keep the image's `CMD ["./camunda.sh"]` and make `camunda.sh` responsible for preparing the mounted content before it delegates to Camunda Run.

At startup the wrapper must:

1. Verify that the two mount points are directories when present; fail early with a clear message otherwise.
2. Discover `/camunda/extensions/*.jar` deterministically.
3. Make the discovered JARs available in Camunda Run's `/camunda/configuration/userlib/` without modifying the mounted volume. Symbolic links are preferred because the image filesystem is writable by its existing UID `1000` and links avoid copying potentially large JARs.
4. Set `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/camunda/configuration/external/` when the configuration mount exists, preserving any caller-provided value by appending this location.
5. `exec /camunda/internal/run.sh start "$@"` as today.

Do not replace Spring's configuration location. An *additional* location keeps Camunda Run's bundled defaults active while allowing the mounted configuration to override them.

### 3. Keep Camunda Run conventions intact

The wrapper must use Camunda Run's existing `configuration/userlib/` convention instead of creating a second Java classpath implementation. This keeps JDBC drivers, process-engine plugins, webapp plugins, and other supported Run extensions on the same loading path as an unpacked Camunda Run distribution.

The implementation must not alter `internal/run.sh` from the upstream distribution.

### 4. Publish the user contract

Update `docker/camunda-bpm-platform/README.md` with:

- the directory layout above
- a `docker run` example with independent configuration and JAR mounts
- Docker Compose and Kubernetes examples that mount a ConfigMap at `/camunda/configuration/external` and a JAR volume at `/camunda/extensions`
- an example `application.yml` for an external datasource
- a PostgreSQL driver example
- the UID/readability requirement for bind-mounted files
- the trusted-code and restart requirements

### 5. Support launch-mode environment variables

Add these optional environment variables, each accepting only `true`, `false`, or an unset value:

| Environment variable | Camunda Run argument when `true` |
| --- | --- |
| `CAMUNDA_RUN_REST` | `--rest` |
| `CAMUNDA_RUN_WEBAPPS` | `--webapps` |
| `CAMUNDA_RUN_SWAGGERUI` | `--swaggerui` |
| `CAMUNDA_RUN_PRODUCTION` | `--production` |

The wrapper translates variables set to `true` into the matching arguments before delegating to `internal/run.sh`. Variables set to `false` add no argument. Invalid values must fail before Run starts, with an actionable error.

The component switches have selection semantics: if no component argument is passed, Camunda Run keeps its existing default component selection. Therefore, the wrapper must add component arguments from the environment only when at least one component environment variable is `true`.

Explicit command arguments retain precedence:

- if the user supplies one or more of `--rest`, `--webapps`, or `--swaggerui`, do not derive any component arguments from environment variables
- if the user supplies `--production`, do not derive it from `CAMUNDA_RUN_PRODUCTION`
- non-mode command arguments continue to pass through unchanged

This makes environment variables the declarative deployment interface while retaining existing Docker, Compose, Testcontainers, and direct CLI invocations unchanged.

## Example Usage

```bash
docker run \
  --publish 8080:8080 \
  --volume "$PWD/config:/camunda/configuration/external:ro" \
  --volume "$PWD/extensions:/camunda/extensions:ro" \
  --env CAMUNDA_RUN_REST=true \
  --env CAMUNDA_RUN_WEBAPPS=true \
  --env CAMUNDA_RUN_PRODUCTION=true \
  bpmcrafters/camunda-bpm-platform:run-7.24.0
```

Example mounted configuration:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/camunda
    username: camunda
    password: camunda
    driver-class-name: org.postgresql.Driver
```

The matching PostgreSQL JDBC JAR belongs in the `/camunda/extensions` mount.

### Kubernetes Shape

```yaml
volumeMounts:
  - name: camunda-configuration
    mountPath: /camunda/configuration/external
    readOnly: true
  - name: camunda-extensions
    mountPath: /camunda/extensions
    readOnly: true
volumes:
  - name: camunda-configuration
    configMap:
      name: camunda-configuration
  - name: camunda-extensions
    persistentVolumeClaim:
      claimName: camunda-extensions
```

An init container can populate the `camunda-extensions` volume before the Camunda Run container starts. It must ensure that JAR files are readable by UID `1000`.

Kubernetes deployments can set the launch mode without replacing the image command:

```yaml
env:
  - name: CAMUNDA_RUN_REST
    value: "true"
  - name: CAMUNDA_RUN_WEBAPPS
    value: "true"
  - name: CAMUNDA_RUN_PRODUCTION
    value: "true"
```

## Implementation Steps

1. Update `docker/camunda-bpm-platform/run/Dockerfile` to create the two mount-point directories owned by UID `1000`.
2. Update `docker/camunda-bpm-platform/run/camunda.sh` to prepare the configuration and JAR directories as described above.
3. Preserve the no-extension path: launching the image without either mount must behave exactly as it does now.
4. Update `docker/camunda-bpm-platform/README.md` with the mount contract and examples.
5. Extend `docker/camunda-bpm-platform/run/camunda.sh` with validated environment-to-argument translation and explicit-argument precedence.
6. Document the launch-mode environment variables and their precedence in the README.
7. Add `docker/camunda-bpm-platform/test/` with Compose services for PostgreSQL and Camunda Run, external datasource configuration, and a script that downloads the PostgreSQL JDBC JAR into the mounted extension directory.
8. Make the script verify the REST engine endpoint and the creation of a Camunda table in PostgreSQL.
9. Run the script in `.github/workflows/camunda-run-image.yml` after a native image build and before the multi-architecture publication build.

## Validation

- Start the image without an extension mount and verify the existing REST/webapp startup path.
- Start the image with a ConfigMap-style mounted `application.yml` and verify that a distinctive configuration property takes effect.
- Start the image with a mounted JDBC driver JAR and external datasource configuration; verify engine startup and a database connection.
- Start the image with each supported `CAMUNDA_RUN_*` value and verify the translated Run launch mode.
- Verify invalid `CAMUNDA_RUN_*` values fail before Run starts.
- Verify explicit component or production arguments override their environment-variable counterparts.
- Run the PostgreSQL Compose fixture against the published image and against a locally built image carrying the normal publication tag.
- Run the PostgreSQL Compose fixture as a required step before image publication.
- Verify that a malformed extension layout fails before Camunda Run starts, with an actionable error.
- Verify that adding or updating a JAR/configuration requires a restart and is picked up after that restart.

## Acceptance Criteria

- Configuration can be mounted independently at `/camunda/configuration/external`.
- JARs mounted independently at `/camunda/extensions` are loaded by the existing Camunda Run user-library mechanism.
- The configuration mount is added to Spring Boot's configuration search path without discarding Camunda Run defaults.
- The image supports the documented environment-variable equivalents of `--rest`, `--webapps`, `--swaggerui`, and `--production`.
- Existing explicit command arguments take precedence over environment-derived mode arguments.
- The existing image behaviour is unchanged when neither mount is present.
- The README documents the directory layout, usage, ownership requirements, and security implications.
- The feature is explicitly limited to startup-time loading; it does not claim hot-reload support.

## Risks and Notes

- Mounted JARs are executable code in the engine process. Deployers must treat the mount as trusted and use `:ro` where possible.
- Extension JARs must be compatible with Camunda Run 7.24, its dependency set, and Java 17. Dependency conflicts are a consumer responsibility and should surface clearly at startup.
- Spring Boot configuration precedence matters. `SPRING_CONFIG_ADDITIONAL_LOCATION` is required; `SPRING_CONFIG_LOCATION` would replace normal locations and can accidentally disable required bundled defaults.
- The environment variables intentionally control only image startup mode. They do not replace Spring Boot's native environment-based configuration.
- If a future requirement needs multiple arbitrary YAML filenames, add an explicit opt-in list rather than silently scanning all files.
