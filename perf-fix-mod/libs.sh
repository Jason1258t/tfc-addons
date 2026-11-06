#!/usr/bin/env bash
# Библиотеки для компиляции: jar модов, которые аддон правит (JourneyMap Shared Maps, TFC). В мод не попадают.
# Jar сборки — из кеша снимка (npm run pack:fetch, ~/.cache/tfc-plans/pack/mods); MODS=<папка> — другая папка модов.
set -euo pipefail
cd "$(dirname "$0")"
MODS="${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"
rm -rf libs && mkdir libs
cp "$MODS"/jmsharedmaps-*.jar "$MODS"/TerraFirmaCraft-NeoForge-1.21.1-*.jar libs/
ls libs
