#!/usr/bin/env bash
# Прогон плана на полном клиенте сборки (test1a, npm run client-stand): ./run.sh <план> [флаги client-stand run…]
# План — plans/<план>.txt, длительность — строка «# settle: <с>». Итоги — строки «[STRESS]» в latest.log прогона.
set -euo pipefail
cd "$(dirname "$0")"
PLAN="plans/$1.txt"; shift
[ -f "$PLAN" ] || { echo "нет $PLAN"; exit 1; }
SETTLE=$(sed -n 's/^# settle: *//p' "$PLAN")
VERSION=$(sed -n 's/^mod_version=//p' gradle.properties)
JAR="$PWD/build/libs/stress-test-$VERSION.jar"
[ -f "$JAR" ] || { echo "нет $JAR — ./gradlew build"; exit 1; }
OVL=$(mktemp -d)
trap 'rm -rf "$OVL"' EXIT
cp -R overlay/. "$OVL/"
cp "$PLAN" "$OVL/stress-plan.txt"
cd ../..
npm run client-stand -- run --world new --mod "$JAR" --overlay "$OVL" --settle "${SETTLE:-600}" --load 600 --timeout 1200 --shots 1 "$@"
