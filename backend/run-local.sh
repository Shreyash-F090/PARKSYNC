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

MYSQL_PID=""
if [[ -z "${DB_URL:-}" && -z "${DB_HOST:-}" ]]; then
  MYSQLD="$(command -v mysqld || true)"
  MYSQL="$(command -v mysql || true)"
  MYSQLADMIN="$(command -v mysqladmin || true)"
  if [[ -z "$MYSQLD" || -z "$MYSQL" || -z "$MYSQLADMIN" ]]; then
    echo "MySQL 8 server and client are required for local development." >&2
    echo "Install MySQL, configure DB_URL/DB_HOST, or run it through your database service." >&2
    exit 1
  fi

  DB_PORT="${DB_PORT:-3306}"
  DB_NAME="${DB_NAME:-parksync}"
  if [[ ! "$DB_PORT" =~ ^[0-9]+$ || ! "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "DB_PORT must be numeric and DB_NAME may contain only letters, digits, and underscores." >&2
    exit 1
  fi

  MYSQL_DATA_DIR="${PARKSYNC_MYSQL_DATA_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/parksync/mysql}"
  MYSQL_RUN_DIR="${TMPDIR:-/tmp}/parksync-mysql-$(id -u)"
  MYSQL_SOCKET="$MYSQL_RUN_DIR/mysql.sock"
  mkdir -p "$MYSQL_DATA_DIR" "$MYSQL_RUN_DIR"

  if ! "$MYSQLADMIN" --protocol=tcp --host=127.0.0.1 --port="$DB_PORT" ping --silent >/dev/null 2>&1; then
    if [[ ! -d "$MYSQL_DATA_DIR/mysql" ]]; then
      "$MYSQLD" --initialize-insecure --datadir="$MYSQL_DATA_DIR" --user="$(id -un)"
    fi
    "$MYSQLD" \
      --datadir="$MYSQL_DATA_DIR" \
      --socket="$MYSQL_SOCKET" \
      --pid-file="$MYSQL_RUN_DIR/mysql.pid" \
      --port="$DB_PORT" \
      --bind-address=127.0.0.1 \
      --mysqlx=0 \
      --user="$(id -un)" \
      --log-error="$MYSQL_RUN_DIR/mysql.log" &
    MYSQL_PID=$!
    trap 'if [[ -n "$MYSQL_PID" ]]; then kill "$MYSQL_PID" 2>/dev/null || true; wait "$MYSQL_PID" 2>/dev/null || true; fi' EXIT INT TERM

    ready=false
    for _ in $(seq 1 60); do
      if "$MYSQLADMIN" --protocol=socket --socket="$MYSQL_SOCKET" --user=root --skip-password ping --silent >/dev/null 2>&1; then
        ready=true
        break
      fi
      if ! kill -0 "$MYSQL_PID" 2>/dev/null; then
        cat "$MYSQL_RUN_DIR/mysql.log" >&2
        exit 1
      fi
      sleep 1
    done
    if [[ "$ready" != true ]]; then
      cat "$MYSQL_RUN_DIR/mysql.log" >&2
      echo "Timed out waiting for local MySQL." >&2
      exit 1
    fi

    "$MYSQL" --protocol=socket --socket="$MYSQL_SOCKET" --user=root --skip-password <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'parksync'@'localhost' IDENTIFIED BY '';
CREATE USER IF NOT EXISTS 'parksync'@'127.0.0.1' IDENTIFIED BY '';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO 'parksync'@'localhost';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO 'parksync'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
    export DB_USER=parksync DB_PASSWORD=""
  else
    echo "Using the MySQL server already listening on 127.0.0.1:$DB_PORT."
  fi
  export DB_HOST=127.0.0.1 DB_PORT DB_NAME
fi

cd "$ROOT_DIR"
mvn -f "$ROOT_DIR/backend/pom.xml" spring-boot:run