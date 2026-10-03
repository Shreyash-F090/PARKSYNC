#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_BIN="$(readlink -f "$(command -v java)")"
JAVA_HOME="${JAVA_BIN%/bin/java}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

JAVA_VERSION="$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
if [[ "$JAVA_VERSION" != "21" ]]; then
  echo "PARKSYNC requires Java 21; found Java ${JAVA_VERSION:-unknown} at $JAVA_HOME." >&2
  exit 1
fi

exec mvn -f "$ROOT_DIR/backend/pom.xml" package