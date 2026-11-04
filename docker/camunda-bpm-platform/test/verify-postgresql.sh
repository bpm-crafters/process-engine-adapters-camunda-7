#!/bin/bash
set -euo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIRECTORY/docker-compose.yaml"
CAMUNDA_IMAGE="${CAMUNDA_IMAGE:-bpmcrafters/camunda-bpm-platform:run-7.24.0}"
BUILD_IMAGE="${BUILD_IMAGE:-false}"
JDBC_VERSION="${POSTGRESQL_JDBC_VERSION:-42.7.5}"
JDBC_DIRECTORY="$SCRIPT_DIRECTORY/extensions"
JDBC_JAR="$JDBC_DIRECTORY/postgresql-$JDBC_VERSION.jar"
JDBC_URL="https://repo1.maven.org/maven2/org/postgresql/postgresql/$JDBC_VERSION/postgresql-$JDBC_VERSION.jar"
ENGINE_URL="http://localhost:${CAMUNDA_TEST_PORT:-18080}/engine-rest/engine/"
COMPOSE=(docker compose --file "$COMPOSE_FILE")

cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans
}

require_command() {
  if ! command -v "$1" >/dev/null; then
    echo "Required command '$1' is not available." >&2
    exit 1
  fi
}

download_driver() {
  mkdir -p "$JDBC_DIRECTORY"

  if [[ -f "$JDBC_JAR" ]]; then
    return
  fi

  echo "Downloading PostgreSQL JDBC driver $JDBC_VERSION"
  curl --fail --location --retry 3 --output "$JDBC_JAR.tmp" "$JDBC_URL"
  mv "$JDBC_JAR.tmp" "$JDBC_JAR"
}

wait_for_engine() {
  local attempt

  for attempt in {1..60}; do
    if curl --fail --silent --show-error "$ENGINE_URL" >/dev/null; then
      return
    fi
    sleep 2
  done

  echo "Camunda Run did not start successfully." >&2
  "${COMPOSE[@]}" logs >&2
  exit 1
}

require_command docker
require_command curl
docker compose version >/dev/null
case "$BUILD_IMAGE" in
  true | false) ;;
  *)
    echo "BUILD_IMAGE must be 'true' or 'false'." >&2
    exit 1
    ;;
esac

if [[ "$BUILD_IMAGE" == true ]]; then
  docker build --tag "$CAMUNDA_IMAGE" "$SCRIPT_DIRECTORY/../run"
fi

export CAMUNDA_IMAGE
download_driver

trap cleanup EXIT
"${COMPOSE[@]}" up --detach --build
wait_for_engine

if [[ "$("${COMPOSE[@]}" exec --no-TTY postgres psql -U camunda -d camunda -tAc "SELECT to_regclass('act_ge_property') IS NOT NULL")" != t ]]; then
  echo "Camunda tables were not created in PostgreSQL." >&2
  "${COMPOSE[@]}" logs >&2
  exit 1
fi

echo "Camunda Run started with the mounted PostgreSQL JDBC driver."
