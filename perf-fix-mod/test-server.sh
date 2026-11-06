#!/usr/bin/env bash
# Проверка аддона на сервере разработки: JourneyMap Shared Maps, TFC и Patchouli из jar сборки, плоский мир, RCON 25608.
# Без аргументов — два мира, в каждом два запуска (второй — тот же мир после перезапуска):
#  OFF — аддон выключен (как у самих модов), ON — включён. В каждом: 400 чанков × 5 проходов тайлов общей карты
#  в главном потоке (время), сверка файлов регионов с памятью мода, октопотеутис со светом далеко и рядом;
#  после перезапуска мод заново читает свои файлы — сверка ещё раз.
# С аргументами — один запуск с этими командами (KEEP=1 — не стирать мир, OFF=1 — аддон выключен).
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
MODS="${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"

run() { # run <выключен 0|1> <команды…>
  local off=$1; shift
  mkdir -p run/mods
  rm -f run/mods/*.jar
  cp "$MODS"/jmsharedmaps-*.jar "$MODS"/TerraFirmaCraft-NeoForge-1.21.1-*.jar "$MODS"/Patchouli-*.jar run/mods/
  echo 'eula=true' > run/eula.txt
  cat > run/server.properties <<'S'
level-type=minecraft\:flat
online-mode=false
spawn-protection=0
generate-structures=false
enable-rcon=true
rcon.port=25608
rcon.password=check
server-port=25609
S
  local opts=""
  if [ "$off" = 1 ]; then opts="-Dperf_fix.jmsharedmaps.disabled=true -Dperf_fix.glow.disabled=true"; fi
  JAVA_TOOL_OPTIONS="$opts" ./gradlew runServer --console=plain > run/check.log 2>&1 &
  local gradle=$!
  SERVER_PID=$gradle python3 rcon.py 25608 check "$@" 'stop' || true
  wait $gradle || true
  grep -E "ERROR|Exception|Caused by|perf_fix|Perf Fix" run/check.log | grep -v "^\s*at " | head -30 || true
}

if [ $# -gt 0 ]; then
  if [ -z "${KEEP:-}" ]; then rm -rf run; fi
  run "${OFF:-0}" "$@"
  exit 0
fi

for mode in OFF ON; do
  off=0; [ $mode = OFF ] && off=1
  rm -rf run
  echo "== $mode: тайлы и октопотеутис"
  run $off 'sleep 2' 'pftest bench 400 5' 'sleep 3' 'pftest verify' 'pftest glow near' 'pftest glow far'
  echo "== $mode: перезапуск, мод читает свои файлы"
  run $off 'sleep 2' 'pftest verify'
done
