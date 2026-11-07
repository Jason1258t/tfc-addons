#!/usr/bin/env bash
# Выпуск: сборка → jar рядом с исходниками (в git); на сайт его кладёт сборка сайта (scripts/addons-index.mjs), версию — addon.json.
# Проверка — на стенде всей сборки (MCP pack_devserver), см. README «Проверка».
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
[ -d libs ] || ./libs.sh
./gradlew build -q --console=plain
VERSION=$(sed -n 's/^mod_version=//p' gradle.properties)
rm -f schematic-fix-*.jar
cp "build/libs/schematic-fix-$VERSION.jar" .
echo "готово: schematic-fix-$VERSION.jar → tools/schematic-fix-mod; обновите addon.json и проверьте MCP addon_check"
