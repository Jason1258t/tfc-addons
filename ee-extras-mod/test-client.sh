#!/usr/bin/env bash
# Настоящий клиент разработки с IE: плоский мир, прожекторы на кабеле (src/devtest ClientTest), снимки в run/screenshots.
# Итог — строки EECLIENT в run/client-check.log.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
MODS="${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"
rm -rf run/mods run/saves/eetest
mkdir -p run/mods
cp libs/create-1.21.1-*.jar libs/electroenergetics-*.jar "$MODS"/ImmersiveEngineering-*.jar run/mods/
printf 'lang:ru_ru\nonboardAccessibility:false\ntutorialStep:none\n' > run/options.txt
./gradlew runClient --console=plain > run/client-check.log 2>&1 || true
grep EECLIENT run/client-check.log
