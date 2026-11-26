#!/usr/bin/env bash
# Библиотеки для компиляции: TFC из jar сборки. В мод не попадает — на сервере он уже стоит.
# Jar сборки — из кеша снимка (npm run pack:fetch, ~/.cache/tfc-plans/pack/mods); MODS=<папка> — другая папка модов.
set -euo pipefail
cd "$(dirname "$0")"
MODS="${MODS:-${TFC_MODS_DIR:-${TFC_PACK_CACHE:-$HOME/.cache/tfc-plans/pack}/mods}}"
rm -rf libs && mkdir libs
cp "$MODS"/TerraFirmaCraft-NeoForge-1.21.1-*.jar libs/
# Registrate нужен только проверочному mixin, в основной аддон не попадает.
unzip -qq -o -j "$MODS"/create-1.21.1-*.jar 'META-INF/jarjar/Registrate*.jar' -d libs/
ls libs
