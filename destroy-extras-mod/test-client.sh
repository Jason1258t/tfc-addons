#!/usr/bin/env bash
# Клиент разработки: настоящие JEI, EMI/Jemi и Ponder, русские ресурсы и скриншоты экранов.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
./libs.sh
rm -rf run-client
mkdir -p run-client/mods
for jar in create-1.21.1-6.0.10.jar electroenergetics-1.21.1-1.1.2.jar TerraFirmaCraft-NeoForge-1.21.1-4.2.9.jar Patchouli-1.21.1-93-NEOFORGE.jar destroy-1.21.1-0.4.6-eligibbs.jar petrolpark-1.21.1-1.5.11.jar jei-1.21.1-neoforge-19.55.0.431.jar emi-1.1.24+1.21.1+neoforge.jar; do
  cp "libs/$jar" run-client/mods/
done
MODS="${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"
cp "$MODS/Firmalife-NeoForge-1.21.1-3.0.14.jar" run-client/mods/
EE_VERSION=$(sed -n 's/^mod_version=//p' ../ee-extras-mod/gradle.properties)
cp ../destroy-bridge-mod/build/libs/destroy-bridge-0.1.0.jar run-client/mods/
cp "../ee-extras-mod/build/libs/ee-extras-$EE_VERSION.jar" run-client/mods/
cp ../ee-extras-mod/libs/ImmersiveEngineering-1.21.1-12.4.2-194.jar run-client/mods/
printf 'lang:ru_ru\nonboardAccessibility:false\ntutorialStep:none\npauseOnLostFocus:false\nenableVsync:false\nmaxFps:60\n' > run-client/options.txt
set +e
./gradlew runClient --console=plain > run-client/check.log 2>&1
STATUS=$?
set -e
perl -ne 'print if /DESTROYEXTRACLIENT|ERROR|Exception|Caused by|Mod loading has failed/' run-client/check.log > run-client/results.txt
head -120 run-client/results.txt
if [ "$STATUS" -ne 0 ] || ! grep -q 'DESTROYEXTRACLIENT PASS' run-client/check.log || grep -q 'DESTROYEXTRACLIENT FAIL' run-client/check.log || grep -q 'Failed to read ponder schematic' run-client/check.log; then
  exit 1
fi
