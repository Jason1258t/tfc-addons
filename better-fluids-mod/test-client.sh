#!/usr/bin/env bash
# Создаёт плоский мир с баками и снимает кадры сверху и снизу в run/screenshots/bf-*.png, затем закрывает окно.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
[ -d libs ] || ./libs.sh
rm -rf run/mods run/saves/bftest run/screenshots/bf-*.png
mkdir -p run/mods
cp libs/create-1.21.1-*.jar run/mods/
# EXTRA="sodium iris" — добавить моды сборки по началу имени jar (проверка совместимости рендера).
for name in ${EXTRA:-}; do
    cp "${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"/"$name"*.jar run/mods/
done
printf 'lang:ru_ru\nonboardAccessibility:false\ntutorialStep:none\n' > run/options.txt
./gradlew runClient --console=plain > run/client-check.log 2>&1
grep BFCLIENT run/client-check.log
