#!/usr/bin/env bash
# Выпуск: сборка → jar и иконки рядом с исходниками; на сайт их кладёт сборка сайта (scripts/addons-index.mjs), версию — addon.json.
# Проверка на сервере разработки — отдельно: ./test-server.sh (около минуты).
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
./gradlew build -q --console=plain
VERSION=$(sed -n 's/^mod_version=//p' gradle.properties)
rm -f incinerator-*.jar
cp "build/libs/incinerator-$VERSION.jar" .
# Иконки для сайта (icons/) — общим генератором MCP
(cd ../.. && node scripts/mcp/render.mjs tools/incinerator-mod incinerator:incinerator --out tools/incinerator-mod/icons --size 192)
echo "готово: incinerator-$VERSION.jar → tools/incinerator-mod; обновите addon.json и проверьте MCP addon_check"
