#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")"

docker compose up -d --wait
set -a; . ./.env; set +a
mvn clean mn:run
