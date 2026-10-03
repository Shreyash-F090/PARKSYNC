#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT_DIR/backend/target/parksync-backend.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Backend jar not found. Build it first with: bash backend/build.sh" >&2
  exit 1
fi

exec java -jar "$JAR"