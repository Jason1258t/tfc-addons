#!/usr/bin/env bash
# Сборка игрового релиза и иконок; зависимости Destroy/Petrolpark на сайт не публикуются.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
./libs.sh
./gradlew build --console=plain
VERSION=$(sed -n 's/^mod_version=//p' gradle.properties)
rm -f destroy-extras-*.jar
cp "build/libs/destroy-extras-$VERSION.jar" .
ICONS="membrane_electrolyzer etching_bath membrane"
(cd ../.. && node scripts/mcp/render.mjs tools/destroy-extras-mod $(printf 'destroy_extras:%s ' $ICONS) --out tools/destroy-extras-mod/icons --size 192)
shasum -a 256 "destroy-extras-$VERSION.jar"
echo "готово: destroy-extras-$VERSION.jar → tools/destroy-extras-mod; обновите addon.json и проверьте MCP addon_check"
