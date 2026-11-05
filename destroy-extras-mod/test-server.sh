#!/usr/bin/env bash
# Воспроизводимый dedicated server M2. Сервер останавливается после RCON-сценария; FAIL даёт ненулевой код.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
RCON_PORT="${RCON_PORT:-25606}"
SERVER_PORT="${SERVER_PORT:-25607}"
PROFILE="${PROFILE:-minimal}"
case "$PROFILE" in minimal|thermal|sable) ;; *) echo "Неизвестный PROFILE=$PROFILE" >&2; exit 2 ;; esac
python3 - "$RCON_PORT" "$SERVER_PORT" <<'PY'
import socket, sys
for value in sys.argv[1:]:
    s = socket.socket()
    try:
        s.bind(('127.0.0.1', int(value)))
    except OSError:
        raise SystemExit(f'Порт {value} занят; задайте RCON_PORT/SERVER_PORT.')
    finally:
        s.close()
PY
RUN_DIR="${RUN_DIR:-run}"
export RUN_DIR
if [ -z "${SKIP_LIBS:-}" ]; then
  ./libs.sh
  (cd ../destroy-bridge-mod && ./libs.sh)
fi
./gradlew jar --console=plain
(cd ../destroy-bridge-mod && ./gradlew jar --console=plain)
if [ -z "${KEEP:-}" ]; then rm -rf "$RUN_DIR"; fi
mkdir -p "$RUN_DIR/mods"
rm -f "$RUN_DIR/mods"/*.jar
for jar in create-1.21.1-6.0.10.jar electroenergetics-1.21.1-1.1.2.jar TerraFirmaCraft-NeoForge-1.21.1-4.2.9.jar Patchouli-1.21.1-93-NEOFORGE.jar destroy-1.21.1-0.4.6-eligibbs.jar petrolpark-1.21.1-1.5.11.jar; do
  cp "libs/$jar" "$RUN_DIR/mods/"
done
MODS="${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"
cp "$MODS/Firmalife-NeoForge-1.21.1-3.0.14.jar" "$RUN_DIR/mods/"
if [ "$PROFILE" = thermal ]; then
  cp "$MODS/woodencog-1.2.18-1.21.1-TFCR.jar" "$MODS/ImmersiveEngineering-1.21.1-12.4.2-194.jar" \
    "$MODS/TFC-IE-Crossover-NeoForge-1.21.1-2.1.3.jar" "$RUN_DIR/mods/"
fi
if [ "$PROFILE" = sable ]; then cp "$MODS/sable-neoforge-1.21.1-2.0.5.jar" "$RUN_DIR/mods/"; fi
EE_VERSION=$(sed -n 's/^mod_version=//p' ../ee-extras-mod/gradle.properties)
VERSION=$(sed -n 's/^mod_version=//p' gradle.properties)
cp ../destroy-bridge-mod/build/libs/destroy-bridge-0.2.0.jar "$RUN_DIR/mods/"
cp "../ee-extras-mod/build/libs/ee-extras-$EE_VERSION.jar" "$RUN_DIR/mods/"
{
  echo "profile=$PROFILE"
  shasum -a 256 "$RUN_DIR/mods"/*.jar "build/libs/destroy-extras-$VERSION.jar"
} > "$RUN_DIR/manifest.txt"
echo 'eula=true' > "$RUN_DIR/eula.txt"
cat > "$RUN_DIR/server.properties" <<P
level-type=minecraft\\:flat
online-mode=false
spawn-protection=0
generate-structures=false
enable-rcon=true
rcon.port=${RCON_PORT}
server-port=${SERVER_PORT}
rcon.password=check
sync-chunk-writes=true
P
if [ $# -eq 0 ]; then
  set -- 'destroyextratest environment'
fi
./gradlew runServer --console=plain > "$RUN_DIR/check.log" 2>&1 &
GRADLE=$!
cleanup() {
  if kill -0 "$GRADLE" 2>/dev/null; then
    SERVER_PID="$GRADLE" python3 rcon.py "$RCON_PORT" check stop >/dev/null 2>&1 || kill "$GRADLE" 2>/dev/null || true
  fi
}
trap cleanup EXIT
set +e
SERVER_PID=$GRADLE python3 rcon.py "$RCON_PORT" check 'forceload add -16 -16 16 16' "$@" 'stop' 2>&1 | tee "$RUN_DIR/results.txt"
RCON_STATUS=${PIPESTATUS[0]}
wait "$GRADLE"
SERVER_STATUS=$?
set -e
trap - EXIT
grep -E 'DESTROYEXTRATEST|ERROR|FATAL|Exception|Caused by' "$RUN_DIR/check.log" | head -100 || true
STATUS=0
if [ "$RCON_STATUS" -ne 0 ] || [ "$SERVER_STATUS" -ne 0 ]; then STATUS=1; fi
if grep -E 'DESTROYEXTRATEST FAIL|Unknown (or incomplete )?command|No value present' "$RUN_DIR/results.txt" "$RUN_DIR/check.log"; then STATUS=1; fi
if grep -E 'FATAL|Failed to start the minecraft server|Mod loading has failed' "$RUN_DIR/check.log"; then STATUS=1; fi
case " $* " in *' destroyextratest assert '*) grep -q 'DESTROYEXTRATEST PASS' "$RUN_DIR/results.txt" || STATUS=1 ;; esac
exit "$STATUS"
