#!/usr/bin/env bash
# Проверка аддона на сервере разработки: NeoForge + TFC (гарь из его порошков), Create и Create Connected (шлюзы,
# желоба, рецепт) из кеша сборки, плоский мир, RCON 25612. Без аргументов — сценарий из README («Что проверено»);
# с аргументами — эти команды. KEEP=1 — не стирать мир; NO_MODS=1 — без TFC и Create (аддон должен грузиться и так).
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
MODS="${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"
if [ -z "${KEEP:-}" ]; then rm -rf run; fi
mkdir -p run/mods
rm -f run/mods/*.jar
if [ -z "${NO_MODS:-}" ]; then
  cp "$MODS"/TerraFirmaCraft-NeoForge-*.jar "$MODS"/Patchouli-*.jar "$MODS"/create-1.21.1-*.jar "$MODS"/create_connected-*.jar run/mods/
fi
echo 'eula=true' > run/eula.txt
cat > run/server.properties <<'P'
level-type=minecraft\:flat
online-mode=false
spawn-protection=0
generate-structures=false
enable-rcon=true
rcon.port=25612
rcon.password=check
server-port=25613
P
if [ $# -eq 0 ]; then
  set -- 'inctest setup' 'sleep 15' 'inctest report' 'inctest stats 1000' 'inctest powered 640'
fi
./gradlew runServer --console=plain > run/check.log 2>&1 &
GRADLE=$!
SERVER_PID=$GRADLE python3 rcon.py 25612 check 'forceload add -16 -16 48 48' "$@" 'stop' || true
wait $GRADLE || true
grep -E "ERROR|Exception|Caused by|incinerator" run/check.log | grep -v "^\s*at " | head -30 || true
