#!/bin/bash
set -euo pipefail

CONFIGURATION_DIRECTORY=/camunda/configuration/external
EXTENSIONS_DIRECTORY=/camunda/extensions
USERLIB_DIRECTORY=/camunda/configuration/userlib

require_directory() {
  local directory="$1"

  if [[ ! -d "$directory" ]]; then
    echo "Expected directory '$directory' is not available." >&2
    exit 1
  fi
}

validate_boolean() {
  local variable="$1"
  local value="${!variable:-}"

  case "$value" in
    "" | true | false) ;;
    *)
      echo "Environment variable '$variable' must be 'true' or 'false' when set." >&2
      exit 1
      ;;
  esac
}

has_argument() {
  local expected="$1"
  local argument

  shift

  for argument in "$@"; do
    if [[ "$argument" == "$expected" ]]; then
      return 0
    fi
  done

  return 1
}

prepare_extensions() {
  local link target jar

  require_directory "$EXTENSIONS_DIRECTORY"
  require_directory "$USERLIB_DIRECTORY"

  while IFS= read -r -d '' link; do
    target="$(readlink "$link")"
    if [[ "$target" == "$EXTENSIONS_DIRECTORY/"* ]]; then
      rm "$link"
    fi
  done < <(find "$USERLIB_DIRECTORY" -maxdepth 1 -type l -print0)

  shopt -s nullglob
  for jar in "$EXTENSIONS_DIRECTORY"/*.jar; do
    ln -s "$jar" "$USERLIB_DIRECTORY/$(basename "$jar")"
  done
}

require_directory "$CONFIGURATION_DIRECTORY"
prepare_extensions

CONFIGURATION_LOCATION="optional:file:${CONFIGURATION_DIRECTORY}/"
if [[ -n "${SPRING_CONFIG_ADDITIONAL_LOCATION:-}" ]]; then
  export SPRING_CONFIG_ADDITIONAL_LOCATION="${SPRING_CONFIG_ADDITIONAL_LOCATION},${CONFIGURATION_LOCATION}"
else
  export SPRING_CONFIG_ADDITIONAL_LOCATION="$CONFIGURATION_LOCATION"
fi

validate_boolean CAMUNDA_RUN_REST
validate_boolean CAMUNDA_RUN_WEBAPPS
validate_boolean CAMUNDA_RUN_SWAGGERUI
validate_boolean CAMUNDA_RUN_PRODUCTION

run_arguments=()
if ! has_argument --rest "$@" \
  && ! has_argument --webapps "$@" \
  && ! has_argument --swaggerui "$@"; then
  if [[ "${CAMUNDA_RUN_REST:-}" == true ]]; then
    run_arguments+=(--rest)
  fi
  if [[ "${CAMUNDA_RUN_WEBAPPS:-}" == true ]]; then
    run_arguments+=(--webapps)
  fi
  if [[ "${CAMUNDA_RUN_SWAGGERUI:-}" == true ]]; then
    run_arguments+=(--swaggerui)
  fi
fi

if ! has_argument --production "$@" && [[ "${CAMUNDA_RUN_PRODUCTION:-}" == true ]]; then
  run_arguments+=(--production)
fi

exec /camunda/internal/run.sh start "${run_arguments[@]}" "$@"
