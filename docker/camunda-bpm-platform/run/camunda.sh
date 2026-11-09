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

fail() {
  echo "$1" >&2
  exit 1
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
  local destination jar link target writable_probe
  local jars=()

  require_directory "$EXTENSIONS_DIRECTORY"
  require_directory "$USERLIB_DIRECTORY"

  shopt -s nullglob
  jars=("$EXTENSIONS_DIRECTORY"/*.jar)
  if (( ${#jars[@]} == 0 )); then
    return
  fi

  writable_probe="$(mktemp "$USERLIB_DIRECTORY/.camunda-extension-writable.XXXXXX")" \
    || fail "Camunda Run extension directory '$USERLIB_DIRECTORY' must be writable by the runtime user."
  rm "$writable_probe"

  while IFS= read -r -d '' link; do
    target="$(readlink "$link")"
    if [[ "$target" == "$EXTENSIONS_DIRECTORY/"* ]]; then
      rm "$link"
    fi
  done < <(find "$USERLIB_DIRECTORY" -maxdepth 1 -type l -print0)

  for jar in "${jars[@]}"; do
    destination="$USERLIB_DIRECTORY/$(basename "$jar")"
    if [[ -e "$destination" || -L "$destination" ]]; then
      fail "Cannot provision extension '$jar': '$destination' already exists. Rename the mounted JAR or remove the colliding userlib entry."
    fi
    ln -s "$jar" "$destination" \
      || fail "Cannot provision extension '$jar' in '$USERLIB_DIRECTORY'."
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
validate_boolean CAMUNDA_RUN_EXAMPLE
validate_boolean CAMUNDA_RUN_PRODUCTION
validate_boolean CAMUNDA_RUN_OAUTH2

run_arguments=()
if ! has_argument --rest "$@" \
  && ! has_argument --webapps "$@" \
  && ! has_argument --example "$@"; then
  if [[ "${CAMUNDA_RUN_REST:-}" == true ]]; then
    run_arguments+=(--rest)
  fi
  if [[ "${CAMUNDA_RUN_WEBAPPS:-}" == true ]]; then
    run_arguments+=(--webapps)
  fi
  if [[ "${CAMUNDA_RUN_EXAMPLE:-}" == true ]]; then
    run_arguments+=(--example)
  fi
fi

if ! has_argument --production "$@" && [[ "${CAMUNDA_RUN_PRODUCTION:-}" == true ]]; then
  run_arguments+=(--production)
fi

if ! has_argument --oauth2 "$@" && [[ "${CAMUNDA_RUN_OAUTH2:-}" == true ]]; then
  run_arguments+=(--oauth2)
fi

exec /camunda/internal/run.sh start "${run_arguments[@]}" "$@"
