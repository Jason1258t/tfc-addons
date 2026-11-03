#!/usr/bin/env bash
# Те же зафиксированные зависимости, что у моста, плюс его собранный jar для compileOnly.
set -euo pipefail
cd "$(dirname "$0")"
MODS="${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"
SUPER_ROOT="$(git rev-parse --show-superproject-working-tree)"
MAIN_ROOT="${SUPER_ROOT%%/.claude/worktrees/*}"
CANDIDATES="${CANDIDATES:-$MAIN_ROOT/.cache/mcp/candidates}"
DESTROY_JAR="${DESTROY_JAR:-$CANDIDATES/destroy-1.21.1-0.4.6-eligibbs.jar}"
PETROLPARK_JAR="${PETROLPARK_JAR:-$CANDIDATES/petrolpark-1.21.1-1.5.11.jar}"
# В снимке пака JEI старше минимально поддерживаемого Petrolpark 1.5.11; на клиентском стенде та же 19.55, что в обновлении сборки.
JEI_VERSION="19.55.0.431"
JEI_JAR="$PWD/client-libs/jei-1.21.1-neoforge-$JEI_VERSION.jar"
mkdir -p client-libs
[ -f "$JEI_JAR" ] || curl -fsSL "https://maven.blamejared.com/mezz/jei/jei-1.21.1-neoforge/$JEI_VERSION/jei-1.21.1-neoforge-$JEI_VERSION.jar" -o "$JEI_JAR"
FILES=(
  "$MODS/create-1.21.1-6.0.10.jar"
  "$MODS/electroenergetics-1.21.1-1.1.2.jar"
  "$MODS/TerraFirmaCraft-NeoForge-1.21.1-4.2.9.jar"
  "$MODS/Patchouli-1.21.1-93-NEOFORGE.jar"
  "$DESTROY_JAR"
  "$PETROLPARK_JAR"
  "$JEI_JAR"
  "$MODS/emi-1.1.24+1.21.1+neoforge.jar"
)
for jar in "${FILES[@]}"; do
  [ -f "$jar" ] || { echo "Нет точной зависимости: $jar" >&2; exit 1; }
done
echo '8fb94ff4fc124671b3408ca13b9fe471a8a2dacfc79218fddda3308c42a02862  '"$DESTROY_JAR" | shasum -a 256 -c -
echo 'f9870f0c9c654d962d89a485c60d971181556c48bea345964ee5a07bc54095f4  '"$PETROLPARK_JAR" | shasum -a 256 -c -
(cd ../destroy-bridge-mod && ./libs.sh && ./gradlew jar --console=plain)
(cd ../ee-extras-mod && ./libs.sh && ./gradlew jar --console=plain)
rm -rf libs && mkdir libs
: > libs/manifest.txt
EE_VERSION=$(sed -n 's/^mod_version=//p' ../ee-extras-mod/gradle.properties)
for jar in "${FILES[@]}" ../destroy-bridge-mod/build/libs/destroy-bridge-0.1.0.jar "../ee-extras-mod/build/libs/ee-extras-$EE_VERSION.jar"; do
  cp "$jar" libs/
  shasum -a 256 "$jar" >> libs/manifest.txt
  case "$jar" in *destroy-bridge-*) ;; *) unzip -qq -o -j "$jar" 'META-INF/jarjar/*.jar' -d libs/ 2>/dev/null || true ;; esac
done
find libs -maxdepth 1 -name '*.jar' -print0 | sort -z | xargs -0 shasum -a 256 >> libs/manifest.txt
cat libs/manifest.txt
