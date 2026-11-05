#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/local-tools/jdk21/Contents/Home}"
[ -d libs ] || ./libs.sh
python3 art/gen_assets.py
./gradlew build --console=plain
version=$(sed -n 's/^mod_version=//p' gradle.properties)
cp "build/libs/better-fluids-$version.jar" .
(cd ../.. && node scripts/mcp/render.mjs tools/better-fluids-mod better_fluids:iron_tank better_fluids:iron_tank_plate better_fluids:iron_tank_quarter better_fluids:iron_tank_bevel better_fluids:fluid_tap --out tools/better-fluids-mod/icons --size 192)
