#!/usr/bin/env bash
# Воспроизводимый dedicated server. Сервер останавливается после RCON-сценария; FAIL даёт ненулевой код.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
PROFILE="${PROFILE:-minimal}"
RCON_PORT="${RCON_PORT:-25606}"
SERVER_PORT="${SERVER_PORT:-25607}"
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
./libs.sh
./gradlew jar --console=plain
(cd ../destroy-extras-mod && ./libs.sh && ./gradlew jar --console=plain)
if [ -z "${KEEP:-}" ]; then rm -rf run; fi
mkdir -p run/mods
rm -f run/mods/*.jar
for jar in create-1.21.1-6.0.10.jar electroenergetics-1.21.1-1.1.2.jar TerraFirmaCraft-NeoForge-1.21.1-4.2.9.jar Patchouli-1.21.1-93-NEOFORGE.jar destroy-1.21.1-0.4.6-eligibbs.jar petrolpark-1.21.1-1.5.11.jar; do
  cp "libs/$jar" run/mods/
done
if [ -n "${OLD_JAR:-}" ]; then
  cp "$OLD_JAR" run/mods/
else
  cp ../destroy-extras-mod/build/libs/destroy-extras-0.1.0.jar run/mods/
fi
MODS="${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"
if [ "$PROFILE" = thermal ]; then
  cp "$MODS/woodencog-1.2.18-1.21.1-TFCR.jar" "$MODS/ImmersiveEngineering-1.21.1-12.4.2-194.jar" run/mods/
fi
if [ "$PROFILE" = sable ]; then cp "$MODS/sable-neoforge-1.21.1-2.0.5.jar" run/mods/; fi
{
  echo "profile=$PROFILE rcon=$RCON_PORT server=$SERVER_PORT keep=${KEEP:-0} old_jar=${OLD_JAR:-}"
  shasum -a 256 run/mods/*.jar build/libs/destroy-bridge-0.1.0.jar
} > run/manifest.txt
echo 'eula=true' > run/eula.txt
cat > run/server.properties <<P
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
  set -- 'destroytest environment' 'destroytest assert'
fi
./gradlew runServer --console=plain > run/check.log 2>&1 &
GRADLE=$!
cleanup() {
  if kill -0 "$GRADLE" 2>/dev/null; then
    SERVER_PID="$GRADLE" python3 rcon.py "$RCON_PORT" check stop >/dev/null 2>&1 || kill "$GRADLE" 2>/dev/null || true
  fi
}
trap cleanup EXIT
set +e
SERVER_PID=$GRADLE python3 rcon.py "$RCON_PORT" check 'forceload add -16 -16 16 16' "$@" 'stop' 2>&1 | tee run/results.txt
RCON_STATUS=${PIPESTATUS[0]}
wait "$GRADLE"
SERVER_STATUS=$?
set -e
trap - EXIT
grep -E 'DESTROYTEST|ERROR|FATAL|Exception|Caused by' run/check.log | head -80 || true
STATUS=0
if [ "$RCON_STATUS" -ne 0 ] || [ "$SERVER_STATUS" -ne 0 ]; then STATUS=1; fi
if grep -E 'DESTROYTEST FAIL|Unknown command|Failed to load datapack reaction|No value present' run/results.txt run/check.log; then STATUS=1; fi
# RuntimeDistCleaner и разбор пустых layers в dev flat world — известные сообщения NeoForge после успешного старта.
if grep -E 'FATAL|Failed to start the minecraft server|Mod loading has failed' run/check.log; then STATUS=1; fi
case " $* " in *' destroytest assert '*) grep -q 'DESTROYTEST PASS' run/results.txt || STATUS=1 ;; esac
exit "$STATUS"
