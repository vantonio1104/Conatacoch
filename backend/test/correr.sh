#!/usr/bin/env bash
# Recrea la base de pruebas local y corre las pruebas del servidor. Uso: test/correr.sh [archivo.test.js ...]
# Necesita PostgreSQL local; por defecto el del entorno de desarrollo (puerto 55432, socket en /tmp).
set -euo pipefail
cd "$(dirname "$0")/.."
PG="${PG:-psql -h /tmp -p 55432 -U postgres}"
$PG -qc "DROP DATABASE IF EXISTS conatacoch_test;" -c "CREATE DATABASE conatacoch_test;"
export TEST_DATABASE_URL="${TEST_DATABASE_URL:-postgresql://postgres@localhost:55432/conatacoch_test}"
if [ $# -gt 0 ]; then node --test --test-concurrency=1 "$@"; else npm test --silent; fi
