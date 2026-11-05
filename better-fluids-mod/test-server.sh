#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
[ -d libs ] || ./libs.sh
mkdir -p run/mods
cp libs/create-1.21.1-*.jar run/mods/
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
