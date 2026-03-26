#!/usr/bin/env bash
# Build all JARs locally, then build every Docker image used by docker-compose.yml.
# Docker output uses --progress=plain so logs stream verbosely (no BuildKit collapse).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

export DOCKER_BUILDKIT=1

echo "==> Maven: employee, address, candidate, resume-parser, search (build-all.sh)"
"$ROOT/build-all.sh"

echo "==> Maven: api-gateway"
(
  cd "$ROOT/api-gateway"
  if [[ -x ./mvnw ]]; then ./mvnw clean package -DskipTests; else bash ./mvnw clean package -DskipTests; fi
)

echo "==> Docker Compose build (all services with a Dockerfile in compose)"
docker compose -f "$ROOT/docker-compose.yml" build --progress=plain

echo "==> Done. Run: docker compose up (add --build if you want compose to rebuild again)"
