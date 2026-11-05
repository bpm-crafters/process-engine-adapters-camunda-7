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
- Image users must set `command: ./camunda.sh ...` to pass Run's `--rest`, `--webapps`, `--example`, `--oauth2`, or `--production` arguments.

## Camunda Run 7.24 capability analysis

Sources: [Camunda Run 7.24 guide](https://docs.camunda.org/manual/7.24/user-guide/camunda-bpm-run/) and [Camunda Docker image database-variable reference](https://github.com/camunda/docker-camunda-bpm-platform#database-environment-variables).

The image packages the unmodified Community Edition Camunda Run 7.24 distribution. Its actual `internal/run.sh start --help` contract is the source of truth for this feature. It supports `--webapps`, `--rest`, `--example`, `--production`, `--oauth2`, and `--detached`; `--help` is informational. In particular, it **does not support `--swaggerui`**. The upstream Docker README still describes `--swaggerui`, but that applies to its Docker image wrapper and conflicts with the launcher contained in this image. This image must therefore not expose a Swagger UI launch variable.

Camunda Run starts webapps, REST, and the example application by default. Passing one or more of `--webapps`, `--rest`, or `--example` changes this to an explicit selection; omitted components are disabled. `--production` loads `production.yml` and disables the example unless `--example` is also supplied. `--oauth2` enables the Spring Security OAuth2 integration. The container must remain a foreground process, so `--detached` is intentionally not modelled as an environment variable.

The Run distribution already supplies these extension points:

- `configuration/userlib/` for JDBC drivers and other required JARs;
- `configuration/resources/` for automatic deployment of BPMN, DMN, CMMN, forms, and scripts;
- `configuration/default.yml` and `configuration/production.yml` for distribution configuration;
- Spring Boot/Camunda properties, including `spring.datasource.{url,driver-class-name,username,password}`.

This feature extends only configuration files and JARs. It does not add a deployment-resource mount, manage database schema installation, or hot-reload mounted data. A later feature may add a resource mount if users need deployment artifacts independently of extension JARs.

The official Camunda Docker image documents both native `SPRING_DATASOURCE_*` variables and compatibility aliases such as `DB_DRIVER`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `DB_PASSWORD_FILE`. The aliases are implemented by that image's wrapper; they are not provided by raw Camunda Run and are not translated by this image today. Consequently, this feature documents and tests the native `SPRING_DATASOURCE_*` interface (or mounted Spring configuration). Adding `DB_*` compatibility, secret-file handling, database wait logic, or pool aliases is an explicit future feature, not an implicit part of the volume support.

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
3. Make the discovered JARs available in Camunda Run's `/camunda/configuration/userlib/` without modifying the mounted volume. Symbolic links are preferred because they avoid copying potentially large JARs. Before linking, fail clearly if a non-extension file with the same name already exists in `userlib`; never silently replace a bundled JAR.
4. Set `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/camunda/configuration/external/` when the configuration mount exists, preserving any caller-provided value by appending this location. Spring Boot gives the later additional location higher file-configuration precedence, so the mounted deployment configuration deliberately wins over a caller-provided additional file location. Environment variables and command-line properties remain the appropriate higher-priority operational overrides.
5. `exec /camunda/internal/run.sh start "$@"` as today.

The link target is inside the image filesystem. The initial implementation consequently requires a writable `/camunda/configuration/userlib` owned by the runtime user (UID `1000`). `readOnlyRootFilesystem: true` and platforms that assign an arbitrary UID, such as a restrictive OpenShift configuration, are unsupported until the image has an explicit writable `userlib` mount strategy. The wrapper must detect this condition before invoking Run and state the required writable path in its error message.

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
| `CAMUNDA_RUN_EXAMPLE` | `--example` |
| `CAMUNDA_RUN_PRODUCTION` | `--production` |
| `CAMUNDA_RUN_OAUTH2` | `--oauth2` |

The wrapper translates variables set to `true` into the matching arguments before delegating to `internal/run.sh`. Variables set to `false` add no argument. Invalid values must fail before Run starts, with an actionable error.

The component switches (`REST`, `WEBAPPS`, and `EXAMPLE`) have selection semantics: if no component argument is passed, Camunda Run keeps its existing default selection. Therefore, the wrapper must add component arguments from the environment only when at least one component environment variable is `true`. A value of `false` does not itself disable a component: it adds no argument. For example, all three values set to `false` still starts Run's defaults, while `CAMUNDA_RUN_REST=true` and `CAMUNDA_RUN_WEBAPPS=false` starts REST only. This limitation follows the underlying enable-only CLI.

Explicit command arguments retain precedence:

- if the user supplies one or more of `--rest`, `--webapps`, or `--example`, do not derive any component arguments from environment variables
- if the user supplies `--production`, do not derive it from `CAMUNDA_RUN_PRODUCTION`
- if the user supplies `--oauth2`, do not derive it from `CAMUNDA_RUN_OAUTH2`
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
6. Document the launch-mode environment variables, selection semantics, and their precedence in the README. Do not document `CAMUNDA_RUN_SWAGGERUI`.
7. Add `docker/camunda-bpm-platform/test/` with Compose services for PostgreSQL and Camunda Run, external datasource configuration, and a script that downloads the PostgreSQL JDBC JAR into the mounted extension directory.
8. Make the script verify the REST engine endpoint and the creation of a Camunda table in PostgreSQL.
9. Run the script in `.github/workflows/camunda-run-image.yml` after a native image build and before the multi-architecture publication build.

## Validation

- Start the image without an extension mount and verify the existing REST/webapp startup path.
- Start the image with a ConfigMap-style mounted `application.yml` and verify that a distinctive configuration property takes effect.
- Start the image with a mounted JDBC driver JAR and external datasource configuration; verify engine startup and a database connection.
- Start the image with each supported `CAMUNDA_RUN_*` value and verify the translated Run launch mode, including `EXAMPLE` and `OAUTH2`.
- Verify invalid `CAMUNDA_RUN_*` values fail before Run starts.
- Verify explicit component or production arguments override their environment-variable counterparts.
- Run the PostgreSQL Compose fixture against the published image and against a locally built image carrying the normal publication tag.
- Run the PostgreSQL Compose fixture as a required step before image publication.
- Verify that a malformed extension layout, an existing colliding `userlib` JAR, or an unwritable `userlib` fails before Camunda Run starts, with an actionable error.
- Verify that adding or updating a JAR/configuration requires a restart and is picked up after that restart.

## Acceptance Criteria

- Configuration can be mounted independently at `/camunda/configuration/external`.
- JARs mounted independently at `/camunda/extensions` are loaded by the existing Camunda Run user-library mechanism.
- The configuration mount is added to Spring Boot's configuration search path without discarding Camunda Run defaults.
- The image supports the documented environment-variable equivalents of `--rest`, `--webapps`, `--example`, `--production`, and `--oauth2`; it does not expose a Swagger UI switch unsupported by its bundled Run launcher.
- Existing explicit command arguments take precedence over environment-derived mode arguments.
- The existing image behaviour is unchanged when neither mount is present.
- The README documents the directory layout, usage, ownership requirements, and security implications.
- The feature is explicitly limited to startup-time loading; it does not claim hot-reload support.

## Risks and Notes

- Mounted JARs are executable code in the engine process. Deployers must treat the mount as trusted and use `:ro` where possible.
- Extension JARs must be compatible with Camunda Run 7.24, its dependency set, and Java 17. Dependency conflicts are a consumer responsibility and should surface clearly at startup.
- Spring Boot configuration precedence matters. `SPRING_CONFIG_ADDITIONAL_LOCATION` is required; `SPRING_CONFIG_LOCATION` would replace normal locations and can accidentally disable required bundled defaults.
- Appending the external mount makes it win over a caller-provided additional file location. This is intentional for a deployment-mounted ConfigMap/Secret; use native environment variables or command-line properties for an operational override.
- The environment variables intentionally control only image startup mode. They do not replace Spring Boot's native environment-based configuration.
- The initial JAR-link implementation cannot run on a read-only root filesystem or with an arbitrary runtime UID because Run consumes `configuration/userlib/`. Document this limitation and fail early; do not redirect the link to `/tmp`, which Run does not scan.
- A same-name JAR already in `userlib` must produce a clear startup failure. Replacing a bundled library would make the image's effective dependency set non-deterministic.
- The PostgreSQL fixture must remove stale `postgresql-*.jar` files when its requested driver version changes and should stop polling immediately when the Camunda service has exited. Its manual `psql` example must use `\\dt act_*`.
- If a future requirement needs multiple arbitrary YAML filenames, add an explicit opt-in list rather than silently scanning all files.
