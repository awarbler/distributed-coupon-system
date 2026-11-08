#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ ! -f .local-api.env ]; then
  echo "Missing private settings: .local-api.env" >&2
  exit 1
fi
. ./.local-api.env
if [ ! -f target/classes/coupons/api/CouponApiServer.class ]; then
  echo "Building the local API..." >&2
  mvn -q -DskipTests clean package dependency:copy-dependencies
fi
# Keep the API request contract in sync with frontend/source changes.
if [ -n "$(find src/main -type f -newer target/classes/coupons/api/CouponApiServer.class -print -quit)" ]; then
  echo "API sources changed; rebuilding before startup..." >&2
  mvn -q -DskipTests clean package dependency:copy-dependencies
fi
if [ -x .local-reader/runtime/ollama ]; then
  ./start-local-reader.sh --background
fi
exec java -cp 'target/classes:target/dependency/*' coupons.api.CouponApiServer
