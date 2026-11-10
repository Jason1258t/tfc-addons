#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
[ -d libs ] || ./libs.sh
mkdir -p run/mods
cp libs/create-1.21.1-*.jar run/mods/
if [ "${WITH_IE:-0}" = 1 ]; then
  cp "${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"/ImmersiveEngineering-*.jar run/mods/
fi
if [ "${WITH_COPYCATS:-0}" = 1 ]; then
  cp "${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"/copycats-*.jar run/mods/
fi
echo eula=true > run/eula.txt
cat > run/server.properties <<'PROPERTIES'
level-type=minecraft:flat
online-mode=false
enable-rcon=true
rcon.port=25586
rcon.password=check
server-port=25587
PROPERTIES
./gradlew runServer --console=plain > run/check.log 2>&1 &
pid=$!
SERVER_PID=$pid python3 rcon.py 25586 check 'forceload add -16 0 15 15' "$@" stop
wait "$pid"
