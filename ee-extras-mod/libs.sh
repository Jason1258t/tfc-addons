#!/usr/bin/env bash
# Библиотеки для компиляции: Create, Electro Energetics, TFC (термометр), Immersive Engineering и CC:Tweaked
# из jar сборки + вложенные в них (catnip/ponder, flywheel, Registrate). В мод не попадают — на сервере они уже стоят.
# Jar сборки — из кеша снимка (npm run pack:fetch, ~/.cache/tfc-plans/pack/mods); MODS=<папка> — другая папка модов.
set -euo pipefail
cd "$(dirname "$0")"
MODS="${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"
rm -rf libs && mkdir libs
for j in "$MODS"/create-1.21.1-*.jar "$MODS"/electroenergetics-*.jar "$MODS"/ImmersiveEngineering-*.jar "$MODS"/TerraFirmaCraft-NeoForge-*.jar "$MODS"/cc-tweaked-*.jar; do
  cp "$j" libs/
  unzip -qq -o -j "$j" 'META-INF/jarjar/*.jar' -d libs/ 2>/dev/null || true
done
# DESTROY=1 — кандидат Destroy и Petrolpark только для проверки химии PET в devtest.
if [ -n "${DESTROY:-}" ]; then
  SUPER_ROOT="$(git rev-parse --show-superproject-working-tree)"
  MAIN_ROOT="${SUPER_ROOT%%/.claude/worktrees/*}"
  CANDIDATES="${CANDIDATES:-$MAIN_ROOT/.cache/mcp/candidates}"
  cp "$CANDIDATES/destroy-1.21.1-0.4.6-eligibbs.jar" "$CANDIDATES/petrolpark-1.21.1-1.5.11.jar" libs/
fi
ls libs
