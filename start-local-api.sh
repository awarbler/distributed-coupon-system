#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ ! -f .local-api.env ]; then
  echo "Missing private settings: .local-api.env" >&2
  exit 1
fi
. ./.local-api.env
if [ ! -f target/classes/coupons/api/CouponApiServer.class ]; then
  echo "Build first: mvn -q -DskipTests package dependency:copy-dependencies" >&2
  exit 1
fi
exec java -cp 'target/classes:target/dependency/*' coupons.api.CouponApiServer
