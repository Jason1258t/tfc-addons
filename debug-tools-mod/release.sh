#!/usr/bin/env bash
# Выпуск: сборка → jar рядом с исходниками; на сервер сборки его кладёт tfc admin addon upload.
# Проверка — на стенде полной сборки: npm run stand -- --level-type tfc:overworld --mod <jar> --cmd 'tfcdebug vein list'
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
[ -d libs ] || ./libs.sh
./gradlew build -q --console=plain
VERSION=$(sed -n 's/^mod_version=//p' gradle.properties)
rm -f debug-tools-*.jar
cp "build/libs/debug-tools-$VERSION.jar" .
echo "готово: debug-tools-$VERSION.jar → tools/debug-tools-mod; обновите addon.json и проверьте MCP addon_check"
